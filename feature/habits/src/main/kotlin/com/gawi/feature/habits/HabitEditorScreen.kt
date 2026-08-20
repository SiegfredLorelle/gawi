package com.gawi.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.model.Schedule
import com.gawi.core.ui.component.Notice
import com.gawi.core.ui.theme.GawiSpacing
import com.gawi.core.ui.theme.HabitPalette
import com.gawi.core.ui.theme.glyphColorOn
import com.gawi.core.ui.theme.parseHabitColor

/**
 * The habit editor, stateless.
 *
 * One screen for a new habit and for an existing one, because `updateHabit` is
 * a whole-record write rather than a patch — an edit submits every field, which
 * is the same form a create submits. [HabitEditorUiState.Form.editing] changes
 * the title and nothing else.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitEditorScreen(
    state: HabitEditorUiState,
    actions: HabitEditorActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val form = state as? HabitEditorUiState.Form
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (form?.editing == true) R.string.habits_edit_title else R.string.habits_new_title,
                        ),
                    )
                },
                navigationIcon = {
                    val label = stringResource(R.string.habits_cancel)
                    IconButton(onClick = actions.onCancel, modifier = Modifier.semantics { contentDescription = label }) {
                        Text(text = "✕", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    // Disabled rather than hidden, so the reason a save is not
                    // available is visible next to the field that causes it.
                    TextButton(onClick = actions.onSave, enabled = form?.canSave == true) {
                        Text(stringResource(R.string.habits_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        when (state) {
            HabitEditorUiState.Loading -> Box(Modifier.fillMaxSize().padding(insets))

            HabitEditorUiState.Unavailable -> Notice(
                title = stringResource(R.string.habits_editor_unavailable_title),
                body = stringResource(R.string.habits_editor_unavailable_body),
                modifier = Modifier.fillMaxSize().padding(insets),
            )

            is HabitEditorUiState.Form -> EditorForm(
                form = state,
                onEdit = actions.onEdit,
                modifier = Modifier.fillMaxSize().padding(insets),
            )
        }
    }
}

@Composable
private fun EditorForm(form: HabitEditorUiState.Form, onEdit: (HabitEditorUiState.Form) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(GawiSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Row),
    ) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { onEdit(form.copy(name = it)) },
            label = { Text(stringResource(R.string.habits_name_label)) },
            isError = !form.canSave,
            // The slot is always present so the field does not change height the
            // first time the name is cleared. It states the same rule canSave
            // does, next to the field that decides it.
            supportingText = { if (!form.canSave) Text(stringResource(R.string.habits_name_error)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        LabelledSection(R.string.habits_icon_label) {
            IconPicker(form, onEdit)
        }

        LabelledSection(R.string.habits_color_label) {
            ColorPicker(form, onEdit)
        }

        LabelledSection(R.string.habits_schedule_label) {
            SchedulePicker(form, onEdit)
        }

        OutlinedTextField(
            value = form.tag,
            onValueChange = { onEdit(form.copy(tag = it)) },
            label = { Text(stringResource(R.string.habits_tag_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LabelledSection(labelRes: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/**
 * The icon, chosen from a fixed list.
 *
 * Emoji rather than drawables, because `HabitMetadata.icon` is a String that
 * has to survive an export and an import — a drawable id would not.
 */
@Composable
private fun IconPicker(form: HabitEditorUiState.Form, onEdit: (HabitEditorUiState.Form) -> Unit) {
    // Wrapped rather than scrolled: a horizontal scroller hides options, and
    // there are few enough that a fixed grid of rows fits any phone.
    FlowingRow(HabitPalette.Icons) { icon ->
        val selected = icon == form.icon
        Box(
            modifier = Modifier
                .size(GawiSpacing.IconBox)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .selectableBorder(selected)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onEdit(form.copy(icon = icon)) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** The colour, chosen from a fixed palette, which is what keeps it valid. */
@Composable
private fun ColorPicker(form: HabitEditorUiState.Form, onEdit: (HabitEditorUiState.Form) -> Unit) {
    FlowingRow(HabitPalette.Colors) { hex ->
        val selected = hex == form.color
        // Never null for a palette entry — HabitColorTest pins that — so the
        // fallback here is for a colour that arrived from somewhere else.
        val tint = parseHabitColor(hex) ?: MaterialTheme.colorScheme.secondaryContainer
        Box(
            modifier = Modifier
                .size(GawiSpacing.IconBox)
                .clip(CircleShape)
                .background(tint)
                .selectableBorder(selected)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onEdit(form.copy(color = hex)) },
                )
                .semantics { contentDescription = hex },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleSmall,
                    color = glyphColorOn(tint, MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}

/**
 * Daily or weekly, and for weekly a target the stepper cannot take out of range.
 *
 * The clamp is the point. `Schedule.Weekly` validates with `require`, so a
 * target outside 1..7 throws rather than being rejected — an unbounded stepper
 * would crash on save rather than showing an error.
 */
@Composable
private fun SchedulePicker(form: HabitEditorUiState.Form, onEdit: (HabitEditorUiState.Form) -> Unit) {
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
                onClick = { onEdit(form.copy(schedule = ScheduleUi.Weekly(DEFAULT_WEEKLY_TARGET))) },
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
        StepperButton("−", R.string.habits_target_fewer, enabled = target > MIN_WEEKLY_TARGET) {
            onChange(target - 1)
        }
        Text(
            text = stringResource(R.string.habits_weekly_target, target),
            style = MaterialTheme.typography.bodyLarge,
        )
        StepperButton("+", R.string.habits_target_more, enabled = target < Schedule.DAYS_PER_WEEK) {
            onChange(target + 1)
        }
    }
}

@Composable
private fun StepperButton(glyph: String, labelRes: Int, enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(labelRes)
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        Text(text = glyph, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * A fixed-width wrap, because `FlowRow` is still experimental in material3 and
 * this needs one behaviour from it.
 */
@Composable
private fun <T> FlowingRow(items: List<T>, content: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
        items.chunked(SWATCHES_PER_ROW).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
                chunk.forEach { content(it) }
            }
        }
    }
}

/** The selected ring. A border rather than a scale, so nothing reflows on tap. */
@Composable
private fun Modifier.selectableBorder(selected: Boolean): Modifier =
    if (selected) border(SELECTION_RING, MaterialTheme.colorScheme.onSurface, CircleShape) else this

private val SELECTION_RING = 3.dp
private const val SWATCHES_PER_ROW = 6
private const val MIN_WEEKLY_TARGET = 1
private const val DEFAULT_WEEKLY_TARGET = 3
