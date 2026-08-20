package com.gawi.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gawi.core.ui.theme.GawiSpacing
import java.time.DayOfWeek
import java.time.LocalTime

// The two dialogs, split out of SettingsScreen.kt because detekt's
// TooManyFunctions applies per file as well as per class. Both hold the
// half-made choice and hand it back only on confirm, so dismissing one leaves
// the stored setting exactly as it was.

/**
 * Pick a time.
 *
 * The picker's own state is the mid-edit value: it exists from the moment the
 * dialog opens and dies with it, which is the whole reason nothing half-picked
 * can reach the store. [onConfirm] is the only way a value leaves here.
 *
 * There is no invalid time to guard against — every point on the clock is a
 * legal cutoff and a legal reminder — which is what makes this simpler than the
 * habit editor's weekly stepper, where the domain type throws on an
 * out-of-range value and a separate UI type had to exist to hold one.
 *
 * Material's own [TimePickerDialog] rather than an [AlertDialog] wrapping a
 * [TimePicker]. That is not a cosmetic preference: `AlertDialog` puts its text
 * slot in a `weight(1f, fill = false)` box with no scrolling, so a ~400dp clock
 * dial is *clipped* rather than scrolled on a short viewport — and since the
 * activity is not locked to portrait and the open dialog survives rotation, a
 * user could confirm a time whose minute ring they could not reach. This dialog
 * sizes itself, and its display-mode toggle puts the text entry one tap away
 * when the dial still does not fit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeDialog(
    @StringRes titleRes: Int,
    initial: LocalTime,
    is24Hour: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = is24Hour,
    )
    // A Boolean, not the TimePickerDisplayMode itself. That type is a value
    // class over an Int, so rememberSaveable boxes it into something the default
    // SaveableStateRegistry cannot put in a Bundle — and it throws when the
    // dialog is composed, not when it is restored. Saving the flag and deriving
    // the mode keeps the state bundleable with no custom Saver.
    var showTextInput by rememberSaveable { mutableStateOf(false) }
    val mode = if (showTextInput) TimePickerDisplayMode.Input else TimePickerDisplayMode.Picker

    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(picker.hour, picker.minute)) }) {
                Text(stringResource(R.string.settings_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
        title = { Text(stringResource(titleRes)) },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = { showTextInput = !showTextInput },
                displayMode = mode,
            )
        },
    ) {
        if (mode == TimePickerDisplayMode.Picker) TimePicker(state = picker) else TimeInput(state = picker)
    }
}

/**
 * Pick the day a week starts on.
 *
 * A list of all seven rather than a Monday/Sunday pair. The PRD calls this
 * "week start day (default Monday)" without narrowing it, some calendars start
 * on Saturday, and seven radio buttons cost nothing over two.
 */
@Composable
internal fun WeekStartDialog(selected: DayOfWeek, onConfirm: (DayOfWeek) -> Unit, onDismiss: () -> Unit) {
    // Held rather than committed on tap, so the dialog reads like the time one
    // next to it: choose, then confirm, and Cancel always means nothing changed.
    var choice by rememberSaveable { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_week_start_label)) },
        text = {
            Column(
                // selectableGroup, so assistive technology announces these as one
                // set of seven and reads a position within it, rather than as
                // seven unrelated selectable things that happen to be adjacent.
                modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
            ) {
                WEEK_START_OPTIONS.forEach { day ->
                    WeekStartOption(day = day, selected = day == choice, onSelect = { choice = day })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice) }) { Text(stringResource(R.string.settings_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

/**
 * One day in the week-start list.
 *
 * `selectable` on the whole row rather than an `onClick` on the radio button,
 * so the name is part of the target and assistive technology reads the two as
 * one control. The explicit minimum height is not decoration: `selectable` does
 * not apply `minimumInteractiveComponentSize` the way a Material component
 * does, so without it the row is only as tall as its text.
 */
@Composable
private fun WeekStartOption(day: DayOfWeek, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TOUCH_TARGET)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = GawiSpacing.Line),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        // null: the row above already carries the selection semantics, and a
        // clickable button inside a selectable row would be two targets saying
        // the same thing.
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(labelFor(day)))
    }
}

/** Material's minimum touch target, which `selectable` does not apply on its own. */
private val TOUCH_TARGET = 48.dp
