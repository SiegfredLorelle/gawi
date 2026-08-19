package com.gawi.core.data.repository

import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.data.testsupport.metadata
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Day rollover — the one transition no event can express.
 *
 * A streak reaching zero because nothing happened is invisible to the log, so
 * it can only come from recomputing against a new "today". That is why the
 * streak rows carry the date they were computed for, and why the sweep has to
 * cover every habit rather than the ones some append happened to touch.
 */
@RunWith(RobolectricTestRunner::class)
class StreakRefreshTest {

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
    fun `a streak survives an unfinished day and dies on the day after`() = runTest {
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today())
        assertEquals(1, store.snapshot().streaks.single().currentStreak)

        // Next day, nothing logged: yesterday still holds the run up.
        store.clock.advanceDays(1)
        store.repository.refreshStreaks()
        assertEquals(1, store.snapshot().streaks.single().currentStreak)

        // The day after, the gap is real.
        store.clock.advanceDays(1)
        store.repository.refreshStreaks()

        val broken = store.snapshot().streaks.single()
        assertEquals(0, broken.currentStreak)
        assertEquals(1, broken.previousStreak)
    }

    @Test
    fun `a break records the day it happened, which is what recentlyBroken reads`() = runTest {
        val habit = createHabit()
        val start = store.today()
        store.repository.addCompletion(habit, start)

        store.clock.advanceDays(2)
        store.repository.refreshStreaks()

        assertEquals(start.plusDays(1).toString(), store.snapshot().streaks.single().brokenOn)
    }

    @Test
    fun `a live streak records no break`() = runTest {
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today())

        val row = store.snapshot().streaks.single()
        assertEquals(1, row.currentStreak)
        assertEquals(0, row.previousStreak)
        assertNull(row.brokenOn)
    }

    @Test
    fun `refreshing stamps every habit with the current date, not just touched ones`() = runTest {
        val touched = createHabit("touched")
        val untouched = createHabit("untouched")
        store.repository.addCompletion(touched, store.today())

        store.clock.advanceDays(1)
        store.repository.refreshStreaks()

        val today = store.today().toString()
        val rows = store.snapshot().streaks.associateBy { it.habitId }
        assertEquals(today, rows.getValue(touched.value).computedForDate)
        assertEquals(today, rows.getValue(untouched.value).computedForDate)
    }

    @Test
    fun `refreshing twice on the same day changes nothing`() = runTest {
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today())
        store.repository.refreshStreaks()
        val before = store.snapshot()

        store.repository.refreshStreaks()

        assertEquals(before, store.snapshot())
    }

    @Test
    fun `a weekly streak is not broken by a missed day inside its week`() = runTest {
        // Wednesday, so Monday and Tuesday are both inside the retro window and
        // inside the same week as today.
        store.clock.moveTo(java.time.LocalDate.parse("2026-08-19"))
        val habit = createHabit("weekly", Schedule.Weekly(2))
        store.repository.addCompletion(habit, java.time.LocalDate.parse("2026-08-17"))
        store.repository.addCompletion(habit, java.time.LocalDate.parse("2026-08-18"))
        store.repository.refreshStreaks()

        val hit = store.snapshot().streaks.single()
        assertEquals(1, hit.currentStreak)
        assertNull(hit.brokenOn)

        // Thursday and Friday logged nothing. A daily habit would have broken;
        // a weekly one that already met its target has not.
        store.clock.advanceDays(2)
        store.repository.refreshStreaks()

        val still = store.snapshot().streaks.single()
        assertEquals(1, still.currentStreak)
        assertNull(still.brokenOn)
    }

    @Test
    fun `a weekly streak breaks only once a whole week has been missed`() = runTest {
        store.clock.moveTo(java.time.LocalDate.parse("2026-08-19"))
        val habit = createHabit("weekly", Schedule.Weekly(2))
        store.repository.addCompletion(habit, java.time.LocalDate.parse("2026-08-17"))
        store.repository.addCompletion(habit, java.time.LocalDate.parse("2026-08-18"))

        // Skip the whole of the following week and land in the one after it.
        store.clock.moveTo(java.time.LocalDate.parse("2026-09-01"))
        store.repository.refreshStreaks()

        val broken = store.snapshot().streaks.single()
        assertEquals(0, broken.currentStreak)
        assertEquals(1, broken.previousStreak)
        assertEquals("2026-08-24", broken.brokenOn)
    }
}
