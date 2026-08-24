package com.gawi.feature.insights

import com.gawi.core.data.model.ReadContext
import com.gawi.core.data.model.TagEffort
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.rate.Rates
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The reads as the Insights screen draws them.
 *
 * Three inputs, deliberately kept apart: the habits say what exists, the
 * completions-by-habit say what was done, and the tag totals are their own
 * query. The headline is derived from the completions rather than from the tag
 * totals so there is **one** source for one number — the tag query excludes a
 * completion whose `HabitCreated` has not arrived, which is unreachable before
 * Phase 2 sync and would otherwise let two numbers on one screen disagree.
 */
internal fun overviewOf(period: Period, breakdown: Breakdown, context: ReadContext, reads: PeriodReads): InsightsUiState.Overview =
    with(reads) {
        InsightsUiState.Overview(
            period = period,
            breakdown = breakdown,
            // The union, not the sum: two habits done on one day is one active day.
            activeDays = completions.values.flatten().toSet().size,
            completions = completions.values.sumOf { it.size },
            habits = habits.toRates(period, context, completions),
            tags = tagEffort.toShares(),
        )
    }

/**
 * The three reads the screen is drawn from, as one value.
 *
 * They arrive from one `combine`, so they travel as one thing — and grouping
 * them says what the mapper's shape otherwise only implies: these are the same
 * period, read three ways, not three independent inputs that happen to be
 * passed together.
 */
internal data class PeriodReads(
    val habits: List<HabitState>,
    val completions: Map<HabitId, Set<LocalDate>>,
    val tagEffort: List<TagEffort>,
)

/**
 * A row per habit, ordered as the habit list is.
 *
 * **Archived habits are excluded here and counted in the tag totals**, which
 * looks inconsistent and is the same asymmetry the data layer already draws:
 * `observeTagEffort` counts them because effort spent does not stop having
 * happened, and `observeToday` hides them because archiving is a decision about
 * the future. Adherence is a question about the present tense, so it follows
 * `observeToday`.
 *
 * Ordered by name rather than by rate. A list that reordered itself as the
 * numbers moved would make the same habit hard to find twice running, and
 * ranking your own habits against each other is not what this screen is for.
 */
private fun List<HabitState>.toRates(period: Period, context: ReadContext, completions: Map<HabitId, Set<LocalDate>>): List<HabitRateUi> =
    filterNot { it.archived }.map { habit ->
        HabitRateUi(
            name = habit.name,
            schedule = habit.schedule.toLabelUi(),
            percent = habit.percentOver(period.window(context.today), context, completions[habit.id].orEmpty()),
        )
    }

/**
 * Clipped to the habit's start, and null when the period offered nothing.
 *
 * The clip is what the projected creation date bought: without it a habit made
 * halfway through a quarter reads as having missed the first half, which is a
 * number that is arithmetically right and accuses the user of days that were
 * never offered.
 */
private fun HabitState.percentOver(window: ClosedRange<LocalDate>, context: ReadContext, dates: Set<LocalDate>): Int? {
    val born = createdOn
    if (born != null && born > window.endInclusive) return null
    val from = if (born != null) maxOf(window.start, born) else window.start
    val rate = Rates.completionRate(dates, schedule, from..window.endInclusive, context.today, context.weekStart)
    return rate.fraction?.let { (it * PERCENT).roundToInt() }
}

/**
 * Bars scaled to the largest total, biggest first, untagged always last.
 *
 * Last regardless of size, because it is the residual rather than a competitor:
 * "Untagged" winning the list would read as a tag that beat the others, when it
 * is the absence of one.
 *
 * Scaled to the largest rather than to the sum. A bar whose length were its
 * share of the whole would leave the longest one a third of the track on any
 * realistic spread, and the question these rows answer is which tag took the
 * most, not what fraction of a pie it was.
 */
private fun List<TagEffort>.toShares(): List<TagShareUi> {
    val largest = maxOfOrNull { it.completions } ?: return emptyList()
    return sortedWith(compareBy({ it.tag == null }, { -it.completions }, { it.tag }))
        .map { TagShareUi(name = it.tag, completions = it.completions, fill = it.completions.toFloat() / largest) }
}

private const val PERCENT = 100
