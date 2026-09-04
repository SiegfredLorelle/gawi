package com.gawi.core.domain.streak

import com.gawi.core.domain.model.Schedule
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The longest run a habit managed *inside* a period — PRD §5 Phase 1.5's
 * "best streak", measured for a retrospective rather than for today.
 *
 * Not [Streaks]: that answers "how long is the run ending now", and looking
 * back at last quarter asks "how long was the longest run that quarter", which
 * the current streak says nothing about once it has broken. Same unit, though —
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
        is Schedule.Daily -> longestRun(
            completedDates.filter { it in window && !it.isAfter(today) }.toSet(),
            prev = { it.minusDays(1) },
            next = { it.plusDays(1) },
        )

        is Schedule.Weekly -> longestRun(
            hitWeeksWithin(completedDates, schedule, window, today, weekStart),
            prev = { it.minusWeeks(1) },
            next = { it.plusWeeks(1) },
        )
    }

    /**
     * Week-start dates of the weeks that met the target, counting only weeks
     * that lie **wholly** inside the window.
     *
     * A week the window only partly contains cannot be judged: three of its
     * days may be in the period and the other four — and the completions on
     * them — out of it, so a miss there would be the window's doing rather than
     * the user's. `Rates.completionRate` draws the same line for the same
     * reason — and, like it, applies the line to the window *as handed in*, so
     * a habit created midweek has its birth week judged partial too. The
     * current week is not excluded for being unfinished: if it has already hit
     * its target it is a hit week, as `Streaks` also holds.
     *
     * The hit rule itself is [Streaks.hitWeeks], not a copy of it.
     */
    private fun hitWeeksWithin(
        completedDates: Set<LocalDate>,
        schedule: Schedule.Weekly,
        window: ClosedRange<LocalDate>,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): Set<LocalDate> {
        val lastDayOffset = Schedule.DAYS_PER_WEEK - 1L
        val inside = completedDates.filter { it in window }.toSet()
        return Streaks.hitWeeks(inside, schedule, today, weekStart)
            .filter { week -> week >= window.start && week.plusDays(lastDayOffset) <= window.endInclusive }
            .toSet()
    }

    /**
     * Longest chain in [present] in which each member is [next] of the one
     * before. A chain is walked from its head only — the member whose [prev] is
     * absent — so the whole thing is linear in the set, which matters because
     * the mapper runs it per habit on every emission.
     */
    private fun longestRun(present: Set<LocalDate>, prev: (LocalDate) -> LocalDate, next: (LocalDate) -> LocalDate): Int {
        var best = 0
        for (start in present) {
            if (prev(start) in present) continue
            var length = 0
            var cursor = start
            while (cursor in present) {
                length++
                cursor = next(cursor)
            }
            best = maxOf(best, length)
        }
        return best
    }
}
