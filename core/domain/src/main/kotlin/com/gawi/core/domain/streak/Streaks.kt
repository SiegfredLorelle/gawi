package com.gawi.core.domain.streak

import com.gawi.core.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

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
    fun weekStreak(
        completedDates: Set<LocalDate>,
        schedule: Schedule.Weekly,
        today: LocalDate,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): Int {
        val hitWeeks = completedDates
            .filter { !it.isAfter(today) }
            .groupingBy { it.with(TemporalAdjusters.previousOrSame(weekStart)) }
            .eachCount()
            .filterValues { it >= schedule.timesPerWeek }
            .keys
        val currentWeek = today.with(TemporalAdjusters.previousOrSame(weekStart))
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
}
