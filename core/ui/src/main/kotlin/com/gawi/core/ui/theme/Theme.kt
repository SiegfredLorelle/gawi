package com.gawi.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * The app's Compose theme: Material defaults, following the system dark
 * setting, paired with the values-night window theme that :app's manifest
 * points at. That XML style only paints the window before `setContent`, so it
 * stays with the manifest that references it; everything drawn is this.
 *
 * Still deliberately stock. There is no bespoke `ColorScheme`, no typography
 * and no dynamic colour, because Momo's palette is PRD OQ-4 and undesigned —
 * inventing one here would mean choosing it in the module least able to
 * explain the choice. A habit's own colour is per-row and comes from the event
 * log, not from here.
 */
@Composable
fun GawiTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}
