package com.gawi.feature.insights

import com.gawi.core.data.model.ReadContext
import com.gawi.core.data.model.TagEffort
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.rate.Rates
import com.gawi.core.domain.streak.BestRun
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
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
internal fun overviewOf(
    period: Period,
    back: Int,
    breakdown: Breakdown,
    context: ReadContext,
    reads: PeriodReads,
): InsightsUiState.Overview = with(reads) {
    InsightsUiState.Overview(
        period = period,
        label = period.labelOf(window),
        canStepLater = back > 0,
        breakdown = breakdown,
        // The union, not the sum: two habits done on one day is one active day.
        activeDays = completions.values.flatten().toSet().size,
        completions = completions.values.sumOf { it.size },
        focus = focusShift(previousTagEffort, tagEffort),
        trend = if (period == Period.MONTH) emptyList() else trendOf(window, context.today, completions),
        habits = habits.toRates(window, context, completions),
        tags = tagEffort.toShares(),
        // The unfiltered list, which is the whole reason this is a separate
        // field: `habits.toRates` drops the archived ones.
        hasAnyHabit = habits.isNotEmpty(),
    )
}

/**
 * The reads the screen is drawn from, and the window they were made over.
 *
 * They arrive from one `combine`, so they travel as one thing — and grouping
 * them says what the mapper's shape otherwise only implies: these are the same
 * period, read four ways, not four independent inputs that happen to be passed
 * together.
 *
 * [window] rides along for a narrower reason than convenience. The rates are
 * measured over a window and the rows were read over a window, and those have
 * to be the same one. They were computed twice — once by the ViewModel to issue
 * the reads, once per habit inside the mapper — from the same inputs, so they
 * could not actually differ. Carrying the one that was used makes that
 * structural rather than a coincidence two call sites have to keep up.
 *
 * [previousTagEffort] is the one read that is not over [window]: the same tag
 * query over the period before it, which is all the focus sentence needs.
 */
internal data class PeriodReads(
    val window: ClosedRange<LocalDate>,
    val habits: List<HabitState>,
    val completions: Map<HabitId, Set<LocalDate>>,
    val tagEffort: List<TagEffort>,
    val previousTagEffort: List<TagEffort> = emptyList(),
)

/** The stepper's caption for [window], which is a period of this kind. */
private fun Period.labelOf(window: ClosedRange<LocalDate>): PeriodLabelUi = when (this) {
    Period.MONTH -> PeriodLabelUi.Month(monthName(window.start.month), window.start.year)
    Period.QUARTER -> PeriodLabelUi.Quarter(window.start.get(IsoFields.QUARTER_OF_YEAR), window.start.year)
    Period.YEAR -> PeriodLabelUi.Year(window.start.year)
}

/**
 * Active days per month, oldest first, for the months of the window that have
 * begun.
 *
 * **A month that has not started is not a point** — not a zero, not a gap. The
 * history grid's rule for future days (docs/ux/insights.md §8.3), in months: a
 * year drawn with four zeros at its end would read as a year already lost. The
 * current month is a real point over the days it has had so far, for the same
 * reason the rate card draws a real number for it (§8.7).
 */
private fun trendOf(window: ClosedRange<LocalDate>, today: LocalDate, completions: Map<HabitId, Set<LocalDate>>): List<TrendPointUi> {
    val activeDates = completions.values.flatten().toSet()
    return generateSequence(YearMonth.from(window.start)) { it.plusMonths(1) }
        .takeWhile { it <= YearMonth.from(window.endInclusive) && it.atDay(1) <= today }
        .map { month ->
            val active = activeDates.count { YearMonth.from(it) == month && !it.isAfter(today) }
            val elapsed = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
            TrendPointUi(monthName = monthName(month.month), activeDays = active, fill = active.toFloat() / elapsed)
        }
        .toList()
}

/**
 * The sentence, or none.
 *
 * "Focus" is the tag with the largest total, and only a **tagged** one: untagged
 * is what is left over, not a thing the user chose to work on. Ties break the
 * way the bars sort — largest first, then by name — so the sentence can never
 * name a tag the list draws second. Nothing is said when either period had no
 * tagged completion: a habit tagged for the first time this quarter did not
 * shift the focus from anywhere.
 */
private fun focusShift(previous: List<TagEffort>, current: List<TagEffort>): FocusShiftUi? {
    val before = previous.topTag()
    val now = current.topTag()
    return when {
        before == null || now == null -> null
        before == now -> FocusShiftUi.Held(now)
        else -> FocusShiftUi.Shifted(from = before, to = now)
    }
}

private fun List<TagEffort>.topTag(): String? = filter { it.tag != null && it.completions > 0 }
    .sortedWith(compareBy({ -it.completions }, { it.tag }))
    .firstOrNull()
    ?.tag

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
private fun List<HabitState>.toRates(
    window: ClosedRange<LocalDate>,
    context: ReadContext,
    completions: Map<HabitId, Set<LocalDate>>,
): List<HabitRateUi> = filterNot { it.archived }.map { habit ->
    val dates = completions[habit.id].orEmpty()
    val measured = habit.measuredWindow(window)
    HabitRateUi(
        name = habit.name,
        schedule = habit.schedule.toLabelUi(),
        percent = measured?.let { habit.percentOver(it, context, dates) },
        best = measured?.let { BestRun.within(dates, habit.schedule, it, context.today, context.weekStart).takeIf { run -> run > 0 } },
    )
}

/**
 * The window clipped to the habit's start, or null when the habit is younger
 * than the whole period.
 *
 * The clip is what the projected creation date bought: without it a habit made
 * halfway through a quarter reads as having missed the first half, which is a
 * number that is arithmetically right and accuses the user of days that were
 * never offered. The best run is measured over the same clipped window, so the
 * two figures on a row describe the same span.
 */
private fun HabitState.measuredWindow(window: ClosedRange<LocalDate>): ClosedRange<LocalDate>? {
    val born = createdOn
    return when {
        born == null -> window
        born > window.endInclusive -> null
        else -> maxOf(window.start, born)..window.endInclusive
    }
}

private fun HabitState.percentOver(window: ClosedRange<LocalDate>, context: ReadContext, dates: Set<LocalDate>): Int? {
    val rate = Rates.completionRate(dates, schedule, window, context.today, context.weekStart)
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
