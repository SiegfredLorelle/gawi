package com.gawi.core.domain.mascot

import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.testsupport.habitId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * docs/ux/today-view.md §6's `recentlyBrokenHabits`: which habits the
 * regenerating line may name, and in which order.
 *
 * The ordering cases are the point of this file. [Mascot.mood] already has
 * `MascotMoodTest` asserting *whether* anything broke; nothing there can see
 * which habit, because a mood is a bare label.
 */
class RecentlyBrokenHabitsTest {

    // A Monday, matching the other mascot tests so the dates read the same way.
    private val today = LocalDate.parse("2026-08-17")

    private fun habit(n: Int, brokenOn: LocalDate?, archived: Boolean = false, completedToday: Boolean = false) = HabitMoodState(
        id = habitId(n),
        schedule = Schedule.Daily,
        archived = archived,
        completedToday = completedToday,
        completionsThisWeek = 0,
        streak = if (brokenOn == null) StreakSnapshot.NONE else StreakSnapshot(0, previous = 4, brokenOn = brokenOn),
    )

    private fun inputsOf(vararg habits: HabitMoodState) = MoodInputs(
        habits = habits.toList(),
        today = today,
        now = LocalDateTime.of(today, LocalTime.of(9, 0)),
        reminderTime = LocalTime.of(21, 0),
        dayCutoff = LocalTime.MIDNIGHT,
        weekStart = DayOfWeek.MONDAY,
    )

    private fun brokenIn(vararg habits: HabitMoodState): List<HabitId> = Mascot.recentlyBrokenHabits(inputsOf(*habits))

    @Test
    fun `nothing broken is an empty list, not a habit with no break`() {
        assertEquals(emptyList<HabitId>(), brokenIn(habit(1, brokenOn = null), habit(2, brokenOn = null)))
    }

    @Test
    fun `the most recently broken habit comes first`() {
        // The line names one habit, so this order is what the user reads.
        val ids = brokenIn(
            habit(1, brokenOn = today.minusDays(2)),
            habit(2, brokenOn = today),
            habit(3, brokenOn = today.minusDays(1)),
        )
        assertEquals(listOf(habitId(2), habitId(3), habitId(1)), ids)
    }

    @Test
    fun `two breaks on the same day keep the caller's order`() {
        // Stability is the whole of the tie-break rule: without it the named
        // habit could swap between two equally recent breaks on a recomposition.
        assertEquals(listOf(habitId(2), habitId(1)), brokenIn(habit(2, brokenOn = today), habit(1, brokenOn = today)))
        assertEquals(listOf(habitId(1), habitId(2)), brokenIn(habit(1, brokenOn = today), habit(2, brokenOn = today)))
    }

    @Test
    fun `the window is three days counting today, at both ends`() {
        // REGENERATING_WINDOW_DAYS is 3, so today, yesterday and the day before
        // are in and the day before that is out.
        assertEquals(listOf(habitId(1)), brokenIn(habit(1, brokenOn = today)))
        assertEquals(listOf(habitId(1)), brokenIn(habit(1, brokenOn = today.minusDays(2))))
        assertEquals(emptyList<HabitId>(), brokenIn(habit(1, brokenOn = today.minusDays(3))))
    }

    @Test
    fun `a break dated after today is a snapshot paired with the wrong date, not a break`() {
        assertEquals(emptyList<HabitId>(), brokenIn(habit(1, brokenOn = today.plusDays(1))))
    }

    @Test
    fun `an archived habit is never named`() {
        // Rule 0's filter, held here too: an archived habit is not something the
        // user is being invited to pick back up.
        assertEquals(listOf(habitId(2)), brokenIn(habit(1, brokenOn = today, archived = true), habit(2, brokenOn = today)))
    }

    @Test
    fun `a finished day still lists its breaks, because the mood decides whether to say so`() {
        // Mood.THRIVING outranks Mood.REGENERATING, so this list is non-empty
        // with nothing to draw. Which habit is this function's question; whether
        // to name one at all is the panel's.
        val finished = habit(1, brokenOn = today, completedToday = true)
        assertEquals(Mood.THRIVING, Mascot.mood(inputsOf(finished)))
        assertEquals(listOf(habitId(1)), brokenIn(finished))
    }
}
