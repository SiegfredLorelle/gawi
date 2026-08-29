package com.gawi.feature.insights

/**
 * What the Insights screen can report.
 *
 * [onEarlier] and [onLater] step the period back through the calendar and
 * forward again — the retrospective's one control (docs/ux/insights.md §9).
 * The screen never says *which* period that lands on; the state holder owns
 * the offset, as the history grid's does.
 */
internal data class InsightsActions(
    val onPeriod: (Period) -> Unit,
    val onBreakdown: (Breakdown) -> Unit,
    val onEarlier: () -> Unit,
    val onLater: () -> Unit,
    val onBack: () -> Unit,
)
