package com.gawi.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider

/*
 * On the `@Suppress("MagicNumber")` below: the same reasoning as
 * core/ui/theme/Color.kt, which carries the argument in full. A colour literal
 * is the value, not a number standing in for one, and detekt's
 * `ignoreNamedArgument` does not reach a literal nested one call deeper, so
 * `day = Color(0xFFF4FBFA)` is flagged even though the argument is named.
 */

/**
 * The widget's own four colours, each in a day and a night value.
 *
 * **Why a second copy of hexes that already exist.** A Glance tree is
 * `RemoteViews` under the composition, so it cannot consume `GawiTheme`
 * (docs/architecture.md §2), and `:widget` sees `:core:ui` for the Outfit font
 * and Momo's geometry only (build.gradle.kts). Hand-copied hexes are the
 * duplication docs/ux/visual-identity.md §2 sanctions. The values are `surface`,
 * `onSurface`, `primary` and `outline` from `core/ui/theme/Color.kt`.
 *
 * **The copy is not unguarded, and it is half-guarded rather than whole.**
 * `:core:ui` already publishes `gawiWindowBackground(darkTheme)` for exactly this
 * problem — its KDoc calls it "the only value that leaves the module without
 * going through `GawiTheme`, because it is the only one another module has to
 * reproduce rather than consume", and `:app`'s XML copy is pinned against it by
 * `WindowBackgroundTest`. [surface] is the same two hexes, so `WidgetPaletteTest`
 * pins it the same way, from the test source set so no production import
 * widens the edge. The other three have no such accessor, deliberately, so
 * **retuning `onSurface`, `primary` or `outline` in `core/ui/theme/Color.kt`
 * will not fail anything here** — the app's checkboxes would move and the
 * widget's glyph would not. Adding three accessors to `:core:ui` would close
 * that, and it is a decision for §7.4's full palette rather than a legibility
 * fix to make on its way past.
 *
 * **Why day/night providers, and not the pinned literals the docs asked for
 * until 2026-08-28.** The bug this fixes is a disagreement between two colours,
 * so pinning one side cannot fix it — and a single flat literal cannot clear
 * 4.5:1 against both a light and a dark ground at once, because that needs a
 * relative luminance at or below 0.17 against `#F4FBFA` and at or above 0.229
 * against `#0E1A1C`. What went wrong on API 29 and 30 was an asymmetry of
 * translation paths, measured on emulators of both levels and read out of
 * `glance-appwidget` 1.1.1 to confirm it:
 *
 *  - A background from a *resource* provider becomes
 *    `setViewBackgroundColorResource`, which the **host** resolves in its own
 *    theme — so it followed a night-mode toggle on its own, immediately.
 *  - An image tint has no resource path below API 31 at all, and the checkbox
 *    glyph below 31 goes through `getColor(context)` the same way, so both were
 *    resolved in *our* process at translation and kept the last render's value.
 *
 * One side following the host while the other two stayed put is what produced
 * near-zero contrast rather than a stale widget ([BitmapText] has the numbers).
 * A day/night provider is translated the same way at all three sites: API 31
 * and up hands the host a day/night pair, so the widget still follows a toggle
 * instantly; below 31 all three resolve in our process at the same instant,
 * from the same configuration, so they cannot disagree. A toggle there now
 * leaves the widget stale *together* and legible, repairing at the next render
 * — which is what docs/running.md §4 expected of it in the first place.
 *
 * Contrast against [surface] in the matching scheme, light then night: 16.59 and
 * 14.82 for [onSurface], 5.56 and 10.44 for [glyphChecked], 5.18 and 5.31 for
 * [glyphUnchecked] — WCAG ratios, matching what was sampled off a launcher on
 * API 29 and 30 (docs/ux/widget.md). `WidgetPaletteTest` asserts all six against
 * the 4.5 floor rather than against these figures, so treat them as a record of
 * the headroom and not as a contract; `WidgetTextColourTest` measures what the
 * widget actually draws, against the ground it actually draws it on.
 *
 * The type is deliberately left inferred. `androidx.glance.color.ColorProvider`
 * returns a `DayNightColorProvider`, and that class is `@RestrictTo(LIBRARY)`,
 * so it can be built and used but never named.
 */
@Suppress("MagicNumber")
internal object WidgetPalette {
    /** The ground the whole widget is drawn on — `surface`. */
    val surface = ColorProvider(day = Color(0xFFF4FBFA), night = Color(0xFF0E1A1C))

    /** Every string the widget draws, as the tint on a bitmap — `onSurface`. */
    val onSurface = ColorProvider(day = Color(0xFF101C1E), night = Color(0xFFDCEEF0))

    /** A completed habit's checkbox glyph — `primary`. */
    val glyphChecked = ColorProvider(day = Color(0xFF1F6F78), night = Color(0xFF7FD4DC))

    /** An outstanding habit's checkbox glyph — `outline`, which docs/ux/visual-identity.md §4.1 makes semantic. */
    val glyphUnchecked = ColorProvider(day = Color(0xFF5B6D70), night = Color(0xFF7D9093))
}
