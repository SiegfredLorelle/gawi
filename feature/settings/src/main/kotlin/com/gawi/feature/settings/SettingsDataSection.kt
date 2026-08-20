package com.gawi.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.gawi.core.ui.theme.GawiSpacing

// Export and import: the two rows on this screen that are not settings.
//
// Their own file for the reason SettingsPickers.kt has one — detekt counts
// functions per file as well as per class — and because the split falls
// somewhere honest: everything here is about getting the log off the phone and
// back, and nothing here reads or writes a preference.

/**
 * The Data section.
 *
 * Named where the three settings above it are not, following the habit list's
 * archived section: the obvious group goes unlabelled and only the one that
 * needs saying gets a heading. Labelling both would mean inventing a word for
 * "the three settings", and every candidate says less than the rows do.
 *
 * No divider. The heading carries the separation on its own over there, a rule
 * and a heading are two devices doing one job, and there is no
 * `HorizontalDivider` anywhere in this app to be consistent with.
 */
@Composable
internal fun DataSection(dataTask: DataTask, actions: SettingsActions) {
    // Both rows go dead while either runs. Exporting midway through an import
    // reads a log that is half-merged; importing during an export writes a file
    // that is half-written. Neither is worth allowing to save a tap.
    val idle = dataTask == DataTask.Idle

    SectionHeader(stringResource(R.string.settings_data_header))
    ActionRow(
        label = stringResource(R.string.settings_export_label),
        help = statusOr(dataTask, DataTask.Exporting, R.string.settings_export_running, R.string.settings_export_help),
        running = dataTask == DataTask.Exporting,
        enabled = idle,
        onClick = actions.onExport,
    )
    ActionRow(
        label = stringResource(R.string.settings_import_label),
        help = statusOr(dataTask, DataTask.Importing, R.string.settings_import_running, R.string.settings_import_help),
        running = dataTask == DataTask.Importing,
        enabled = idle,
        onClick = actions.onImport,
    )
}

@Composable
private fun statusOr(dataTask: DataTask, running: DataTask, runningRes: Int, helpRes: Int): String =
    stringResource(if (dataTask == running) runningRes else helpRes)

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
    )
}

/**
 * One thing you can do, and what it does.
 *
 * `SettingRow`'s sibling, and deliberately not `SettingRow` with an empty
 * value. That middle line is `titleMedium` in the primary colour and it means
 * "this is what the setting is set to" — three rows have taught the reader that
 * before they reach this one. "Export a copy" is not a value, and a row that
 * put an action verb where the others put a state would borrow the wrong
 * emphasis.
 *
 * When the 30-day nudge lands, the export row gains a real stored value and
 * *becomes* a `SettingRow`; this does not grow a `value` parameter. The import
 * row will never have one.
 *
 * Disabled rather than hidden while the work runs, the way the habit editor's
 * Save is: a control that vanishes takes its explanation with it. [running]
 * marks the row whose help line has been replaced by a status, so a screen
 * reader is told rather than left tapping something silent.
 */
@Composable
private fun ActionRow(label: String, help: String, running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            // A colour rather than an alpha: no magic number to name, and dark
            // mode needs no second thought.
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (running) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
        )
    }
}
