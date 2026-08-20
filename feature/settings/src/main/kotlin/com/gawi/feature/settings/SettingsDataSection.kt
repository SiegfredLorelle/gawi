package com.gawi.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
 *
 * The export row is a [SettingRow] with a real stored value — how long ago the
 * log was last written to a file — and the import row is the same composable
 * with none, because there is nothing an import leaves behind for a row to
 * report. That is the convergence docs/ux/settings.md §6 predicted, arriving one
 * step further along than it expected: one row composable rather than two.
 */
@Composable
internal fun DataSection(dataTask: DataTask, recency: ExportRecency, actions: SettingsActions) {
    val exporting = activityOf(dataTask, DataTask.Exporting)
    val importing = activityOf(dataTask, DataTask.Importing)

    SectionHeader(stringResource(R.string.settings_data_header))
    SettingRow(
        label = stringResource(R.string.settings_export_label),
        value = exportValue(recency),
        help = stringResource(exportHelp(exporting, recency)),
        activity = exporting,
        onClick = actions.onExport,
    )
    SettingRow(
        label = stringResource(R.string.settings_import_label),
        value = null,
        help = stringResource(importHelp(importing)),
        activity = importing,
        onClick = actions.onImport,
    )
}

/**
 * The export row's value line, or null when there is nothing worth saying.
 *
 * Only resolving here; which of the four cases applies was decided by
 * [recencyOf], where it can be tested without rendering anything. A `<plurals>`
 * rather than one string with a `%d` in it, because this sentence has exactly one
 * number in it and "1 days ago" is not a thing to ship — which is the case §6's
 * argument against quantity resources explicitly does not cover, that one being
 * about a sentence with two independent counts.
 */
@Composable
private fun exportValue(recency: ExportRecency): String? = when (recency) {
    ExportRecency.NothingYet -> null
    ExportRecency.Never -> stringResource(R.string.settings_export_never)
    ExportRecency.Today -> stringResource(R.string.settings_export_today)
    is ExportRecency.DaysAgo -> pluralStringResource(R.plurals.settings_export_days_ago, recency.days, recency.days)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
    )
}
