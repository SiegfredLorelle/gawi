package com.gawi.feature.settings

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.gawi.core.data.settings.ThemeMode
import com.gawi.core.ui.component.GawiIconButton
import com.gawi.core.ui.component.GawiIcons
import com.gawi.core.ui.component.Notice
import com.gawi.core.ui.theme.GawiSpacing

/**
 * The settings screen, stateless as to what is stored.
 *
 * The stored values come in and the callbacks go out; the only thing this
 * composable remembers is which dialog is open, which is view state and not a
 * setting. That is deliberate: a half-picked time must not survive a dismiss,
 * and a committed one must come back from the store rather than from here, so
 * the screen can never show a value the file does not hold.
 *
 * [DeviceFacts] are parameters rather than things read off the platform in here,
 * so a test can render both clocks — and both notification states — without a
 * device to set either on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    actions: SettingsActions,
    snackbarHostState: SnackbarHostState,
    device: DeviceFacts,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { GawiIconButton(GawiIcons.ArrowLeft, R.string.settings_back, onClick = actions.onBack) },
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
                device = device,
                modifier = Modifier.fillMaxSize().padding(insets),
            )
        }
    }
}

/**
 * The rows, in their four groups, and whichever dialog one of them has opened.
 *
 * Scrollable because the rows with their explanations already outrun a short
 * screen at a large font scale, and there is nothing here to virtualise.
 */
@Composable
private fun SettingsList(state: SettingsUiState.Settings, actions: SettingsActions, device: DeviceFacts, modifier: Modifier = Modifier) {
    var openDialog by rememberSaveable { mutableStateOf(SettingsDialog.None) }
    val is24Hour = device.is24Hour

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
        if (!device.notificationsAllowed) ReminderBlocked(actions.onEnableNotifications)
        AppearanceSection(state.theme) { openDialog = SettingsDialog.Theme }
        DataSection(state.dataTask, state.exportRecency, actions)
        AboutSection(state.version, actions.onOpenLicences)
    }

    SettingsDialogs(
        openDialog = openDialog,
        state = state,
        is24Hour = is24Hour,
        actions = actions,
        onClose = { openDialog = SettingsDialog.None },
    )
}

/**
 * Whichever dialog a row has opened, and nothing if none has.
 *
 * Split out of [SettingsList] rather than nested in it, because the two do
 * different jobs: that one lays out rows, this one is a `when` over view state.
 * Together they are past detekt's `LongMethod` — the sort of limit worth taking
 * the hint from rather than raising, since the seam it points at is a real
 * one.
 *
 * [onClose] is called before the action, in every branch, so a confirm cannot
 * leave the dialog up if the write throws.
 */
@Composable
private fun SettingsDialogs(
    openDialog: SettingsDialog,
    state: SettingsUiState.Settings,
    is24Hour: Boolean,
    actions: SettingsActions,
    onClose: () -> Unit,
) {
    when (openDialog) {
        SettingsDialog.None -> Unit

        SettingsDialog.DayCutoff -> TimeDialog(
            titleRes = R.string.settings_day_cutoff_label,
            initial = state.dayCutoff,
            is24Hour = is24Hour,
            onConfirm = { picked ->
                onClose()
                actions.onDayCutoffChange(picked)
            },
            onDismiss = onClose,
        )

        SettingsDialog.Reminder -> TimeDialog(
            titleRes = R.string.settings_reminder_label,
            initial = state.reminderTime,
            is24Hour = is24Hour,
            onConfirm = { picked ->
                onClose()
                actions.onReminderTimeChange(picked)
            },
            onDismiss = onClose,
        )

        SettingsDialog.WeekStart -> WeekStartDialog(
            selected = state.weekStart,
            onConfirm = { picked ->
                onClose()
                actions.onWeekStartChange(picked)
            },
            onDismiss = onClose,
        )

        SettingsDialog.Theme -> ThemeDialog(
            selected = state.theme,
            onConfirm = { picked ->
                onClose()
                actions.onThemeChange(picked)
            },
            onDismiss = onClose,
        )
    }
}

