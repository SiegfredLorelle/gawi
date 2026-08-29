package com.gawi.core.domain.streak

import com.gawi.core.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** The longest run inside a period, clipped to the period and to today. */
class BestRunTest {

    private val q2 = LocalDate.of(2026, 4, 1)..LocalDate.of(2026, 6, 30)
    private val today = LocalDate.of(2026, 8, 29)

    private fun days(from: LocalDate, count: Int): Set<LocalDate> = (0 until count).map { from.plusDays(it.toLong()) }.toSet()

    private fun best(
        dates: Set<LocalDate>,
        schedule: Schedule = Schedule.Daily,
        window: ClosedRange<LocalDate> = q2,
        on: LocalDate = today,
    ) = BestRun.within(dates, schedule, window, on, DayOfWeek.MONDAY)

    @Test
    fun `nothing completed is zero`() {
        assertEquals(0, best(emptySet()))
    }

    @Test
    fun `the longer of two runs wins`() {
        val dates = days(LocalDate.of(2026, 4, 3), 5) + days(LocalDate.of(2026, 5, 10), 12)
        assertEquals(12, best(dates))
    }

    @Test
    fun `a run that began before the period counts only its inside part`() {
        // 40 days ending 2026-04-05: 31 of them are in March.
        assertEquals(5, best(days(LocalDate.of(2026, 2, 25), 40)))
    }

    @Test
    fun `a run that outlives the period is clipped at its end`() {
        assertEquals(10, best(days(LocalDate.of(2026, 6, 21), 30)))
    }

    @Test
    fun `dates after today do not lengthen a run`() {
        val window = LocalDate.of(2026, 8, 1)..LocalDate.of(2026, 8, 31)
        assertEquals(4, best(days(LocalDate.of(2026, 8, 26), 10), window = window))
    }

    @Test
    fun `a run counts today when today is done, as the streak on Today does`() {
        val window = LocalDate.of(2026, 8, 1)..LocalDate.of(2026, 8, 31)
        assertEquals(1, best(setOf(today), window = window))
    }

    @Test
    fun `a weekly run is counted in hit weeks`() {
        val twice = Schedule.Weekly(2)
        // Mondays 2026-04-06, 13, 20 hit with two days each; 27 has one.
        val dates = setOf(
            LocalDate.of(2026, 4, 6), LocalDate.of(2026, 4, 7),
            LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 14),
            LocalDate.of(2026, 4, 20), LocalDate.of(2026, 4, 21),
            LocalDate.of(2026, 4, 27),
            LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5),
        )
        assertEquals(3, best(dates, twice))
    }

    @Test
    fun `a week the period only partly holds is not judged`() {
        // Q2 2026 opens on a Wednesday: the week of Monday 30 March straddles it.
        val once = Schedule.Weekly(1)
        val dates = setOf(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 8), LocalDate.of(2026, 4, 15))
        assertEquals(2, best(dates, once))
    }

    @Test
    fun `a weekly run crosses a year boundary inside a year-long window`() {
        val once = Schedule.Weekly(1)
        val window = LocalDate.of(2025, 12, 1)..LocalDate.of(2026, 1, 31)
        // Mondays 2025-12-15, 22, 29 and 2026-01-05 — the last spans New Year.
        val dates = setOf(LocalDate.of(2025, 12, 15), LocalDate.of(2025, 12, 22), LocalDate.of(2025, 12, 29), LocalDate.of(2026, 1, 5))
        assertEquals(4, best(dates, once, window))
    }

    @Test
    fun `an unfinished current week that already hit extends the run`() {
        val once = Schedule.Weekly(1)
        val window = LocalDate.of(2026, 8, 1)..LocalDate.of(2026, 8, 31)
        // today is Saturday 2026-08-29, in the week of Monday 24 August.
        val dates = setOf(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 25))
        assertEquals(3, best(dates, once, window))
    }
}
