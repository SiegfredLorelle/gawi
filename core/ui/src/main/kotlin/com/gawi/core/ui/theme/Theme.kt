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
 * **Type is Outfit, as of 2026-08-24.**
 * [GawiTypography] carries it; §5 chose the face and Type.kt records why. The
 * typeface had been parked on an experiment — whether a Glance widget can be
 * handed a bundled font at all — so that the scale would not be chosen before
 * the face it was being chosen for. **That experiment ran and the answer was
 * no**: `RemoteViews` inflation resolves only the platform's generic family
 * names and drops a bundled font resource silently
 * (docs/ux/visual-identity.md §2). The widget got the face anyway a day later,
 * as bitmaps — `widget/…/BitmapText.kt`, which reaches into this module for
 * `R.font.outfit` and for nothing else.
 *
 * Not *everything* drawn is Outfit, and the gap is narrower than "the app is
 * restyled" suggests — narrower again since 2026-08-24. Five of the glyph
 * characters the screens drew as text were outside this font's `cmap` and fell
 * back to the platform face, so an app bar mixed the two. That was a reason to
 * replace those dingbats with icons rather than to doubt the face, and it is
 * what `GawiIcons` did. What still comes from this `cmap` is text that is not an
 * icon: `RetroStrip`'s day marks and the editor's selection tick. [Outfit]'s
 * KDoc has the audit and what outlived it — this paragraph asserted the
 * fallback in the present tense for one commit too long, which is what a claim
 * repeated in four files costs.
 *
 * The divergence it warned about was accepted on 2026-08-24 — the app in
 * Outfit, `:widget` in the system sans, one home screen apart — and closed on
 * 2026-08-25 by rasterising the widget's text instead. A quieter face would
 * have hidden the seam for that one day and given up the identity this whole
 * brief exists to buy. Type.kt has the rest, and docs/ux/visual-identity.md §5
 * is where the trade and its reversal are recorded.
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
