package com.gawi.core.data.repository

import app.cash.turbine.test
import com.gawi.core.data.model.toMoodState
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.data.testsupport.FakeSettingsSource
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.data.testsupport.metadata
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.mascot.HabitMoodState
import com.gawi.core.domain.mascot.Mascot
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * The mascot's half of a Today reading — the mapping onto the mood's inputs,
 * and the two things that must re-emit it without a data change behind them.
 *
 * The mood rules themselves are `:core:domain`'s tests. What is checkable only
 * here is that the rows and the mood are one observation: same logical date,
 * same settings, and no way to hold one that disagrees with the other.
 */
@RunWith(RobolectricTestRunner::class)
class TodayMoodTest {

    private lateinit var store: TestStore

    private fun start(settings: FakeSettingsSource = FakeSettingsSource()) {
        store = TestStore.create(settings = settings)
    }

    @After
    fun tearDown() = store.close()

    private suspend fun createHabit(name: String = "read", schedule: Schedule = Schedule.Daily): HabitId =
        (store.repository.createHabit(metadata(name, schedule)) as CommandResult.Accepted).payload

    @Test
    fun `a row maps onto the mood's row field for field`() = runTest {
        start()
        val habit = createHabit("read", Schedule.Weekly(3))
        store.repository.addCompletion(habit, store.today())

        store.repository.observeToday().test {
            val snapshot = awaitItem()
            val expected = HabitMoodState(
                schedule = Schedule.Weekly(3),
                archived = false,
                completedToday = true,
                // The week count includes today's completion, which is what the
                // weekly now-or-never rule subtracts. A producer that excluded
                // it would shift every weekly threshold by a day and no domain
                // test would notice.
                completionsThisWeek = 1,
                streak = snapshot.habits.single().streak,
            )
            assertEquals(expected, snapshot.habits.single().toMoodState())
            assertEquals(listOf(expected), snapshot.moodInputs().habits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the snapshot carries the stored settings, not the defaults`() = runTest {
        val stored = UserSettings(
            dayCutoff = LocalTime.of(3, 0),
            weekStart = DayOfWeek.THURSDAY,
            reminderTime = LocalTime.of(19, 45),
        )
        start(FakeSettingsSource(stored))
        createHabit()

        store.repository.observeToday().test {
            val snapshot = awaitItem()
            // Three fields of the same type, so a transposed mapping compiles.
            // This is the assertion that catches it.
            assertEquals(stored.dayCutoff, snapshot.dayCutoff)
            assertEquals(stored.reminderTime, snapshot.reminderTime)
            assertEquals(stored.weekStart, snapshot.weekStart)
            assertEquals(store.today(), snapshot.today)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving the reminder behind us worries the mascot with no data change`() = runTest {
        start(FakeSettingsSource(UserSettings(reminderTime = LocalTime.of(21, 0))))
        store.clock.instant = Instant.parse("2026-08-17T20:00:00Z")
        createHabit()

        store.repository.observeToday().test {
            assertEquals(Mood.CONTENT, Mascot.mood(awaitItem().moodInputs()))

            store.settings.settings = store.settings.settings.copy(reminderTime = LocalTime.of(19, 0))

            assertEquals(Mood.WORRIED, Mascot.mood(awaitItem().moodInputs()))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reminder edit does not re-run the streak sweep`() = runTest {
        start(FakeSettingsSource(UserSettings(reminderTime = LocalTime.of(21, 0))))
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today())

        store.repository.observeToday().test {
            awaitItem()

            // Corrupt the cached streak by hand. The sweep rewrites every
            // habit's row, so its survival is the only observable proof that
            // the sweep did not run — an identical re-query would be hidden by
            // distinctUntilChanged, and ProjectionWriter cannot be spied on.
            val streaks = store.database.habitStreakDao()
            val corrupted = streaks.find(habit.value)!!.copy(currentStreak = 99)
            streaks.upsert(corrupted)

            val edited = LocalTime.of(19, 0)
            store.settings.settings = store.settings.settings.copy(reminderTime = edited)

            // The hand-written streak row invalidates its table as well, so an
            // emission carrying the old threshold can arrive first. Wait for the
            // one the edit produced rather than assuming it is next.
            var after = awaitItem()
            while (after.reminderTime != edited) after = awaitItem()

            assertEquals(99, streaks.find(habit.value)!!.currentStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `crossing the reminder threshold re-emits with the rows unchanged`() = runTest {
        start(FakeSettingsSource(UserSettings(reminderTime = LocalTime.of(21, 0))))
        store.clock.instant = Instant.parse("2026-08-17T20:00:00Z")
        createHabit()

        store.repository.observeToday().test {
            val before = awaitItem()
            assertEquals(Mood.CONTENT, Mascot.mood(before.moodInputs()))

            // No edit and no write — only the clock passing the threshold the
            // ticker is waiting on.
            store.clock.instant = Instant.parse("2026-08-17T21:30:00Z")

            val after = awaitItem()
            assertEquals(before.habits, after.habits)
            assertNotEquals(before.now, after.now)
            assertEquals(Mood.WORRIED, Mascot.mood(after.moodInputs()))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the mood is decided for the same logical date as the rows`() = runTest {
        start()
        store.clock.instant = Instant.parse("2026-08-17T23:00:00Z")
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today())

        store.repository.observeToday().test {
            val before = awaitItem()
            assertEquals(Mood.THRIVING, Mascot.mood(before.moodInputs()))

            store.clock.instant = Instant.parse("2026-08-18T00:30:00Z")

            // Every emission has to agree with itself. There is no window in
            // which the rows are the new day's and the mood is the old day's,
            // which is what one flow buys and two flows cannot.
            var rolled = false
            while (!rolled) {
                val snapshot = awaitItem()
                if (snapshot.today == before.today.plusDays(1)) {
                    rolled = true
                    assertEquals(false, snapshot.habits.single().completedToday)
                    assertNotEquals(Mood.THRIVING, Mascot.mood(snapshot.moodInputs()))
                }
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unreadable settings store still yields a snapshot`() = runTest {
        start()
        createHabit()
        // The mood path reads observe(), never current(), so the same refusal
        // that must stop a write must not stop the screen.
        store.settings.currentFails = true

        store.repository.observeToday().test {
            val snapshot = awaitItem()
            assertEquals(1, snapshot.habits.size)
            assertEquals(StreakSnapshot.NONE, snapshot.habits.single().streak)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
