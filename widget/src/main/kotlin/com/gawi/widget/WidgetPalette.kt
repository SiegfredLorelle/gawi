package com.gawi.widget

import androidx.glance.color.ColorProvider
import com.gawi.core.ui.theme.GawiRole
import com.gawi.core.ui.theme.gawiRole

/**
 * The widget's colours, each in a day and a night value, derived from `:core:ui`.
 *
 * **Why the widget needs its own object at all.** A Glance tree is `RemoteViews`
 * under the composition, so it cannot consume `GawiTheme`
 * (docs/architecture.md §2) — it has to draw the same colours from its own
 * values. What it does *not* have to do is retype them: `GawiRole` publishes the
 * roles a reproducing surface draws, and every value below is read from there.
 *
 * **This used to be eight hand-typed literals, and that was the debt.** Four
 * roles in two schemes, of which only `surface`'s pair was pinned to its
 * original, by a test-source-only import of `gawiWindowBackground`. The other
 * three roles had no accessor, so retuning
 * `onSurface`, `primary` or `outline` in `core/ui/theme/Color.kt` moved the
 * app's checkboxes and left the widget's glyph behind with every test in this
 * module still green. docs/ux/visual-identity.md §7.4 named that as the widget
 * set's debt to clear and costed two ways out — three more accessors, or reading
 * a composed `GawiTheme` in a Robolectric test. Deriving is neither: it removes
 * the second copy rather than guarding it, so there is no longer a drift to
 * detect. §2's claim that "there is no mechanism that would let it be one" was
 * wrong, and wrong in a way that reads as correct — a Glance tree cannot take a
 * Compose *theme*, but a plain `Color` is not a theme.
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
internal object WidgetPalette {
    /** The ground the whole widget is drawn on. */
    val surface = provider(GawiRole.Surface)

    /** Every string the widget draws, as the tint on a bitmap. */
    val onSurface = provider(GawiRole.OnSurface)

    /** A completed habit's checkbox glyph. */
    val glyphChecked = provider(GawiRole.Primary)

    /** An outstanding habit's checkbox glyph — semantic, per docs/ux/visual-identity.md §4.1. */
    val glyphUnchecked = provider(GawiRole.Outline)

    private fun provider(role: GawiRole) = ColorProvider(day = gawiRole(role, darkTheme = false), night = gawiRole(role, darkTheme = true))
}
