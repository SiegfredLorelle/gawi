package com.gawi.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.gawi.core.ui.component.Notice
import com.gawi.core.ui.theme.GawiSpacing

/**
 * The settings screen, stateless as to what is stored.
 *
 * The three values come in and the four callbacks go out; the only thing this
 * composable remembers is which dialog is open, which is view state and not a
 * setting. That is deliberate: a half-picked time must not survive a dismiss,
 * and a committed one must come back from the store rather than from here, so
 * the screen can never show a value the file does not hold.
 *
 * [is24Hour] is a parameter rather than something read off the platform in
 * here, so a test can render both clocks without a device to set the flag on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    snackbarHostState: SnackbarHostState,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { GlyphButton("←", R.string.settings_back, actions.onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        // targetSdk 37 draws edge to edge with no opt-out, so every branch has
        // to honour the insets or its first row sits under the status bar.
        when (state) {
            // Blank rather than a spinner: the first emission is one read of a
            // preferences file.
            SettingsUiState.Loading -> Box(Modifier.fillMaxSize().padding(insets))

            SettingsUiState.Unavailable -> Notice(
                title = stringResource(R.string.settings_unavailable_title),
                body = stringResource(R.string.settings_unavailable_body),
                modifier = Modifier.fillMaxSize().padding(insets),
            )

            is SettingsUiState.Settings -> SettingsList(
                state = state,
                actions = actions,
                is24Hour = is24Hour,
                modifier = Modifier.fillMaxSize().padding(insets),
            )
        }
    }
}

/**
 * The three rows, and whichever dialog one of them has opened.
 *
 * Scrollable because three rows with their explanations already outrun a short
 * screen at a large font scale, and there is nothing here to virtualise.
 */
@Composable
private fun SettingsList(state: SettingsUiState.Settings, actions: SettingsActions, is24Hour: Boolean, modifier: Modifier = Modifier) {
    var openDialog by rememberSaveable { mutableStateOf(SettingsDialog.None) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        SettingRow(
            label = stringResource(R.string.settings_day_cutoff_label),
            value = formatTime(state.dayCutoff, is24Hour),
            help = stringResource(R.string.settings_day_cutoff_help),
            onClick = { openDialog = SettingsDialog.DayCutoff },
        )
        SettingRow(
            label = stringResource(R.string.settings_week_start_label),
            value = stringResource(labelFor(state.weekStart)),
            help = stringResource(R.string.settings_week_start_help),
            onClick = { openDialog = SettingsDialog.WeekStart },
        )
        SettingRow(
            label = stringResource(R.string.settings_reminder_label),
            value = formatTime(state.reminderTime, is24Hour),
            help = stringResource(R.string.settings_reminder_help),
            onClick = { openDialog = SettingsDialog.Reminder },
        )
        DataSection(state.dataTask, actions)
    }

    when (openDialog) {
        SettingsDialog.None -> Unit

        SettingsDialog.DayCutoff -> TimeDialog(
            titleRes = R.string.settings_day_cutoff_label,
            initial = state.dayCutoff,
            is24Hour = is24Hour,
            onConfirm = { picked ->
                openDialog = SettingsDialog.None
                actions.onDayCutoffChange(picked)
            },
            onDismiss = { openDialog = SettingsDialog.None },
        )

        SettingsDialog.Reminder -> TimeDialog(
            titleRes = R.string.settings_reminder_label,
            initial = state.reminderTime,
            is24Hour = is24Hour,
            onConfirm = { picked ->
                openDialog = SettingsDialog.None
                actions.onReminderTimeChange(picked)
            },
            onDismiss = { openDialog = SettingsDialog.None },
        )

        SettingsDialog.WeekStart -> WeekStartDialog(
            selected = state.weekStart,
            onConfirm = { picked ->
                openDialog = SettingsDialog.None
                actions.onWeekStartChange(picked)
            },
            onDismiss = { openDialog = SettingsDialog.None },
        )
    }
}

/**
 * One setting: what it is, what it is set to, and what it does.
 *
 * The whole row is the target rather than the value alone, so a setting is not
 * harder to reach for being set to a short string. The explanation is part of
 * the row rather than a help icon, because all three of these change how the
 * app counts days and a reader who has to go looking for that will not.
 */
@Composable
private fun SettingRow(label: String, value: String, help: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An icon button with no icon font behind it, named for assistive technology. */
@Composable
private fun GlyphButton(glyph: String, @StringRes labelRes: Int, onClick: () -> Unit) {
    val label = stringResource(labelRes)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(text = glyph, style = MaterialTheme.typography.titleLarge)
    }
}
