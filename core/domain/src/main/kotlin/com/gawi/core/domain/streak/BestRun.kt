package com.gawi.core.domain.streak

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.time.weekStartOn
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The longest run a habit managed *inside* a period — PRD §5 Phase 1.5's
 * "best streak", measured for a retrospective rather than for today.
 *
 * Not [Streaks]: that answers "how long is the run ending now", and a review
 * of last quarter wants "how long was the longest run that quarter", which the
 * current streak says nothing about once it has broken. Same unit, though —
 * days for a daily habit, weeks for a weekly one — so a caller still needs the
 * schedule to read the number, exactly as with [StreakSnapshot.current].
 *
 * Two clips, both deliberate:
 *
 * - **Inside the window only.** A run that began before the period counts only
 *   the part that falls in it. The question is what happened *in* the quarter,
 *   and a 40-day run that ended on its second day was not a 40-day quarter.
 * - **Nothing after [today].** Replay accepts future-dated completions (fast
 *   clocks, imports) and they must not lengthen a run, the same rule
 *   [Streaks.weekStreak] applies.
 *
 * There is no "worst run" beside this, and that is a decision rather than an
 * omission: every habit's worst run is zero, so the number carries nothing.
 */
object BestRun {

    /** Longest run in the schedule's own unit; 0 when nothing in the window was completed. */
    fun within(
        completedDates: Set<LocalDate>,
        schedule: Schedule,
        window: ClosedRange<LocalDate>,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): Int = when (schedule) {
        is Schedule.Daily -> longestRun(completedDates.filter { it in window && !it.isAfter(today) }) { it.plusDays(1) }
        is Schedule.Weekly -> longestRun(hitWeeksWithin(completedDates, schedule, window, today, weekStart)) { it.plusWeeks(1) }
    }

    /**
     * Week-start dates of the weeks that met the target, counting only weeks
     * that lie **wholly** inside the window.
     *
     * A week the window only partly contains cannot be judged: three of its
     * days may be in the period and the other four — and the completions on
     * them — out of it, so a miss there would be the window's doing rather than
     * the user's. `Rates.completionRate` draws the same line for the same
     * reason. The current week is not excluded for being unfinished: if it has
     * already hit its target it is a hit week, as `Streaks` also holds.
     */
    private fun hitWeeksWithin(
        completedDates: Set<LocalDate>,
        schedule: Schedule.Weekly,
        window: ClosedRange<LocalDate>,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): Collection<LocalDate> {
        val lastDayOffset = Schedule.DAYS_PER_WEEK - 1L
        return completedDates
            .filter { it in window && !it.isAfter(today) }
            .groupingBy { weekStartOn(it, weekStart) }
            .eachCount()
            .filter { (week, count) ->
                count >= schedule.timesPerWeek && week >= window.start && week.plusDays(lastDayOffset) <= window.endInclusive
            }
            .keys
    }

    /** Longest chain of dates in which each is [next] of the one before. */
    private fun longestRun(dates: Collection<LocalDate>, next: (LocalDate) -> LocalDate): Int {
        val present = dates.toSet()
        var best = 0
        for (start in present) {
            // Only the head of a chain is walked, so each date is visited once.
            if (present.none { next(it) == start }) {
                var length = 0
                var cursor = start
                while (cursor in present) {
                    length++
                    cursor = next(cursor)
                }
                best = maxOf(best, length)
            }
        }
        return best
    }
}
