package com.gawi.core.domain.streak

import com.gawi.core.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakSnapshotTest {

    // A Monday, so week arithmetic reads plainly against the default week start.
    private val today = LocalDate.parse("2026-08-17")

    private fun dates(vararg days: String) = days.map(LocalDate::parse).toSet()

    private fun date(day: String) = LocalDate.parse(day)

    private fun daily(completed: Set<LocalDate>, on: LocalDate = today) = Streaks.snapshot(completed, Schedule.Daily, on, DayOfWeek.MONDAY)

    private fun weekly(completed: Set<LocalDate>, timesPerWeek: Int, on: LocalDate = today) =
        Streaks.snapshot(completed, Schedule.Weekly(timesPerWeek), on, DayOfWeek.MONDAY)

    @Test
    fun `no completions is neither running nor broken`() {
        assertEquals(StreakSnapshot.NONE, daily(emptySet()))
        assertEquals(StreakSnapshot.NONE, weekly(emptySet(), timesPerWeek = 3))
    }

    @Test
    fun `a live run reports no break`() {
        val run = dates("2026-08-15", "2026-08-16", "2026-08-17")

        assertEquals(StreakSnapshot(current = 3, previous = 0, brokenOn = null), daily(run))
    }

    @Test
    fun `an unfinished today keeps the run alive rather than reporting a break`() {
        // today-view §5: a row unchecked at 09:00 must not read 0.
        val run = dates("2026-08-14", "2026-08-15", "2026-08-16")

        assertEquals(StreakSnapshot(current = 3, previous = 0, brokenOn = null), daily(run))
    }

    @Test
    fun `a broken run keeps its old length and the day it was lost`() {
        // Mon-Thu completed, Fri and Sat missed, asked on Sat.
        val run = dates("2026-08-10", "2026-08-11", "2026-08-12", "2026-08-13")

        val snapshot = daily(run, on = date("2026-08-15"))

        assertEquals(StreakSnapshot(current = 0, previous = 4, brokenOn = date("2026-08-15")), snapshot)
    }

    @Test
    fun `a long-abandoned habit still names the day it broke`() {
        val run = dates("2026-01-03", "2026-01-04")

        val snapshot = daily(run)

        assertEquals(StreakSnapshot(current = 0, previous = 2, brokenOn = date("2026-01-06")), snapshot)
    }

    @Test
    fun `only the run that ended counts as previous, not every completion`() {
        // An older, longer run followed by a gap and a shorter recent one: the
        // break that just happened is the short one.
        val old = dates("2026-07-01", "2026-07-02", "2026-07-03", "2026-07-04")
        val recent = dates("2026-08-12", "2026-08-13")

        val snapshot = daily(old + recent, on = date("2026-08-15"))

        assertEquals(StreakSnapshot(current = 0, previous = 2, brokenOn = date("2026-08-15")), snapshot)
    }

    @Test
    fun `future-dated completions do not stand in for the last success`() {
        // Replay accepts future dates (fast clocks, imports); they must not
        // pre-fill a streak or mask a break.
        val run = dates("2026-08-10", "2026-08-11")
        val future = dates("2026-09-01")

        val snapshot = daily(run + future, on = date("2026-08-15"))

        assertEquals(StreakSnapshot(current = 0, previous = 2, brokenOn = date("2026-08-13")), snapshot)
    }

    @Test
    fun `a weekly break is measured and dated in weeks`() {
        // Two weeks hitting 2-of-2, then a week that missed. Asked in the week
        // after the miss, so the current week cannot rescue it either.
        val hit = dates(
            "2026-07-27",
            "2026-07-28",
            "2026-08-03",
            "2026-08-04",
        )

        val snapshot = weekly(hit, timesPerWeek = 2, on = date("2026-08-17"))

        assertEquals(StreakSnapshot(current = 0, previous = 2, brokenOn = date("2026-08-17")), snapshot)
    }

    @Test
    fun `a weekly habit below target this week is not yet broken`() {
        // 1 of 2 so far this week, with last week hit: still alive.
        val completed = dates("2026-08-10", "2026-08-11", "2026-08-17")

        assertEquals(StreakSnapshot(current = 1, previous = 0, brokenOn = null), weekly(completed, timesPerWeek = 2))
    }

    @Test
    fun `a weekly habit that never hit its target has nothing to lose`() {
        val completed = dates("2026-08-03", "2026-08-10")

        assertEquals(StreakSnapshot.NONE, weekly(completed, timesPerWeek = 3, on = date("2026-08-24")))
    }

    @Test
    fun `a later completion does not rewrite an earlier day's snapshot`() {
        // The property the whole design rests on. The projection is rebuilt by
        // replaying the entire log, so a snapshot for day D gets recomputed
        // long after D, against a completion set that has since grown. If those
        // later dates could reach back and change D's answer, incremental and
        // rebuilt state would disagree and architecture §4's invariant would
        // not hold.
        val run = dates("2026-08-10", "2026-08-11", "2026-08-12")
        val askedOnTheDay = daily(run, on = date("2026-08-14"))

        val recovered = run + dates("2026-08-20", "2026-08-21")
        val askedAgainAfterRecovery = daily(recovered, on = date("2026-08-14"))

        assertEquals(askedOnTheDay, askedAgainAfterRecovery)
        assertEquals(StreakSnapshot(current = 0, previous = 3, brokenOn = date("2026-08-14")), askedOnTheDay)
    }

    @Test
    fun `recentlyBroken is brokenOn equalling today`() {
        // today-view §4's mood input, answered with nothing stored.
        val run = dates("2026-08-14", "2026-08-15")

        val onTheDayItBroke = daily(run, on = date("2026-08-17"))
        val aDayLater = daily(run, on = date("2026-08-18"))

        assertEquals(date("2026-08-17"), onTheDayItBroke.brokenOn)
        assertEquals(date("2026-08-17"), aDayLater.brokenOn)
    }
}
