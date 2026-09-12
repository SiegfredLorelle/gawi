package com.gawi.core.domain.streak

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.time.weekStartOn
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pure streak calculators over projected completion dates (architecture
 * §5). Deliberately NOT part of projection state: streaks depend on
 * "today", which is not in the event log — folding them into apply would
 * break the incremental-vs-rebuild invariant. The data layer calls these
 * after each projection transaction and on day rollover, caching the result
 * in its derived streak table.
 *
 * Liveness semantics: an unfinished current day (or a current week still
 * below target) does not break a streak — it simply hasn't extended it yet.
 * A finished day/week that missed spends a spare life if the run has one and
 * breaks the run only when it has none ([replay], PRD §8 OQ-3).
 */
object Streaks {

    /**
     * The most spare lives a run can hold — the three right gills Momo draws
     * (docs/ux/momo.md §3).
     */
    const val MAX_SPARE = 3

    /**
     * Clean units — completed days, or weeks that met their target — that earn
     * one spare life.
     *
     * Deliberately not [Schedule.DAYS_PER_WEEK], which is also 7. That one is
     * how long a week is and this one is what a spare life costs; tying them
     * together would make a change to either silently move the other, and this
     * one counts weeks as readily as days.
     */
    const val CLEAN_PER_SPARE = 7

    /** Length of the run ending at today, counting only units actually completed. */
    fun dayStreak(completedDates: Set<LocalDate>, today: LocalDate): Int = dailySnapshot(completedDates, today).current

    /**
     * The run ending at the current week, counted in weeks that met
     * [Schedule.Weekly.timesPerWeek] — **not necessarily consecutive**, since a
     * missed week the run had a spare life for is forgiven rather than counted
     * ([replay]). Which weeks count at all is [hitWeeks]'.
     */
    fun weekStreak(completedDates: Set<LocalDate>, schedule: Schedule.Weekly, today: LocalDate, weekStart: DayOfWeek): Int =
        weeklySnapshot(completedDates, schedule, today, weekStart).current

    /**
     * The streak plus the context the Today view needs to render a break:
     * the run that was lost and the date it was lost on (docs/ux/today-view.md
     * §5, the `was 4` beside a `0`), and the spare lives the run is carrying.
     *
     * This is a pure function of [completedDates], [schedule], [today] and
     * [weekStart] — deliberately, and it is the one property worth protecting
     * here. The tempting reading of "previous" is "the last non-zero value the
     * cached streak row ever held", which depends on when the app happened to
     * be opened: a user away for a week never observes the intermediate values,
     * so a rebuild would disagree with the incremental path and
     * architecture §4's incremental-≡-rebuild invariant would not hold. Replaying the run
     * forward from its first completion has no such history.
     *
     * [StreakSnapshot.brokenOn] also answers the mood spec's `recentlyBroken`
     * input (today-view §4) with nothing stored. Like [StreakSnapshot.current]
     * itself, it is denominated in the schedule's own unit, so a caller needs
     * the schedule to read it — the same schedule it already needs to know
     * whether a `3` means days or weeks.
     */
    fun snapshot(completedDates: Set<LocalDate>, schedule: Schedule, today: LocalDate, weekStart: DayOfWeek): StreakSnapshot =
        when (schedule) {
            is Schedule.Daily -> dailySnapshot(completedDates, today)
            is Schedule.Weekly -> weeklySnapshot(completedDates, schedule, today, weekStart)
        }

    // Future-dated completions exist (fast clocks, imports) and must neither
    // extend a run nor earn a spare life, so the set is bounded at today. The
    // weekly case is bounded inside hitWeeks instead, which BestRun shares.
    private fun dailySnapshot(completedDates: Set<LocalDate>, today: LocalDate): StreakSnapshot =
        replay(completedDates.filterTo(mutableSetOf()) { !it.isAfter(today) }, now = today) { it.plusDays(1) }

    private fun weeklySnapshot(
        completedDates: Set<LocalDate>,
        schedule: Schedule.Weekly,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): StreakSnapshot =
        replay(hitWeeks(completedDates, schedule, today, weekStart), now = weekStartOn(today, weekStart)) { it.plusWeeks(1) }

