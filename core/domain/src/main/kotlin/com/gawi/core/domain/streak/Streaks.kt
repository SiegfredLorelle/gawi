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
 * A finished day/week that missed resets to zero. Grace mechanics are
 * deferred (PRD OQ-3).
 */
object Streaks {

    /** Length of the consecutive-day run ending at today (if completed) or yesterday. */
    fun dayStreak(completedDates: Set<LocalDate>, today: LocalDate): Int {
        val anchor = when {
            today in completedDates -> today
            today.minusDays(1) in completedDates -> today.minusDays(1)
            else -> return 0
        }
        var streak = 0
        var cursor = anchor
        while (cursor in completedDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /**
     * Consecutive calendar weeks with at least [Schedule.Weekly.timesPerWeek]
     * distinct completed dates, ending at the current week (if it already
     * hit) or the previous one. Dates after [today] are ignored — replay
     * accepts future-dated completions (fast device clocks, imports) and they
     * must not pre-fill a week. Weeks are keyed by their start date via
     * [weekStart] arithmetic — never by week-of-year numbers, which
     * misbucket the days around New Year.
     *
     * Takes the [Schedule.Weekly] rather than a bare count so the 1..7 bound
     * that type enforces cannot be bypassed here. A raw target degrades
     * silently instead of failing: 0 is indistinguishable from 1, because
     * weeks with no completions are not keys in the grouping to begin with,
     * and anything above 7 can never be met.
     */
    fun weekStreak(completedDates: Set<LocalDate>, schedule: Schedule.Weekly, today: LocalDate, weekStart: DayOfWeek): Int {
        val hitWeeks = hitWeeks(completedDates, schedule, today, weekStart)
        val currentWeek = weekStartOn(today, weekStart)
        val anchor = when {
            currentWeek in hitWeeks -> currentWeek
            currentWeek.minusWeeks(1) in hitWeeks -> currentWeek.minusWeeks(1)
            else -> return 0
        }
        var streak = 0
        var cursor = anchor
        while (cursor in hitWeeks) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    /**
     * The streak plus the context the Today view needs to render a break:
     * the run that was lost and the date it was lost on (docs/ux/today-view.md
     * §5, the `was 4` beside a `0`).
     *
     * This is a pure function of [completedDates], [schedule], [today] and
     * [weekStart] — deliberately, and it is the one property worth protecting
     * here. The tempting reading of "previous" is "the last non-zero value the
     * cached streak row ever held", which depends on when the app happened to
     * be opened: a user away for a week never observes the intermediate values,
     * so a rebuild would disagree with the incremental path and architecture
     * §4's incremental-≡-rebuild invariant would not hold. Re-running the same
     * calculator anchored at the last success has no such history.
     *
     * [StreakSnapshot.brokenOn] also answers the mood spec's `recentlyBroken`
     * input (today-view §4) with nothing stored, as `brokenOn == today` for a
     * daily habit and `brokenOn == the current week's start` for a weekly one.
     * Like [StreakSnapshot.current] itself, it is denominated in the schedule's
     * own unit, so a caller needs the schedule to read it — the same schedule
     * it already needs to know whether a `3` means days or weeks.
     */
    fun snapshot(completedDates: Set<LocalDate>, schedule: Schedule, today: LocalDate, weekStart: DayOfWeek): StreakSnapshot =
        when (schedule) {
            is Schedule.Daily -> dailySnapshot(completedDates, today)
            is Schedule.Weekly -> weeklySnapshot(completedDates, schedule, today, weekStart)
        }

    private fun dailySnapshot(completedDates: Set<LocalDate>, today: LocalDate): StreakSnapshot {
        val current = dayStreak(completedDates, today)
        // Future-dated completions exist (fast clocks, imports) and must not
        // count as the last success, so the search is bounded at today.
        val lastCompleted = completedDates.filter { !it.isAfter(today) }.maxOrNull()
        return when {
            current > 0 -> StreakSnapshot(current, previous = 0, brokenOn = null)

            lastCompleted == null -> StreakSnapshot.NONE

            else -> StreakSnapshot(
                current = 0,
                previous = dayStreak(completedDates, lastCompleted),
                // Two days on, not one. An unfinished day does not break a
                // streak, so the day after the last completion still reads
                // positive; the first day this reads zero is the one after
                // that, and that is the day the user sees the break.
                brokenOn = lastCompleted.plusDays(2),
            )
        }
    }

    private fun weeklySnapshot(
        completedDates: Set<LocalDate>,
        schedule: Schedule.Weekly,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): StreakSnapshot {
        val current = weekStreak(completedDates, schedule, today, weekStart)
        val lastHitWeek = hitWeeks(completedDates, schedule, today, weekStart)
            .filter { !it.isAfter(weekStartOn(today, weekStart)) }
            .maxOrNull()
        return when {
            current > 0 -> StreakSnapshot(current, previous = 0, brokenOn = null)

            lastHitWeek == null -> StreakSnapshot.NONE

            else -> StreakSnapshot(
                current = 0,
                // Anchored on the last day of the week that was hit, so
                // weekStreak's own "ignore dates after today" filter measures
                // the run as it stood when it ended rather than clipping it.
                previous = weekStreak(completedDates, schedule, lastHitWeek.plusWeeks(1).minusDays(1), weekStart),
                // The start of the first week this reads zero — two weeks on
                // for the same reason the daily case is two days on. A week
                // still below target has not broken anything yet.
                brokenOn = lastHitWeek.plusWeeks(2),
            )
        }
    }

    /**
     * Week-start dates that met [Schedule.Weekly.timesPerWeek], up to [today].
     *
     * Internal rather than private because [BestRun] judges weeks by the same
     * rule, and "which weeks count" written twice is how the weekly best run on
     * Insights would come to disagree with the weekly streak on Today.
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
