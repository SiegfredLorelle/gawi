package com.gawi.feature.insights

/**
 * What the Insights screen reports.
 *
 * Neither the period nor the breakdown is navigation — both are the screen's own
 * state, so changing either must not push a back-stack entry and Back must leave
 * the screen rather than unwind a sequence of filter changes.
 */
internal data class InsightsActions(val onPeriod: (Period) -> Unit, val onBreakdown: (Breakdown) -> Unit, val onBack: () -> Unit)