    /**
     * Walks the run forward from its first success to [now], one unit at a
     * time, and reports where it ended up.
     *
     * **Forward rather than backwards from [now], which is what gills cost.**
     * Whether a gap breaks the run depends on the spare lives earned before it
     * (PRD §8 OQ-3), and those are a property of the history rather than of the
     * gap, so there is nothing to read at the gap itself. One unit is a day for
     * a daily habit and a week that met its target for a weekly one; [hit] and
     * [now] are already denominated in it, so this counts either without
     * knowing which.
     *
     * The four branches are the rule, in precedence order:
     *
     * - A completed unit extends the run and the clean count. At
     *   [MAX_SPARE] the clean count *holds* rather than banking, so a spare
     *   life always costs a fresh [CLEAN_PER_SPARE] however long ago it was
     *   spent. momo.md §3 fixes the seven and the cap; that the clock holds at
     *   the cap and restarts on a spend is §3's "on their own clock" spelled
     *   out, and §3 carries the consequence a user can feel.
     * - [now] itself is never judged: an unfinished day, or a week still below
     *   its target, has not broken anything yet — it simply has not extended
     *   the run.
     * - A missed unit with a spare life spends it. **The run is preserved and
     *   not extended**: it counts units the user turned up for, so a forgiven
     *   miss leaves the number where it was rather than moving it. Spending
     *   restarts the clean count.
     * - A missed unit with nothing spare breaks the run, dating the break one
     *   unit on — which is when [StreakSnapshot.current] first reads zero,
     *   the invariant that type states.
     *
     * The break only records a run that was live, so a long trailing gap
     * cannot re-date a break it did not cause. A later break does overwrite an
     * earlier one, which is right: the snapshot describes the most recent.
     *
     * **A dead run is walked over rather than through, and that bound is not an
     * optimisation.** Stepping one unit at a time would make the cost the
     * calendar span from the first completion, and nothing bounds that span: the
     * retro window is a command rule that deliberately does not reach replay
     * (architecture §4), and an imported log carries whatever `logical_date` it
     * was written with. A single absurd date would otherwise be a hang on every
     * projection write, on a log the user cannot open to repair.
     */
    private fun replay(hit: Set<LocalDate>, now: LocalDate, next: (LocalDate) -> LocalDate): StreakSnapshot {
        val ordered = hit.sorted()
        var unit = ordered.firstOrNull() ?: return StreakSnapshot.NONE
        var run = 0
        var spare = 0
        var clean = 0
        var previous = 0
        var brokenOn: LocalDate? = null

        while (!unit.isAfter(now)) {
            when {
                unit in hit -> {
                    run++
                    // Two statements rather than one nested pair, and they
                    // compose because the clean count is provably 0 at the cap:
                    // reaching it is what last zeroed it, and the guard below
                    // has kept it there since. So "the cap does not bank" and
                    // "a spend starts a fresh seven" are the same line.
                    if (spare < MAX_SPARE) clean++
                    if (clean == CLEAN_PER_SPARE) {
                        spare++
                        clean = 0
                    }
                }

                unit == now -> Unit

                spare > 0 -> {
                    spare--
                    clean = 0
                }

                else -> {
                    if (run > 0) {
                        previous = run
                        brokenOn = next(unit)
                    }
                    run = 0
                    spare = 0
                    clean = 0
                }
            }

            // Read after the `when`, so a completed unit has already set the
            // run and only a dead one takes the jump.
            unit = advance(ordered, unit, now, live = run > 0, next) ?: break
        }

        return when {
            run > 0 -> StreakSnapshot(current = run, previous = 0, brokenOn = null, spare = spare)
            else -> StreakSnapshot(current = 0, previous = previous, brokenOn = brokenOn, spare = 0)
        }
    }

    /**
     * The unit after [unit], or null when nothing left can change the answer.
     *
     * A live run is walked one unit at a time, because every unit in it counts
     * — completed, or forgiven at the cost of a spare life. A dead one is
     * jumped instead: [replay] writes `previous` and `brokenOn` only while a run
     * is live, and a zero run carries a zero spare, so the empty calendar
     * between a break and the next completion cannot change anything and must
     * not be counted. That is the difference between a cost that follows the
     * log and one that follows the calendar, which nothing bounds.
     *
     * A binary search rather than a scan, [ordered] being sorted: the jump
     * happens once per break, and a scan would make the walk quadratic in a log
     * that breaks often.
     */
    private fun advance(
        ordered: List<LocalDate>,
        unit: LocalDate,
        now: LocalDate,
        live: Boolean,
        next: (LocalDate) -> LocalDate,
    ): LocalDate? {
        if (live) return next(unit)
        val found = ordered.binarySearch(unit)
        val after = if (found >= 0) found + 1 else -(found + 1)
        return ordered.getOrNull(after)?.takeIf { !it.isAfter(now) }
    }

    /**
     * Week-start dates that met [Schedule.Weekly.timesPerWeek], up to [today].
     *
     * Internal rather than private because [BestRun] judges weeks by the same
     * rule, and "which weeks count" written twice is how the weekly best run on
     * Insights would come to disagree with the weekly streak on Today.
     *
     * Dates after [today] are ignored — replay accepts future-dated completions
     * (fast device clocks, imports) and they must not pre-fill a week. Weeks are
     * keyed by their start date via [weekStart] arithmetic — never by
     * week-of-year numbers, which misbucket the days around New Year.
     *
     * Takes the [Schedule.Weekly] rather than a bare count so the 1..7 bound
     * that type enforces cannot be bypassed here. A raw target degrades silently
     * instead of failing: 0 is indistinguishable from 1, because weeks with no
     * completions are not keys in the grouping to begin with, and anything above
     * 7 can never be met.
     */
    internal fun hitWeeks(
        completedDates: Set<LocalDate>,
        schedule: Schedule.Weekly,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): Set<LocalDate> = completedDates
        .filter { !it.isAfter(today) }
        .groupingBy { weekStartOn(it, weekStart) }
        .eachCount()
        .filterValues { it >= schedule.timesPerWeek }
        .keys
}
