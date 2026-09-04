package com.gawi.core.data.repository

import app.cash.turbine.test
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.testing.habitId
import com.gawi.core.domain.testing.metadata
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate

/** What the Today view actually receives. */
@RunWith(RobolectricTestRunner::class)
class TodayQueryTest {

    private lateinit var store: TestStore

    @Before
    fun setUp() {
        store = TestStore.create()
    }

    @After
    fun tearDown() = store.close()

    private suspend fun createHabit(name: String = "read", schedule: Schedule = Schedule.Daily): HabitId =
        (store.repository.createHabit(metadata(name, schedule)) as CommandResult.Accepted).payload

    @Test
    fun `a row carries the habit, its completion, its week count and its streak`() = runTest {
        val habit = createHabit("read", Schedule.Weekly(3))
        store.repository.addCompletion(habit, store.today(), note = "chapter one")

        store.repository.observeToday().test {
            val row = awaitItem().habits.single()
            assertEquals(habit, row.habit.id)
            assertEquals("read", row.habit.name)
            assertEquals(Schedule.Weekly(3), row.habit.schedule)
            assertTrue(row.completedToday)
            assertEquals("chapter one", row.note)
            assertEquals(1, row.weekCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an untouched habit reads as not completed with no note`() = runTest {
        createHabit()

        store.repository.observeToday().test {
            val row = awaitItem().habits.single()
            assertFalse(row.completedToday)
            assertNull(row.note)
            assertEquals(0, row.weekCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archived habits leave the list`() = runTest {
        val habit = createHabit()

        store.repository.observeToday().test {
            assertEquals(1, awaitItem().habits.size)

            store.repository.archiveHabit(habit)

            assertEquals(emptyList<Any>(), awaitItem().habits)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing the week start re-buckets a screen that is already open`() = runTest {
        // Today is Wednesday 19 August, and the completion is the Sunday
        // before it. With a Monday week start this week runs Mon 17 - Sun 23,
        // so the Sunday is last week's. With a Thursday week start it runs
        // Thu 13 - Wed 19, and the same completion is now inside it.
        //
        // The edit lands mid-collection on purpose. Re-collecting afterwards
        // would pass even against a reader that captured the settings once and
        // then ignored them, which is exactly the bug this pins: the week
        // bucket would freeze while the streak rows joined into the same query
        // were recomputed under the new setting.
        store.clock.moveTo(LocalDate.parse("2026-08-19"))
        val habit = createHabit("read", Schedule.Weekly(3))
        store.repository.addCompletion(habit, LocalDate.parse("2026-08-16"))

        store.repository.observeToday().test {
            assertEquals(0, awaitItem().habits.single().weekCount)

            store.settings.settings = store.settings.settings.copy(weekStart = DayOfWeek.THURSDAY)

            assertEquals(1, awaitItem().habits.single().weekCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unreadable settings store does not take the screen down`() = runTest {
        // The read path degrades on a settings read failure rather than dying,
        // and it can only do that if it never asks for the command path's read:
        // SettingsSource.current refuses when the store is unreadable, because a
        // command validates the retro window against its answer. The streak
        // sweep is the one that used to reach for it, so this pins the structure
        // rather than a behaviour — nothing here would fail if the sweep started
        // calling current() again except this assertion.
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today())

        store.settings.currentFails = true

        store.repository.observeToday().test {
            assertEquals(habit, awaitItem().habits.single().habit.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a future-dated completion does not inflate this week's count`() = runTest {
        // Reachable without sync: the device clock runs a day fast, a habit is
        // completed, and the clock is then corrected — leaving a completion
        // dated after today. Streaks already refuses to count those, so the
        // week count must too, or a row reads 1/3 in its subtitle while the
        // streak computed from the same cells says the week was not touched.
        store.clock.moveTo(LocalDate.parse("2026-08-19"))
        val habit = createHabit("read", Schedule.Weekly(3))
        store.repository.addCompletion(habit, LocalDate.parse("2026-08-19"))

        store.clock.moveTo(LocalDate.parse("2026-08-18"))

        store.repository.observeToday().test {
            val row = awaitItem().habits.single()
            assertEquals(0, row.weekCount)
            assertEquals(0, row.streak.current)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observing one habit sweeps its stale streak too`() = runTest {
        // The detail screen has the same rollover problem as the list: the
        // streak join carries no staleness guard, so opening it against rows
        // computed for an older day would pair today's completion state with
        // yesterday's streak.
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today())
        store.clock.advanceDays(2)

        store.repository.observeHabitDetail(habit).test {
            assertEquals(0, awaitItem()?.habit?.streak?.current)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(store.today().toString(), store.snapshot().streaks.single().computedForDate)
    }

    @Test
    fun `a repeated completion produces no further emission`() = runTest {
        val habit = createHabit()

        store.repository.observeToday().test {
            assertFalse(awaitItem().habits.single().completedToday)

            store.repository.addCompletion(habit, store.today())
            assertTrue(awaitItem().habits.single().completedToday)

            // Nothing changed, so nothing should reach the UI — this is the
            // pairing of skip-if-equal in the writer with distinctUntilChanged
            // on the way out.
            store.repository.addCompletion(habit, store.today())
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `touching one habit does not re-emit another habit's own flow`() = runTest {
        val a = createHabit("a")
        val b = createHabit("b")

        store.repository.observeHabitDetail(b).test {
            assertEquals(b, awaitItem()?.habit?.habit?.id)

            // Room invalidates per table, so habit b's query does wake up here.
            // distinctUntilChanged is what keeps that off the UI.
            store.repository.addCompletion(a, store.today())
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observing an unknown habit emits null rather than failing`() = runTest {
        store.repository.observeHabitDetail(habitId(404)).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Detail carries the date it was read for.
     *
     * The retro strip sizes its window against this and writes completions to
     * it. Nothing above :core:data may resolve a logical date of its own — that
     * needs a clock, a zone and the day cutoff — and a date one day stale falls
     * *inside* the 3-day window, which accepts it rather than refusing, so a
     * derived date would be a silent wrong answer.
     */
    @Test
    fun `detail carries the logical date it was read for`() = runTest {
        val habit = createHabit()

        store.repository.observeHabitDetail(habit).test {
            assertEquals(store.today(), awaitItem()?.today)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The strip window is the retro window plus one day that is already shut.
     *
     * docs/ux/today-view.md §5 wants the refused day drawn rather than tapped
     * and refused, so the read has to reach one day further back than the
     * command will accept. A window of only the writable days would leave the
     * screen nothing to draw shut; a wider one would offer history the MVP
     * detail screen does not show (that is PRD Phase 1's heatmap).
     */
    @Test
    fun `detail reads the retro window plus the day drawn shut`() = runTest {
        val habit = createHabit()

        // Written on the day each belongs to and the clock advanced between, so
        // none of these needs the retro window to be accepted — which is the
        // point: the window governs writing, and this is about reading back.
        store.repository.addCompletion(habit, store.today()) // becomes today-5
        store.clock.advanceDays(1)
        store.repository.addCompletion(habit, store.today()) // becomes today-4, shut
        store.clock.advanceDays(1)
        store.repository.addCompletion(habit, store.today()) // becomes today-3, oldest open
        store.clock.advanceDays(3)
        store.repository.addCompletion(habit, store.today())

        val today = store.today()
        store.repository.observeHabitDetail(habit).test {
            val recent = awaitItem()?.recent.orEmpty()
            // today-4 is present though nothing may write to it: a day drawn
            // shut still shows whether it was done. today-5 is off the strip.
            assertEquals(
                listOf(today.minusDays(4), today.minusDays(3), today),
                recent.keys.sorted(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completed dates come back with the notes showing on them`() = runTest {
        val habit = createHabit()
        val today = store.today()
        store.repository.addCompletion(habit, today, note = "a")
        store.repository.addCompletion(habit, today.minusDays(1))

        store.repository.observeCompletedDates(habit, today.minusDays(3), today).test {
            assertEquals(mapOf(today to "a", today.minusDays(1) to null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
