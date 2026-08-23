package com.gawi.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The app's Compose theme: the designed light and dark schemes from
 * docs/ux/visual-identity.md §7.2, following the system dark setting, paired
 * with the values-night window theme that :app's manifest points at. That XML
 * style only paints the window before `setContent`, so it stays with the
 * manifest that references it; everything drawn is this. The two have to agree
 * on a background colour or a cold launch flashes — :app's `colors.xml` is
 * where that duplication lives, and it says so.
 *
 * **Dynamic colour is absent, and that is a decision rather than an omission.**
 * §8 keeps Material You off permanently: a designed identity is the point of
 * the whole brief, and dynamic colour would hand it back to the wallpaper.
 *
 * No typography yet. §5 decides to bundle a variable font and defines the ten
 * roles the app actually draws, but the typeface is parked on an experiment —
 * whether a Glance widget can be handed a bundled font at all — so the scale
 * would be chosen before the face it is being chosen for. Type is still
 * `MaterialTheme`'s default here, and that is the last stock thing left.
 *
 * A habit's own colour is per-row and comes from the event log, not from here;
 * [HabitPalette] is what the editor offers and [glyphColorOn] decides what is
 * drawn on top of it.
 *
 * @param darkTheme exposed, and defaulted, so a preview or a test can render a
 *   scheme the host is not in. Both are drawn in production by the system
 *   setting alone.
 */
@Composable
fun GawiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) GawiDarkColors else GawiLightColors, content = content)
}
