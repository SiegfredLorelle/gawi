package com.gawi.feature.habits

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gawi.core.ui.component.GawiIconButton
import com.gawi.core.ui.component.GawiIcons
import com.gawi.core.ui.component.Notice
import com.gawi.core.ui.theme.GawiSpacing

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
                title = { Text(stringResource(titleFor(state))) },
                navigationIcon = {
                    GawiIconButton(GawiIcons.Close, R.string.habits_cancel, onClick = actions.onCancel)
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

/**
 * Which of the two things this screen currently is.
 *
 * Exhaustive over the state rather than reading `form?.editing`, which is false
 * when there is no form and so titled a failed load "New habit" — a screen
 * claiming to be a fresh create while showing an error. [HabitEditorUiState.Loading]
 * and [HabitEditorUiState.Unavailable] only arise when an id was supplied, since
 * creating opens straight onto the form, so both are an edit that has not
 * arrived.
 */
@StringRes
private fun titleFor(state: HabitEditorUiState): Int = when (state) {
    HabitEditorUiState.Loading, HabitEditorUiState.Unavailable -> R.string.habits_edit_title

    is HabitEditorUiState.Form ->
        if (state.editing) R.string.habits_edit_title else R.string.habits_new_title
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
            // Only an error when editing. A habit that had a name and no longer
            // does is wrong; an untouched create form is not wrong yet, and
            // greeting a first habit with a red field is not validation. The
            // disabled Save is what conveys the block in that case, which is all
            // docs/ux/habits.md §3 claims for it.
            isError = form.editing && !form.canSave,
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
