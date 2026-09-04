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
 * **Type is Outfit.** [GawiTypography] carries it; §5 chose the face and
 * Type.kt records why. A Glance widget cannot be handed a bundled font:
 * `RemoteViews` inflation resolves only the platform's generic family names and
 * drops a bundled font resource silently (docs/ux/visual-identity.md §2). The
 * widget draws the face as bitmaps instead — `widget/…/BitmapText.kt`, which
 * reaches into this module for `R.font.outfit` and for nothing else.
 *
 * Not *everything* drawn is Outfit. What comes from this font's `cmap` is text
 * that is not an icon: `RetroStrip`'s day marks and the editor's selection
 * tick. [Outfit]'s KDoc has the audit and the characters it covers, and it is
 * not repeated here, because a claim repeated in four files is one that drifts
 * in three of them. The app in Outfit against `:widget` in the system sans is
 * the divergence that rasterising the widget's text closes, and
 * docs/ux/visual-identity.md §5 records the trade.
 *
 * A habit's own colour is per-row and comes from the event log, not from here;
 * [HabitPalette] is what the editor offers and [glyphColorOn] decides what is
 * drawn on top of it.
 *
 * **Which of the two is drawn is the user's.** `UserSettings.theme` holds
 * System, Light or Dark; `MainActivity` resolves that against
 * [isSystemInDarkTheme] and passes the answer in, and on API 31 and up it also
 * tells the platform, so the window painted before `setContent` agrees
 * (docs/ux/settings.md §7).
 *
 * @param darkTheme exposed, and defaulted, so a preview or a test can render a
 *   scheme the host is not in. The default is the device's setting, which is
 *   what the app draws when the user has not chosen otherwise.
 */
@Composable
fun GawiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) GawiDarkColors else GawiLightColors,
        typography = GawiTypography,
        content = content,
    )
}