/**
 * The theme row, under a header of its own.
 *
 * Its own section rather than a fourth row above, because the three rows above
 * it are one kind of thing and this is not: all three change how the app counts
 * a day or a week, which is the whole argument of docs/ux/settings.md §2, and a
 * setting that changes only paint sitting among them would blunt it. The header
 * is what says so, in the same shape the Data section already uses for rows
 * that are not settings at all.
 *
 * The help line names the widget. It is the one thing about this setting a user
 * can be wrong about — the widget is drawn by the launcher and cannot follow an
 * in-app choice (architecture §2) — and a home screen showing the other scheme
 * would otherwise read as a bug.
 */
@Composable
private fun AppearanceSection(theme: ThemeMode, onOpen: () -> Unit) {
    SectionHeader(stringResource(R.string.settings_appearance_header))
    SettingRow(
        label = stringResource(R.string.settings_theme_label),
        value = stringResource(labelFor(theme)),
        help = stringResource(R.string.settings_theme_help),
        onClick = onOpen,
    )
}

/**
 * One row: what it is, what it is set to if that is a thing it has, and what it
 * does.
 *
 * The whole row is the target rather than the value alone, so a setting is not
 * harder to reach for being set to a short string. The explanation is part of
 * the row rather than a help icon, because the settings above change how the app
 * counts days and a reader who has to go looking for that will not. The theme
 * row uses the same shape to say the opposite — what it does *not* reach — for
 * the reason [AppearanceSection] gives.
 *
 * **[value] is nullable, which is what lets one composable serve both kinds of
 * row.** docs/ux/settings.md §6 argues the Data section's rows must not be this
 * one with an empty value, because the middle line is `titleMedium` in the
 * primary colour and means "this is what it is set to" — the rows above teach
 * that before a reader reaches the fourth. That is why null draws no middle line
 * at all rather than an empty one: the import row has nothing to put there and
 * never will. The export row does have a stored value, so a second composable
 * would mean maintaining the same `Column` twice to express one difference.
 *
 * [activity] carries both "can this be tapped" and "is this the row that is
 * busy". See [RowActivity] for why they are one parameter and not two.
 */
@Composable
internal fun SettingRow(label: String, value: String?, help: String, activity: RowActivity = RowActivity.Live, onClick: () -> Unit) {
    val live = activity == RowActivity.Live
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = live, onClickLabel = label, onClick = onClick)
            .padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            // A colour rather than an alpha: no magic number to name, and dark
            // mode needs no second thought.
            color = if (live) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Marks the row whose help line has been replaced by a status, so a
            // screen reader is told rather than left tapping something silent.
            modifier = if (activity == RowActivity.Running) {
                Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            } else {
                Modifier
            },
        )
    }
}

/**
 * The reminder row's own bad news: the time is set, and nothing will arrive.
 *
 * **Its own target under the row rather than a state on the row**, and that is the
 * decision worth recording. `SettingRow`'s rule is that the whole row is the
 * target (see its KDoc), and the row's tap already means "change the time" —
 * which stays worth doing while notifications are off, because the same setting
 * decides when Momo starts looking worried (docs/ux/today-view.md §4). Folding
 * two different actions into one row would have made a tap ambiguous, and making
 * the row *do* this instead would have taken the time picker away over a
 * permission.
 *
 * `error` rather than a plain caption, because this is the one thing on this
 * screen that says a feature the user has configured is not running.
 *
 * The copy does not say "grant a permission". Below API 33 there is no permission
 * to grant — the user turned notifications off in system settings and that is
 * where they are turned back on — so naming the mechanism would be wrong on some
 * versions and jargon on all of them. It says what is not happening and offers to
 * fix it, which is true everywhere.
 */
@Composable
private fun ReminderBlocked(onEnable: () -> Unit) {
    val label = stringResource(R.string.settings_reminder_enable)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onEnable)
            .padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        Text(
            text = stringResource(R.string.settings_reminder_blocked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            // Announced when it appears: a user who has just come back from
            // system settings needs to hear that it did not take effect, and the
            // row above it is unchanged.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
