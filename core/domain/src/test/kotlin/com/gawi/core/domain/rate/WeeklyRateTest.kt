package com.gawi.core.domain.rate

import com.gawi.core.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Weeks here start on Monday: 2026-08-10, -17 and -24 are Mondays, so the
 * weeks in play are Aug 10–16, Aug 17–23 and Aug 24–30. `today` is Monday
 * 2026-08-24, which makes Aug 24–30 the unfinished current week.
 */
class WeeklyRateTest {

    private val today = LocalDate.parse("2026-08-24")

    private fun dates(vararg days: String) = days.map(LocalDate::parse).toSet()

    private fun rate(completed: Set<LocalDate>, from: String, to: String, timesPerWeek: Int = 3) = Rates.completionRate(
        completedDates = completed,
        schedule = Schedule.Weekly(timesPerWeek),
        window = LocalDate.parse(from)..LocalDate.parse(to),
        today = today,
        weekStart = DayOfWeek.MONDAY,
    )

    @Test
    fun `the denominator is the target times the weeks, not the days`() {
        val result = rate(emptySet(), "2026-08-10", "2026-08-23")

        assertEquals(CompletionRate.Weekly(timesPerWeek = 3, completed = 0, opportunities = 6), result)
    }

    @Test
    fun `completions across two whole weeks add up`() {
        val completed = dates("2026-08-10", "2026-08-11", "2026-08-13", "2026-08-19")

        assertEquals(4, rate(completed, "2026-08-10", "2026-08-23").completed)
    }

    @Test
    fun `the current week is not an opportunity`() {
        val result = rate(emptySet(), "2026-08-10", "2026-08-30")

        assertEquals("Aug 24-30 has not finished", 6, result.opportunities)
    }

    @Test
    fun `a partial first week is dropped rather than charged in full`() {
        val result = rate(emptySet(), "2026-08-12", "2026-08-23")

        assertEquals("only Aug 17-23 is whole", 3, result.opportunities)
    }

    @Test
    fun `a partial last week is dropped too`() {
        val result = rate(emptySet(), "2026-08-10", "2026-08-20")

        assertEquals("only Aug 10-16 is whole", 3, result.opportunities)
    }

    @Test
    fun `overshooting the target in a week does not exceed one hundred percent`() {
        val fiveInOneWeek = dates("2026-08-17", "2026-08-18", "2026-08-19", "2026-08-20", "2026-08-21")
        val result = rate(fiveInOneWeek, "2026-08-17", "2026-08-23")

        assertEquals(CompletionRate.Weekly(timesPerWeek = 3, completed = 3, opportunities = 3), result)
        assertEquals(1.0, result.fraction!!, 1e-9)
    }

    @Test
    fun `a spare week does not subsidise a missed one`() {
        val lopsided = dates(
            "2026-08-10",
            "2026-08-11",
            "2026-08-12",
            "2026-08-13",
            "2026-08-14",
            "2026-08-19",
        )

        assertEquals("3 capped from 5, plus 1", 4, rate(lopsided, "2026-08-10", "2026-08-23").completed)
    }

    @Test
    fun `a window holding no finished whole week has no opportunities`() {
        val result = rate(dates("2026-08-24"), "2026-08-24", "2026-08-30")

        assertEquals(0, result.opportunities)
        assertNull("a habit inside its first week has not failed anything", result.fraction)
    }

    @Test
    fun `the target travels with the rate`() {
        assertEquals(5, (rate(emptySet(), "2026-08-10", "2026-08-23", timesPerWeek = 5) as CompletionRate.Weekly).timesPerWeek)
    }

    @Test
    fun `a daily schedule and a weekly one are not the same type`() {
        val weekly = rate(dates("2026-08-19"), "2026-08-10", "2026-08-23")
        val daily = Rates.completionRate(
            completedDates = dates("2026-08-19"),
            schedule = Schedule.Daily,
            window = LocalDate.parse("2026-08-10")..LocalDate.parse("2026-08-23"),
            today = today,
            weekStart = DayOfWeek.MONDAY,
        )

        assertEquals(CompletionRate.Weekly(timesPerWeek = 3, completed = 1, opportunities = 6), weekly)
        assertEquals(CompletionRate.Daily(completed = 1, opportunities = 14), daily)
    }
}
