package com.gawi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * The About section: the version, and the row that leads to the licences.
 *
 * A fourth header rather than two more rows, for the reason [AppearanceSection]
 * gives: the rows above count days or move files, and neither of these does
 * anything at all (docs/ux/settings.md §9). Last on the screen, so a reader
 * after a preference never passes them.
 *
 * The version goes in the *help* line. [SettingRow]'s middle line is
 * `titleMedium` in the primary colour and means "this is what it is set to";
 * a version is a record of what the app is, not a choice, so it takes the
 * small grey line and the row is [RowActivity.Blocked] — nothing a tap could
 * do, and the greyed label says so before anyone tries.
 */
@Composable
internal fun AboutSection(version: String, onOpenLicences: () -> Unit) {
    SectionHeader(stringResource(R.string.settings_about_header))
    SettingRow(
        label = stringResource(R.string.settings_version_label),
        value = null,
        help = version,
        activity = RowActivity.Blocked,
        onClick = {},
    )
    SettingRow(
        label = stringResource(R.string.settings_licences_label),
        value = null,
        help = stringResource(R.string.settings_licences_help),
        onClick = onOpenLicences,
    )
}
