package com.gawi.core.domain.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The reminder threshold's own tests. The mascot's `nearBoundary` already
 * observes this shift through a mood, but the data layer wakes a timer on the
 * same answer and the notification will fire on it, so it is pinned here
 * directly rather than only through one of its three callers.
 */
class ReminderOnTest {

    private val today = LocalDate.parse("2026-08-17")
    private val midnight = LocalTime.MIDNIGHT
    private val cutoff3 = LocalTime.of(3, 0)
    private val reminder21 = LocalTime.of(21, 0)

    private fun at(dateTime: String): LocalDateTime = LocalDateTime.parse(dateTime)

    @Test
    fun `under a midnight cutoff the reminder falls on the logical date itself`() {
        assertEquals(at("2026-08-17T21:00"), reminderOn(today, reminder21, midnight))
    }

    @Test
    fun `a reminder later in the day than the cutoff stays on the same date`() {
        assertEquals(at("2026-08-17T21:00"), reminderOn(today, reminder21, cutoff3))
    }

    @Test
    fun `a reminder earlier in the day than the cutoff lands on the next date`() {
        // 01:30 is 22:30 into the logical 17th, which runs 03:00 to 03:00.
        assertEquals(at("2026-08-18T01:30"), reminderOn(today, LocalTime.of(1, 30), cutoff3))
    }

    @Test
    fun `a reminder equal to the cutoff marks the day's start, not its end`() {
        // Consistent with logicalDate, where a wall time exactly at the cutoff
        // begins the new day. The whole logical day then reads near the
        // boundary; a settings screen is where the combination is prevented.
        assertEquals(at("2026-08-17T03:00"), reminderOn(today, cutoff3, cutoff3))
    }

    @Test
    fun `the shift is by calendar date, so it crosses a month end`() {
        val monthEnd = LocalDate.parse("2026-08-31")
        assertEquals(at("2026-09-01T01:30"), reminderOn(monthEnd, LocalTime.of(1, 30), cutoff3))
    }
}
