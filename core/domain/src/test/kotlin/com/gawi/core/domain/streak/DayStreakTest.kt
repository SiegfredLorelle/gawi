package com.gawi.core.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DayStreakTest {

    private val today = LocalDate.parse("2026-08-17")

    private fun dates(vararg days: String) = days.map(LocalDate::parse).toSet()

    @Test
    fun `no completions is zero`() {
        assertEquals(0, Streaks.dayStreak(emptySet(), today))
    }

    @Test
    fun `completed today only is one`() {
        assertEquals(1, Streaks.dayStreak(dates("2026-08-17"), today))
    }

    @Test
    fun `completed yesterday only stays alive at one`() {
        assertEquals(1, Streaks.dayStreak(dates("2026-08-16"), today))
    }

    @Test
    fun `a day missed before yesterday resets to zero`() {
        assertEquals(0, Streaks.dayStreak(dates("2026-08-15"), today))
    }

    @Test
    fun `three days ending yesterday then completing today makes four`() {
        val run = dates("2026-08-14", "2026-08-15", "2026-08-16")

        assertEquals(3, Streaks.dayStreak(run, today))
        assertEquals(4, Streaks.dayStreak(run + today, today))
    }

    @Test
    fun `only the run ending now counts past a gap`() {
        val withGap = dates("2026-08-12", "2026-08-13", "2026-08-15", "2026-08-16", "2026-08-17")

        assertEquals(3, Streaks.dayStreak(withGap, today))
    }

    @Test
    fun `retroactively filling the gap bridges the runs`() {
        val bridged = dates("2026-08-12", "2026-08-13", "2026-08-14", "2026-08-15", "2026-08-16", "2026-08-17")

        assertEquals(6, Streaks.dayStreak(bridged, today))
    }

    @Test
    fun `months-old completions alone are zero`() {
        assertEquals(0, Streaks.dayStreak(dates("2026-01-01", "2026-01-02"), today))
    }

    @Test
    fun `a streak spans month and year boundaries`() {
        val newYear = dates("2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02")

        assertEquals(4, Streaks.dayStreak(newYear, LocalDate.parse("2027-01-02")))
    }

    @Test
    fun `consecutive logical dates across a dst weekend are unbroken`() {
        val dstWeekend = dates("2026-03-27", "2026-03-28", "2026-03-29", "2026-03-30")

        assertEquals(4, Streaks.dayStreak(dstWeekend, LocalDate.parse("2026-03-30")))
    }
}
