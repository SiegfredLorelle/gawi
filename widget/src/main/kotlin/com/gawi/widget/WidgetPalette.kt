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
 * **Every value derives rather than being retyped.** Hand-typed literals are
 * the debt this avoids: four roles in two schemes, and with no accessor for
 * `onSurface`, `primary` or `outline`, retuning one in `core/ui/theme/Color.kt`
 * moves the app's checkboxes and leaves the widget's glyph behind with every
 * test in this module still green — the debt docs/ux/visual-identity.md §7.4
 * names. Deriving removes the second copy rather than guarding it, so there is
 * no drift left to detect: a Glance tree cannot take a Compose *theme*, but a
 * plain `Color` is not a theme.
 *
 * **Why day/night providers, and not the pinned literals the docs ask for.**
 * The bug this fixes is a disagreement between two colours, so pinning one
 * side cannot fix it — and a single flat literal cannot clear 4.5:1 against
 * both a light and a dark ground at once, because that needs a relative
 * luminance at or below 0.17 against `#F4FBFA` and at or above 0.229 against
 * `#0E1A1C`. What breaks on API 29 and 30 is an asymmetry of translation
 * paths, measured on emulators of both levels and read out of
 * `glance-appwidget` 1.1.1 to confirm it:
 *
 *  - A background from a *resource* provider becomes
 *    `setViewBackgroundColorResource`, which the **host** resolves in its own
 *    theme — so it follows a night-mode toggle on its own, immediately.
 *  - An image tint has no resource path below API 31 at all, and the checkbox
 *    glyph below 31 goes through `getColor(context)` the same way, so both are
 *    resolved in *our* process at translation and keep the last render's value.
 *
 * One side following the host while the other two stay put is near-zero contrast
 * rather than a stale widget ([BitmapText] has the numbers). A day/night
 * provider is translated the same way at all three sites: API 31 and up hands
 * the host a day/night pair, so the widget still follows a toggle instantly;
 * below 31 all three resolve in our process at the same instant, from the same
 * configuration, so they cannot disagree. A toggle there leaves the widget stale
 * *together* and legible, repairing at the next render (docs/running.md §4).
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

    /** A secondary line: the streak widget's "as of" date, and its "was 12". */
    val caption = provider(GawiRole.OnSurfaceVariant)

    /** A day streak's numeral. */
    val streakDays = provider(GawiRole.Primary)

    /**
     * A week streak's numeral.
     *
     * Never interchangeable with [streakDays]. `StreakUi` is a sealed type
     * precisely so a count of days and a count of weeks cannot be styled as the
     * same number, and this is one of the three signals that keeps them apart on
     * the widget — the others being the unit, which is a `w` suffix when the
     * host reports too little width for the word, and the word itself when it
     * fits.
     */
    val streakWeeks = provider(GawiRole.Tertiary)

    /** A broken streak's zero, and the habit name beside it. */
    val streakBroken = provider(GawiRole.Outline)

    /**
     * Momo's ground on a widget: the Momo widget's whole background, and the
     * pill behind her face on the large Today body (docs/ux/widget.md §7). The
     * tank colour the Today screen paints behind her, flat — a `RemoteViews`
     * background is one colour, and flat was decided anyway. A *ground*, so the
     * polarity test treats it like [surface]: darker at night, not lighter.
     */
    val momoGround = provider(GawiRole.PrimaryContainer)

    /** The one ink drawn on [momoGround]: the Momo widget's caption. */
    val momoCaption = provider(GawiRole.OnPrimaryContainer)

    /**
     * A woven segment of the large Today body's day band — the same role as a
     * completed glyph, named separately for the reason the streak inks are.
     */
    val bandWoven = provider(GawiRole.Primary)

    /**
     * An outstanding segment of the band. A *fill* with nothing drawn on it, so
     * it owes no 4.5:1 to anything; what it owes is 3:1 against [bandWoven], the
     * pair being the information, and `WidgetPaletteTest` holds that.
     */
    val bandOutstanding = provider(GawiRole.OutlineVariant)

    /*
     * On [streakDays] and [glyphChecked] being two names for one role, and
     * [streakBroken] and [glyphUnchecked] likewise: the sharing is the point,
     * not an oversight to be collapsed. `primary` means the positive semantic
     * state on both surfaces and `outline` the neutral one (§4.1), so a retune
     * of either role should move both — while the two *names* are what let
     * `WidgetPaletteTest` pin which role each drawn thing claims. Collapsing
     * them to one name each would make that test a restatement of the
     * declaration, which is the trap the pin against `gawiWindowBackground`
     * fell into.
     */

    private fun provider(role: GawiRole) = ColorProvider(day = gawiRole(role, darkTheme = false), night = gawiRole(role, darkTheme = true))
}
