package com.gawi.core.domain.mascot

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.testsupport.habitId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** docs/ux/today-view.md §4's precedence table, row by row. */
class MascotMoodTest {

    // A Monday, so the week arithmetic the weekly rules do reads plainly
    // against the default week start.
    private val today = LocalDate.parse("2026-08-17")
    private val reminder = LocalTime.of(21, 0)
    private val morning = LocalTime.of(9, 0)
    private val evening = LocalTime.of(21, 30)

    private fun daily(completedToday: Boolean, streak: StreakSnapshot = StreakSnapshot.NONE, archived: Boolean = false) =
        HabitMoodState(habitId(1), Schedule.Daily, archived, completedToday, completionsThisWeek = 0, streak)

    private fun weekly(timesPerWeek: Int, completionsThisWeek: Int = 0, streak: StreakSnapshot = StreakSnapshot.NONE) =
        HabitMoodState(habitId(2), Schedule.Weekly(timesPerWeek), archived = false, completedToday = false, completionsThisWeek, streak)

    private fun brokeOn(day: LocalDate) = StreakSnapshot(current = 0, previous = 4, brokenOn = day)

    private fun moodOf(vararg habits: HabitMoodState, at: LocalTime = morning, on: LocalDate = today) =
        Mascot.mood(MoodInputs(habits.toList(), on, LocalDateTime.of(on, at), reminder, LocalTime.MIDNIGHT, DayOfWeek.MONDAY))

    @Test
    fun `a first run with no habits is content rather than thriving`() {
        // Rule 0 is load-bearing, not a guard: an empty list satisfies rule 1
        // too, and Momo must not greet a brand-new user as thriving.
        assertEquals(Mood.CONTENT, moodOf())
    }

    @Test
    fun `nothing outstanding is thriving`() {
        assertEquals(Mood.THRIVING, moodOf(daily(completedToday = true)))
    }

    @Test
    fun `thriving outranks regenerating`() {
        // The deliberate call in §4: finishing the day is the way out of the
        // recovery state, so it can never sit there as a quiet scold. A weekly
        // habit is where the two rules genuinely meet — its streak can read
        // broken this week while now-or-never still leaves it un-outstanding.
        val brokenButNotYetDue = weekly(timesPerWeek = 3, streak = brokeOn(today))

        assertEquals(Mood.THRIVING, moodOf(brokenButNotYetDue))
        assertEquals(Mood.THRIVING, moodOf(brokenButNotYetDue, at = evening))
    }

    @Test
    fun `a break with something still outstanding regenerates`() {
        assertEquals(Mood.REGENERATING, moodOf(daily(completedToday = false, streak = brokeOn(today))))
    }

    @Test
    fun `regenerating outranks worried`() {
        // Past the reminder, so rule 3 would fire if rule 2 did not come first.
        assertEquals(Mood.REGENERATING, moodOf(daily(completedToday = false, streak = brokeOn(today)), at = evening))
    }

    @Test
    fun `the regenerating window lasts three logical days and then expires`() {
        val onTheDay = brokeOn(today)
        val twoDaysOn = brokeOn(today.minusDays(2))
        val threeDaysOn = brokeOn(today.minusDays(3))

        assertEquals(Mood.REGENERATING, moodOf(daily(completedToday = false, streak = onTheDay)))
        assertEquals(Mood.REGENERATING, moodOf(daily(completedToday = false, streak = twoDaysOn)))
        // Otherwise an abandoned habit would pin Momo to a permanent guilt
        // face, which is the failure mode this mood exists to avoid.
        assertEquals(Mood.CONTENT, moodOf(daily(completedToday = false, streak = threeDaysOn)))
    }

    @Test
    fun `a habit with no completions yet is not recovering from anything`() {
        // StreakSnapshot.NONE is "nothing yet", not a break.
        assertEquals(Mood.CONTENT, moodOf(daily(completedToday = false, streak = StreakSnapshot.NONE)))
    }

    @Test
    fun `a snapshot dated after today is not a break that has happened`() {
        assertEquals(Mood.CONTENT, moodOf(daily(completedToday = false, streak = brokeOn(today.plusDays(1)))))
    }

    @Test
    fun `outstanding past the reminder time is worried`() {
        assertEquals(Mood.WORRIED, moodOf(daily(completedToday = false), at = evening))
    }

    @Test
    fun `outstanding earlier in the day is only content`() {
        assertEquals(Mood.CONTENT, moodOf(daily(completedToday = false)))
    }

    @Test
    fun `an archived habit is invisible to every rule`() {
        val archived = daily(completedToday = false, streak = brokeOn(today), archived = true)

        // Including rule 0: a list of nothing but archived habits is no habits.
        assertEquals(Mood.CONTENT, moodOf(archived))
        assertEquals(Mood.CONTENT, moodOf(archived, at = evening))
        // And it cannot make a finished day read as unfinished.
        assertEquals(Mood.THRIVING, moodOf(archived, daily(completedToday = true)))
    }

    @Test
    fun `one unfinished habit among finished ones still counts`() {
        assertEquals(Mood.WORRIED, moodOf(daily(completedToday = true), daily(completedToday = false), at = evening))
    }
}
