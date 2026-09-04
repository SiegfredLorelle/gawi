package com.gawi.core.domain.rate

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.time.weekStartOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The completion-rate calculator (docs/ux/insights.md §4). Pure, and here
 * rather than in `:feature:insights` for the reason [com.gawi.core.domain.streak.Streaks]
 * is here: the denominator comes from the schedule rules, and those live in
 * this module. A feature module counting rows would get the weekly case wrong.
 *
 * **Only finished units count.** An unfinished day, or a week still below
 * target, is not a miss — it is a unit the user still has. This is the same
 * liveness rule the streak calculators follow, and it matters far more here:
 * a rate that counted the current week as a full opportunity would read as a
 * collapse every Monday morning.
 *
 * **A window that predates the habit is still the caller's problem**, but the
 * caller now has what it needs. A range starting before the habit existed
 * yields a rate that is arithmetically right and meaningless, and clipping it is
 * a presentation decision deliberately not made here — this function has no
 * opinion about which window it is handed.
 *
 * [com.gawi.core.domain.projection.HabitState.createdOn] exists, so the clip
 * has a real date to work from. **Do not reach for the earliest date in
 * `completedDates`** — it biases every rate upward, because a window that
 * begins at the first completion always begins on a day the habit succeeded,
 * and a habit created and then ignored for two weeks loses those two weeks
 * silently.
 */
object Rates {

    /**
     * The share of [schedule]'s target met inside [window], measured against
     * [today] so that unfinished units are excluded.
     *
     * [window] is inclusive at both ends and may extend past [today] — a period
     * picker offering "this year" will hand over a range that mostly has not
     * happened yet, and clipping it here is cheaper than making every caller
     * remember to.
     */
    fun completionRate(
        completedDates: Set<LocalDate>,
        schedule: Schedule,
        window: ClosedRange<LocalDate>,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): CompletionRate = when (schedule) {
        is Schedule.Daily -> dailyRate(completedDates, window, today)
        is Schedule.Weekly -> weeklyRate(completedDates, schedule, window, today, weekStart)
    }

    /**
     * Completions over days elapsed.
     *
     * Today is excluded on both sides of the fraction: it has not finished, so
     * it is neither an opportunity nor a miss. A completion logged this morning
     * therefore does not move the rate until tomorrow, which is the honest
     * reading — the alternative counts a day the user is still inside.
     */
    private fun dailyRate(completedDates: Set<LocalDate>, window: ClosedRange<LocalDate>, today: LocalDate): CompletionRate.Daily {
        val lastFinished = minOf(window.endInclusive, today.minusDays(1))
        if (lastFinished < window.start) return CompletionRate.Daily(completed = 0, opportunities = 0)
        val finished = window.start..lastFinished
        return CompletionRate.Daily(
            completed = completedDates.count { it in finished },
            opportunities = (ChronoUnit.DAYS.between(window.start, lastFinished) + 1).toInt(),
        )
    }

    /**
     * Completions over `timesPerWeek × weeks elapsed`, counting only weeks that
     * are **both fully inside [window] and fully over**.
     *
     * Whole weeks only, on both bounds. A window starting on a Wednesday makes
     * that week a partial one, and charging a user the full weekly target for
     * four days of it would understate every rate whose period does not happen
     * to begin on the week start. The same cut at the other end drops the
     * current week, which has not finished.
     *
     * Per-week counts are **capped at the target**: five completions in a
     * 3-per-week week are three met opportunities and two spare, not 167%. This
     * makes the metric adherence rather than effort, which is what a
     * "completion rate" is read as. Effort is what §5's tag distribution
     * measures, and it deliberately does not cap.
     */
    private fun weeklyRate(
        completedDates: Set<LocalDate>,
        schedule: Schedule.Weekly,
        window: ClosedRange<LocalDate>,
        today: LocalDate,
        weekStart: DayOfWeek,
    ): CompletionRate.Weekly {
        val lastDayOffset = Schedule.DAYS_PER_WEEK - 1L
        val firstWholeWeek = weekStartOn(window.start, weekStart)
            .let { if (it == window.start) it else it.plusWeeks(1) }
        val lastWholeWeek = weekStartOn(window.endInclusive, weekStart)
            .let { if (it.plusDays(lastDayOffset) <= window.endInclusive) it else it.minusWeeks(1) }
        // The current week has not finished, so the last week that can count is
        // the one before it, whatever the window says.
        val lastFinishedWeek = minOf(lastWholeWeek, weekStartOn(today, weekStart).minusWeeks(1))
        if (lastFinishedWeek < firstWholeWeek) {
            return CompletionRate.Weekly(schedule.timesPerWeek, completed = 0, opportunities = 0)
        }
        val weeks = (ChronoUnit.WEEKS.between(firstWholeWeek, lastFinishedWeek) + 1).toInt()
        val counted = firstWholeWeek..lastFinishedWeek.plusDays(lastDayOffset)
        val metPerWeek = completedDates
            .filter { it in counted }
            .groupingBy { weekStartOn(it, weekStart) }
            .eachCount()
            .values
            .sumOf { minOf(it, schedule.timesPerWeek) }
        return CompletionRate.Weekly(
            timesPerWeek = schedule.timesPerWeek,
            completed = metPerWeek,
            opportunities = weeks * schedule.timesPerWeek,
        )
    }
}
