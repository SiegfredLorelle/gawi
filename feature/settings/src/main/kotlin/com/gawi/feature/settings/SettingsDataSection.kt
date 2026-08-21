package com.gawi.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.gawi.core.ui.theme.GawiSpacing

// The Data section: the three rows on this screen that are not settings.
//
// Their own file for the reason SettingsPickers.kt has one — detekt counts
// functions per file as well as per class — and because the split falls
// somewhere honest: everything here is about getting data off the phone or back
// on it, and nothing here reads or writes a preference.
//
// Two of the three rows are the backup; the third is a spreadsheet and is not.
// See DataSection's own KDoc, which is where that distinction is argued.

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
 * log was last written to a file — and the other two are the same composable
 * with none, because there is nothing either of them leaves behind for a row to
 * report. That is the convergence docs/ux/settings.md §6 predicted, arriving one
 * step further along than it expected: one row composable rather than two.
 *
 * **Three rows under one heading, and the third one contradicts what the heading
 * argues.** §6's case for naming this section is that its rows are the only
 * recovery path there is. The CSV is not one, so the distinction cannot live in
 * the layout and has to live in the copy: `settings_export_csv_help` says in as
 * many words that the file is a spreadsheet, holds no habits or settings, and
 * cannot be imported back. Splitting the section instead would have meant a
 * one-row heading and a word to invent for it, which says less than the sentence
 * does.
 */
@Composable
internal fun DataSection(dataTask: DataTask, recency: ExportRecency, actions: SettingsActions) {
    val exporting = activityOf(dataTask, DataTask.Exporting)
    val exportingCsv = activityOf(dataTask, DataTask.ExportingCsv)
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
    SettingRow(
        label = stringResource(R.string.settings_export_csv_label),
        // No value line, and for the same reason the import row has none rather
        // than for a different one: there is nothing stored about this row to
        // report. It is also not stamped — a CSV is not a backup, so the 30-day
        // nudge above must not be settled by one.
        value = null,
        help = stringResource(csvHelp(exportingCsv)),
        activity = exportingCsv,
        onClick = actions.onExportCompletions,
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
