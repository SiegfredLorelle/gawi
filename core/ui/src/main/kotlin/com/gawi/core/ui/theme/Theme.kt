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
 * **Type is Outfit, as of 2026-08-24, and nothing here is stock any more.**
 * [GawiTypography] carries it; §5 chose the face and Type.kt records why. The
 * typeface had been parked on an experiment — whether a Glance widget can be
 * handed a bundled font at all — so that the scale would not be chosen before
 * the face it was being chosen for. **That experiment ran and the answer was
 * no**: `RemoteViews` inflation resolves only the platform's generic family
 * names and drops a bundled font resource silently
 * (docs/ux/visual-identity.md §2).
 *
 * So the divergence it warned about is real and was accepted rather than
 * avoided: the app renders in Outfit and `:widget` renders in the system sans,
 * permanently, one home screen apart. A quieter face would have hidden that and
 * given up the identity this whole brief exists to buy. Type.kt has the rest,
 * and docs/ux/visual-identity.md §5 is where to reopen the trade.
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
    MaterialTheme(
        colorScheme = if (darkTheme) GawiDarkColors else GawiLightColors,
        typography = GawiTypography,
        content = content,
    )
}
