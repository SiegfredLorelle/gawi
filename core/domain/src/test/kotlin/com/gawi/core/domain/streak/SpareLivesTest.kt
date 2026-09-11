package com.gawi.core.domain.streak

import com.gawi.core.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The grace mechanic PRD §8 OQ-3 decides and docs/ux/momo.md §3 draws: a run
 * earns one spare life per seven clean units, capped at three, and a missed
 * unit spends one rather than breaking the run.
 *
 * Separate from [DayStreakTest] and [WeekStreakTest] because those two ask what
 * a run is worth and this asks what it survives. Both denominations are here
 * rather than split across those files: it is one rule counted in days for a
 * daily habit and in target-met weeks for a weekly one, and reading them side
 * by side is what shows that.
 */
class SpareLivesTest {

    /** A Monday, so the weekly cases read plainly against the default week start. */
    private val today = LocalDate.parse("2026-08-17")

    private fun dates(vararg days: String) = days.map(LocalDate::parse).toSet()

    /** [count] consecutive days starting at [from]. */
    private fun consecutive(from: String, count: Int): Set<LocalDate> =
        generateSequence(LocalDate.parse(from)) { it.plusDays(1) }.take(count).toSet()

    private fun daily(completed: Set<LocalDate>, on: LocalDate = today) = Streaks.snapshot(completed, Schedule.Daily, on, DayOfWeek.MONDAY)

    /** One completion in each of [count] consecutive weeks, the last ending before [today]'s week. */
    private fun weeks(firstWeekStart: String, count: Int): Set<LocalDate> =
        generateSequence(LocalDate.parse(firstWeekStart)) { it.plusWeeks(1) }.take(count).toSet()

    private fun weekly(completed: Set<LocalDate>, on: LocalDate = today) =
        Streaks.snapshot(completed, Schedule.Weekly(1), on, DayOfWeek.MONDAY)

    @Test
    fun `seven clean days earn one spare life and six earn none`() {
        assertEquals(StreakSnapshot(current = 7, previous = 0, brokenOn = null, spare = 1), daily(consecutive("2026-08-11", 7)))
        assertEquals(StreakSnapshot(current = 6, previous = 0, brokenOn = null, spare = 0), daily(consecutive("2026-08-12", 6)))
    }

    @Test
    fun `a missed day spends a spare life and leaves the run where it was`() {
        val run = consecutive("2026-08-04", 7)

        assertEquals(StreakSnapshot(current = 7, previous = 0, brokenOn = null, spare = 1), daily(run, on = LocalDate.parse("2026-08-10")))
        // 2026-08-11 missed, and asked on the 12th so that miss is a finished day.
        assertEquals(StreakSnapshot(current = 7, previous = 0, brokenOn = null, spare = 0), daily(run, on = LocalDate.parse("2026-08-12")))
    }

    @Test
    fun `a second missed day with nothing spare breaks the run`() {
        val run = consecutive("2026-08-04", 7)

        val snapshot = daily(run, on = LocalDate.parse("2026-08-13"))

        assertEquals(StreakSnapshot(current = 0, previous = 7, brokenOn = LocalDate.parse("2026-08-13"), spare = 0), snapshot)
    }

    @Test
    fun `spare lives accumulate one per seven days and stop at three`() {
        assertEquals(2, daily(consecutive("2026-08-04", 14)).spare)
        assertEquals(3, daily(consecutive("2026-07-28", 21)).spare)
        assertEquals(3, daily(consecutive("2026-07-21", 28)).spare)
    }

    @Test
    fun `spending a spare life restarts the seven days rather than resuming them`() {
        // Seven clean days earn one, six more bank six, the miss on the 14th
        // spends the life — and the day after it is the first of a fresh seven,
        // not the seventh of the old one, so the next miss breaks the run.
        val completed = consecutive("2026-08-01", 7) + consecutive("2026-08-08", 6) + dates("2026-08-15")

        val snapshot = daily(completed)

        assertEquals(StreakSnapshot(current = 0, previous = 14, brokenOn = today, spare = 0), snapshot)
    }

    @Test
    fun `a gap exactly as long as the spare lives is forgiven`() {
        // Two lives earned, then 2026-08-15 and 2026-08-16 missed.
        val completed = consecutive("2026-08-01", 14) + dates("2026-08-17")

        assertEquals(StreakSnapshot(current = 15, previous = 0, brokenOn = null, spare = 0), daily(completed))
    }

    @Test
    fun `one day more than the spare lives breaks the run`() {
        val completed = consecutive("2026-08-01", 14)

        val snapshot = daily(completed, on = LocalDate.parse("2026-08-18"))

        assertEquals(StreakSnapshot(current = 0, previous = 14, brokenOn = LocalDate.parse("2026-08-18"), spare = 0), snapshot)
    }

    @Test
    fun `a run rebuilt after a break starts with no spare lives`() {
        val threeLivesThenAbandoned = consecutive("2026-07-01", 21)

        val snapshot = daily(threeLivesThenAbandoned + dates("2026-08-17"))

        assertEquals(StreakSnapshot(current = 1, previous = 0, brokenOn = null, spare = 0), snapshot)
    }

    @Test
    fun `retroactively filling a forgiven gap refunds the spare life`() {
        val run = consecutive("2026-08-04", 7)
        val asked = LocalDate.parse("2026-08-12")

        assertEquals(StreakSnapshot(current = 7, previous = 0, brokenOn = null, spare = 0), daily(run, on = asked))
        assertEquals(StreakSnapshot(current = 8, previous = 0, brokenOn = null, spare = 1), daily(run + dates("2026-08-11"), on = asked))
    }

    @Test
    fun `future-dated completions neither extend a run nor earn a spare life`() {
        val withFuture = consecutive("2026-08-11", 7) + consecutive("2026-08-18", 7)

        assertEquals(StreakSnapshot(current = 7, previous = 0, brokenOn = null, spare = 1), daily(withFuture))
    }

    @Test
    fun `seven target-met weeks earn one spare life`() {
        assertEquals(StreakSnapshot(current = 7, previous = 0, brokenOn = null, spare = 1), weekly(weeks("2026-06-29", 7)))
    }

    @Test
    fun `a missed week spends a spare life and leaves the run where it was`() {
        // Seven weeks to 2026-08-03, nothing in the week of the 10th, asked in
        // the week of the 17th so that miss is a finished week.
        assertEquals(StreakSnapshot(current = 7, previous = 0, brokenOn = null, spare = 0), weekly(weeks("2026-06-22", 7)))
    }

    @Test
    fun `a missed week with nothing spare breaks the run, dated a week on`() {
        val snapshot = weekly(weeks("2026-06-15", 7))

        assertEquals(StreakSnapshot(current = 0, previous = 7, brokenOn = today, spare = 0), snapshot)
    }
}
