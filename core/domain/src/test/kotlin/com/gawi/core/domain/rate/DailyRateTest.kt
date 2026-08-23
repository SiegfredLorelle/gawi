package com.gawi.core.domain.rate

import com.gawi.core.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DailyRateTest {

    private val today = LocalDate.parse("2026-08-17")

    private fun dates(vararg days: String) = days.map(LocalDate::parse).toSet()

    private fun rate(completed: Set<LocalDate>, from: String, to: String) = Rates.completionRate(
        completedDates = completed,
        schedule = Schedule.Daily,
        window = LocalDate.parse(from)..LocalDate.parse(to),
        today = today,
        weekStart = DayOfWeek.MONDAY,
    )

    @Test
    fun `a window wholly in the past counts every day in it`() {
        val result = rate(dates("2026-08-10", "2026-08-12", "2026-08-16"), "2026-08-10", "2026-08-16")

        assertEquals(CompletionRate.Daily(completed = 3, opportunities = 7), result)
        assertEquals(3.0 / 7, result.fraction!!, 1e-9)
    }

    @Test
    fun `no completions is zero out of the window, not an absent rate`() {
        val result = rate(emptySet(), "2026-08-10", "2026-08-16")

        assertEquals(CompletionRate.Daily(completed = 0, opportunities = 7), result)
        assertEquals(0.0, result.fraction!!, 1e-9)
    }

    @Test
    fun `today is not an opportunity, because it has not finished`() {
        val result = rate(dates("2026-08-10"), "2026-08-10", "2026-08-17")

        assertEquals(7, result.opportunities)
    }

    @Test
    fun `a completion logged today does not move the rate until tomorrow`() {
        val withoutToday = rate(dates("2026-08-10"), "2026-08-10", "2026-08-17")
        val withToday = rate(dates("2026-08-10", "2026-08-17"), "2026-08-10", "2026-08-17")

        assertEquals(withoutToday, withToday)
    }

    @Test
    fun `a window covering only today has no finished day`() {
        val result = rate(dates("2026-08-17"), "2026-08-17", "2026-08-17")

        assertEquals(CompletionRate.Daily(completed = 0, opportunities = 0), result)
        assertNull("a window with nothing finished in it must not read as 0%", result.fraction)
    }

    @Test
    fun `a window that has not started yet has no opportunities`() {
        val result = rate(emptySet(), "2026-08-18", "2026-08-31")

        assertEquals(0, result.opportunities)
        assertNull(result.fraction)
    }

    @Test
    fun `a window running past today is clipped rather than counted`() {
        val result = rate(dates("2026-08-10"), "2026-08-10", "2026-12-31")

        assertEquals(7, result.opportunities)
    }

    @Test
    fun `completions outside the window are ignored`() {
        val result = rate(dates("2026-08-01", "2026-08-12", "2026-08-30"), "2026-08-10", "2026-08-16")

        assertEquals(1, result.completed)
    }
}
