package com.gawi.core.domain.mascot

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.testing.habitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * docs/ux/today-view.md §4's `outstanding` input, and in particular the
 * now-or-never rule for weekly habits.
 */
class OutstandingTest {

    // The week Mon 17 Aug 2026 through Sun 23 Aug 2026.
    private val monday = LocalDate.parse("2026-08-17")

    private fun day(offset: Long) = monday.plusDays(offset)

    private fun daily(completedToday: Boolean) =
        HabitMoodState(habitId(1), Schedule.Daily, archived = false, completedToday, completionsThisWeek = 0, StreakSnapshot.NONE)

    private fun weekly(timesPerWeek: Int, done: Int = 0, completedToday: Boolean = false) = HabitMoodState(
        habitId(2),
        Schedule.Weekly(timesPerWeek),
        archived = false,
        completedToday = completedToday,
        completionsThisWeek = done,
        streak = StreakSnapshot.NONE,
    )

    private fun outstanding(habit: HabitMoodState, on: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY) =
        Mascot.isOutstanding(habit, on, weekStart)

    @Test
    fun `a daily habit is outstanding until it is completed`() {
        assertTrue(outstanding(daily(completedToday = false), on = monday))
        assertFalse(outstanding(daily(completedToday = true), on = monday))
    }

    @Test
    fun `a once-weekly habit stays quiet until its last possible day`() {
        // Deliberately now-or-never: weekly targets are not tied to specific
        // days (PRD §4), so nagging on Monday about a Sunday-able habit would
        // contradict the schedule type.
        val quiet = (0L..5L).map { day(it) }.filterNot { outstanding(weekly(timesPerWeek = 1), on = it) }

        assertEquals((0L..5L).map { day(it) }, quiet)
        assertTrue(outstanding(weekly(timesPerWeek = 1), on = day(6)))
    }

    @Test
    fun `a three-times-weekly habit with one done waits for Saturday`() {
        // remaining 2, daysLeft 3 on Friday — still recoverable, so quiet.
        assertFalse(outstanding(weekly(timesPerWeek = 3, done = 1), on = day(4)))
        // remaining 2, daysLeft 2 on Saturday — now or never.
        assertTrue(outstanding(weekly(timesPerWeek = 3, done = 1), on = day(5)))
    }

    @Test
    fun `a weekly habit completed today is not outstanding today`() {
        // Saturday, 3x/week, none done before today: the target is already out
        // of reach, and the bare now-or-never arithmetic would keep nagging on a
        // day the user did turn up.
        assertTrue(outstanding(weekly(timesPerWeek = 3), on = day(5)))
        assertFalse(outstanding(weekly(timesPerWeek = 3, done = 1, completedToday = true), on = day(5)))
    }

    @Test
    fun `a met weekly target is never outstanding`() {
        assertFalse(outstanding(weekly(timesPerWeek = 3, done = 3), on = day(6)))
        // Over target too: idempotence per logical date caps a day, not a week.
        assertFalse(outstanding(weekly(timesPerWeek = 3, done = 4), on = day(6)))
    }

    @Test
    fun `a seven-times-weekly habit is outstanding every day it is behind`() {
        assertTrue(outstanding(weekly(timesPerWeek = 7), on = monday))
        assertTrue(outstanding(weekly(timesPerWeek = 7, done = 1), on = day(1)))
        assertFalse(outstanding(weekly(timesPerWeek = 7, done = 2), on = day(1)))
    }

    @Test
    fun `the configured week start decides which day is the last one`() {
        // With a Thursday week start, Wednesday is the week's last day and
        // Thursday its first.
        val wednesday = day(2)
        val thursday = day(3)

        assertTrue(outstanding(weekly(timesPerWeek = 1), on = wednesday, weekStart = DayOfWeek.THURSDAY))
        assertFalse(outstanding(weekly(timesPerWeek = 1), on = thursday, weekStart = DayOfWeek.THURSDAY))
    }
}
