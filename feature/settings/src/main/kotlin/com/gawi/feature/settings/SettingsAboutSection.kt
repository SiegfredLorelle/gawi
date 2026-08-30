package com.gawi.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gawi.core.ui.theme.GawiSpacing

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
 * small grey line. And the row is [StaticRow], not a [SettingRow] that cannot be
 * tapped: a disabled control is announced as one — "disabled" — which promises
 * an action that does not exist. This is text, and reads as text.
 */
@Composable
internal fun AboutSection(version: String, onOpenLicences: () -> Unit) {
    SectionHeader(stringResource(R.string.settings_about_header))
    StaticRow(label = stringResource(R.string.settings_version_label), help = version)
    SettingRow(
        label = stringResource(R.string.settings_licences_label),
        value = null,
        help = stringResource(R.string.settings_licences_help),
        onClick = onOpenLicences,
    )
}

/**
 * A row that says something and does nothing: [SettingRow]'s label and help
 * lines at the same padding, with no click target and no value line.
 */
@Composable
private fun StaticRow(label: String, help: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(text = help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
