package com.gawi.core.domain.mascot

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * docs/ux/today-view.md §4's `nearBoundary` input, observed through the one
 * mood that depends on it: a single outstanding habit reads worried when the
 * reminder threshold has passed and content when it has not.
 */
class NearBoundaryTest {

    private val today = LocalDate.parse("2026-08-17")
    private val outstanding = HabitMoodState(
        Schedule.Daily,
        archived = false,
        completedToday = false,
        completionsThisWeek = 0,
        streak = StreakSnapshot.NONE,
    )

    private fun moodAt(
        now: LocalDateTime,
        reminder: LocalTime = LocalTime.of(21, 0),
        cutoff: LocalTime = LocalTime.MIDNIGHT,
        on: LocalDate = today,
    ) = Mascot.mood(MoodInputs(listOf(outstanding), on, now, reminder, cutoff, DayOfWeek.MONDAY))

    private fun at(day: String, time: String) = LocalDateTime.parse("${day}T$time")

    @Test
    fun `the threshold is at or past the reminder time, not strictly past`() {
        assertEquals(Mood.CONTENT, moodAt(at("2026-08-17", "20:59")))
        assertEquals(Mood.WORRIED, moodAt(at("2026-08-17", "21:00")))
        assertEquals(Mood.WORRIED, moodAt(at("2026-08-17", "23:59")))
    }

    @Test
    fun `a non-midnight cutoff keeps the late evening inside the same logical day`() {
        // With a 03:00 cutoff the logical 17th runs to 03:00 on the 18th, so
        // 01:30 on the 18th is 22:30 into it and past a 21:00 reminder. This is
        // the case a same-calendar-date comparison gets backwards.
        val cutoff = LocalTime.of(3, 0)

        assertEquals(Mood.CONTENT, moodAt(at("2026-08-17", "20:00"), cutoff = cutoff))
        assertEquals(Mood.WORRIED, moodAt(at("2026-08-17", "21:00"), cutoff = cutoff))
        assertEquals(Mood.WORRIED, moodAt(at("2026-08-18", "01:30"), cutoff = cutoff))
        assertEquals(Mood.WORRIED, moodAt(at("2026-08-18", "02:59"), cutoff = cutoff))
    }

    @Test
    fun `a stale logical date reads as not near the boundary rather than worried forever`() {
        // 03:00 on the 18th belongs to the logical 18th, so a caller still
        // holding the 17th has rows from a day that has ended.
        assertEquals(Mood.CONTENT, moodAt(at("2026-08-18", "03:00"), cutoff = LocalTime.of(3, 0)))
    }

    @Test
    fun `a reminder equal to the cutoff marks the whole logical day`() {
        // Pinned rather than special-cased: logicalDate's own rule is that a
        // wall time exactly at the cutoff begins the new day, so a reminder
        // there marks the day's start. Worth a test because the consequence —
        // worried from the first minute — is one a reader would call a bug.
        assertEquals(Mood.WORRIED, moodAt(at("2026-08-17", "00:00"), reminder = LocalTime.MIDNIGHT))
        assertEquals(Mood.WORRIED, moodAt(at("2026-08-17", "09:00"), reminder = LocalTime.MIDNIGHT))
    }

    @Test
    fun `a reminder set earlier than the cutoff falls after midnight`() {
        // Cutoff 03:00, reminder 01:00: the reminder for the logical 17th lands
        // at 01:00 on the 18th, in the last two hours of that logical day.
        val cutoff = LocalTime.of(3, 0)
        val reminder = LocalTime.of(1, 0)

        assertEquals(Mood.CONTENT, moodAt(at("2026-08-17", "23:00"), reminder = reminder, cutoff = cutoff))
        assertEquals(Mood.WORRIED, moodAt(at("2026-08-18", "01:30"), reminder = reminder, cutoff = cutoff))
    }
}
