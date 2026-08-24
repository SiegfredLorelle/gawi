package com.gawi.feature.insights

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gawi.core.domain.model.Schedule

/**
 * A schedule as a line of copy — "Every day", or "3× a week".
 *
 * Two fields rather than one resource id, because only one of the two strings
 * takes an argument and a caller holding a bare id cannot know which. That is
 * the same problem `:feature:habits` solves with its own `ScheduleUi`, solved
 * differently here for a reason: that type is `internal` to that module and
 * resource ids do not cross a module boundary either way, so sharing it would
 * mean promoting a presentation type to `:core:ui` for two call sites in one
 * feature. If a third feature needs this, that promotion is the answer.
 *
 * Both surfaces in this module draw it. docs/ux/insights.md §4 is why: a daily
 * habit's completion rate is completions over days and a weekly habit's is
 * completions over `timesPerWeek × weeks`, so a percentage without this beside
 * it invites being compared with a percentage it is not comparable to.
 */
internal data class ScheduleLabelUi(@StringRes val text: Int, val timesPerWeek: Int?)

internal fun Schedule.toLabelUi(): ScheduleLabelUi = when (this) {
    is Schedule.Daily -> ScheduleLabelUi(R.string.insights_schedule_daily, timesPerWeek = null)
    is Schedule.Weekly -> ScheduleLabelUi(R.string.insights_schedule_weekly, timesPerWeek = timesPerWeek)
}

/** Resolves [ScheduleLabelUi], passing the target only where there is one. */
@Composable
internal fun scheduleText(label: ScheduleLabelUi): String =
    label.timesPerWeek?.let { stringResource(label.text, it) } ?: stringResource(label.text)
