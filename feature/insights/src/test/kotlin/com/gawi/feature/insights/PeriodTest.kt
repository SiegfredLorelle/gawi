package com.gawi.feature.insights

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The three windows, on the dates where an off-by-one would hide.
 *
 * Calendar periods, so every boundary here is a calendar boundary and none of
 * them depend on today's position inside the period — which is the property that
 * makes this shareable with Phase 1.5's quarterly and yearly retrospectives
 * (docs/ux/insights.md §7).
 */
class PeriodTest {

    private fun window(period: Period, on: String) = period.window(LocalDate.parse(on))

    @Test
    fun `a month runs the whole calendar month, wherever in it today falls`() {
        val expected = LocalDate.parse("2026-08-01")..LocalDate.parse("2026-08-31")

        assertEquals(expected, window(Period.MONTH, "2026-08-01"))
        assertEquals(expected, window(Period.MONTH, "2026-08-18"))
        assertEquals(expected, window(Period.MONTH, "2026-08-31"))
    }

    @Test
    fun `a short month and a leap February end where they end`() {
        assertEquals(LocalDate.parse("2026-02-28"), window(Period.MONTH, "2026-02-10").endInclusive)
        assertEquals(LocalDate.parse("2028-02-29"), window(Period.MONTH, "2028-02-10").endInclusive)
    }

    /**
     * All four, because a quarter is the one period whose bounds are arithmetic
     * rather than a calendar lookup — and the arithmetic is where an off-by-one
     * lands a whole month in the wrong quarter.
     */
    @Test
    fun `each quarter runs its own three months`() {
        assertEquals(LocalDate.parse("2026-01-01")..LocalDate.parse("2026-03-31"), window(Period.QUARTER, "2026-02-14"))
        assertEquals(LocalDate.parse("2026-04-01")..LocalDate.parse("2026-06-30"), window(Period.QUARTER, "2026-04-01"))
        assertEquals(LocalDate.parse("2026-07-01")..LocalDate.parse("2026-09-30"), window(Period.QUARTER, "2026-08-18"))
        assertEquals(LocalDate.parse("2026-10-01")..LocalDate.parse("2026-12-31"), window(Period.QUARTER, "2026-12-31"))
    }

    @Test
    fun `a year runs January to December`() {
        assertEquals(LocalDate.parse("2026-01-01")..LocalDate.parse("2026-12-31"), window(Period.YEAR, "2026-08-18"))
        // The two days either side of a year boundary land in their own years.
        assertEquals(2025, window(Period.YEAR, "2025-12-31").start.year)
        assertEquals(2027, window(Period.YEAR, "2027-01-01").endInclusive.year)
    }

    /**
     * A window can end after today, and that is deliberate rather than tolerated.
     * `Rates.completionRate` clips its own — its KDoc anticipates a picker
     * offering "this year" — and a *total* needs no clipping, since a completion
     * cannot be logged for a day that has not happened.
     */
    @Test
    fun `a window may reach past today`() {
        val today = LocalDate.parse("2026-01-02")

        Period.entries.forEach { period ->
            assertEquals("$period", true, period.window(today).endInclusive >= today)
        }
    }

    @Test
    fun `every period contains today`() {
        val today = LocalDate.parse("2026-08-18")

        Period.entries.forEach { period ->
            assertEquals("$period", true, today in period.window(today))
        }
    }
}
