package com.gawi.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.gawi.core.domain.model.Schedule
import com.gawi.core.ui.component.GawiIconButton
import com.gawi.core.ui.component.GawiIcons
import com.gawi.core.ui.theme.GawiSpacing

// The editor's one chooser.
//
// A separate file from the screen because it is a fixed set of options where a
// free-text field would otherwise let an invalid value be typed. It kept an
// icon and a colour picker company until visual-identity §7.3 decided a habit
// keeps neither.

/**
 * Daily or weekly, and for weekly a target the stepper cannot take out of range.
 *
 * The clamp is the point. `Schedule.Weekly` validates with `require`, so a
 * target outside 1..7 throws rather than being rejected — an unbounded stepper
 * would crash on save rather than showing an error.
 */
@Composable
internal fun SchedulePicker(form: HabitEditorUiState.Form, onEdit: (HabitEditorUiState.Form) -> Unit) {
    val weekly = form.schedule as? ScheduleUi.Weekly
    Column(verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
            FilterChip(
                selected = weekly == null,
                onClick = { onEdit(form.copy(schedule = ScheduleUi.Daily)) },
                label = { Text(stringResource(R.string.habits_schedule_daily_option)) },
            )
            FilterChip(
                selected = weekly != null,
                // Keeps the target it already has. Writing the default
                // unconditionally lets a tap on the already-selected chip
                // silently knock a Weekly(6) habit back to 3.
                onClick = {
                    onEdit(form.copy(schedule = ScheduleUi.Weekly(weekly?.timesPerWeek ?: DEFAULT_WEEKLY_TARGET)))
                },
                label = { Text(stringResource(R.string.habits_schedule_weekly_option)) },
            )
        }
        if (weekly != null) {
            WeeklyTargetStepper(weekly.timesPerWeek) { onEdit(form.copy(schedule = ScheduleUi.Weekly(it))) }
        }
    }
}

@Composable
private fun WeeklyTargetStepper(target: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        GawiIconButton(GawiIcons.Minus, R.string.habits_target_fewer, enabled = target > MIN_WEEKLY_TARGET) {
            onChange(target - 1)
        }
        Text(
            text = stringResource(R.string.habits_weekly_target, target),
            style = MaterialTheme.typography.bodyLarge,
        )
        GawiIconButton(GawiIcons.Plus, R.string.habits_target_more, enabled = target < Schedule.DAYS_PER_WEEK) {
            onChange(target + 1)
        }
    }
}

private const val MIN_WEEKLY_TARGET = 1
private const val DEFAULT_WEEKLY_TARGET = 3
