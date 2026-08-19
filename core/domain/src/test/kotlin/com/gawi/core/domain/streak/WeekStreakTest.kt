package com.gawi.core.domain.streak

import com.gawi.core.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekStreakTest {

    /** A Monday. */
    private val today = LocalDate.parse("2026-08-17")

    private fun dates(vararg days: String) = days.map(LocalDate::parse).toSet()

    @Test
    fun `two full weeks of three anywhere make a streak of two`() {
        val completions = dates(
            "2026-08-03",
            "2026-08-05",
            "2026-08-09",
            "2026-08-10",
            "2026-08-14",
            "2026-08-15",
        )

        assertEquals(2, Streaks.weekStreak(completions, Schedule.Weekly(3), today = today))
    }

    @Test
    fun `day placement within the week is irrelevant`() {
        val frontLoaded = dates("2026-08-10", "2026-08-11", "2026-08-12")
        val backLoaded = dates("2026-08-14", "2026-08-15", "2026-08-16")

        assertEquals(1, Streaks.weekStreak(frontLoaded, Schedule.Weekly(3), today = today))
        assertEquals(1, Streaks.weekStreak(backLoaded, Schedule.Weekly(3), today = today))
    }

    @Test
    fun `idempotent completions mean one distinct date cannot satisfy three`() {
        val singleDay = dates("2026-08-12")

        assertEquals(0, Streaks.weekStreak(singleDay, Schedule.Weekly(3), today = today))
    }

    @Test
    fun `a finished week below target resets the streak`() {
        val twoOfThreeLastWeek = dates("2026-08-10", "2026-08-12")

        assertEquals(0, Streaks.weekStreak(twoOfThreeLastWeek, Schedule.Weekly(3), today = today))
    }

    @Test
    fun `an in-progress week below target does not break the streak`() {
        val completions = dates("2026-08-10", "2026-08-12", "2026-08-14", "2026-08-17")

        assertEquals(1, Streaks.weekStreak(completions, Schedule.Weekly(3), today = today))
    }

    @Test
    fun `a current week that already hit extends the streak`() {
        val completions = dates(
            "2026-08-10",
            "2026-08-12",
            "2026-08-14",
            "2026-08-17",
        )

        assertEquals(2, Streaks.weekStreak(completions, Schedule.Weekly(1), today = today))
    }

    @Test
    fun `future-dated completions never pre-fill the current week`() {
        val withFuture = dates("2026-08-17", "2026-08-19")

        assertEquals(0, Streaks.weekStreak(withFuture, Schedule.Weekly(2), today = today))
    }

    @Test
    fun `over-completing a week counts once`() {
        val fourDays = dates("2026-08-10", "2026-08-11", "2026-08-12", "2026-08-13")

        assertEquals(1, Streaks.weekStreak(fourDays, Schedule.Weekly(3), today = today))
    }

    @Test
    fun `the week start setting changes the bucketing`() {
        val sundayAndMonday = dates("2026-08-16", "2026-08-17")

        val mondayStart = Streaks.weekStreak(sundayAndMonday, Schedule.Weekly(2), today, DayOfWeek.MONDAY)
        val sundayStart = Streaks.weekStreak(sundayAndMonday, Schedule.Weekly(2), today, DayOfWeek.SUNDAY)

        assertEquals("split across two Monday-start weeks", 0, mondayStart)
        assertEquals("together in one Sunday-start week", 1, sundayStart)
    }

    @Test
    fun `week streaks cross the year boundary without week-number bugs`() {
        val completions = dates(
            "2026-12-22",
            "2026-12-24",
            "2026-12-29",
            "2027-01-02",
            "2027-01-05",
            "2027-01-08",
        )

        val streak = Streaks.weekStreak(completions, Schedule.Weekly(2), LocalDate.parse("2027-01-08"))

        assertEquals(3, streak)
    }

    @Test
    fun `sunday start also crosses the year boundary cleanly`() {
        val completions = dates(
            "2026-12-22",
            "2026-12-24",
            "2026-12-29",
            "2027-01-02",
            "2027-01-05",
            "2027-01-08",
        )

        val streak = Streaks.weekStreak(completions, Schedule.Weekly(2), LocalDate.parse("2027-01-08"), DayOfWeek.SUNDAY)

        assertEquals(3, streak)
    }

    @Test
    fun `a zero-completion week between full weeks leaves only the latest`() {
        val completions = dates(
            "2026-08-03",
            "2026-08-05",
            "2026-08-17",
            "2026-08-18",
        )

        assertEquals(1, Streaks.weekStreak(completions, Schedule.Weekly(2), LocalDate.parse("2026-08-18")))
    }
}
