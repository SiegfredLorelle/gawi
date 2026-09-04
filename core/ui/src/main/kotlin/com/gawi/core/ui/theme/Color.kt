package com.gawi.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * On the two `@Suppress("MagicNumber")` below.
 *
 * A colour literal is the value, not a number standing in for one, so there is
 * nothing to extract: naming each of the 96 would alias them to 96 invented
 * words and make the ramps harder to read rather than easier. Measured rather
 * than assumed, because the config reads as though it might not fire — detekt's
 * `ignoreNamedArgument` does not reach a literal nested one call deeper, so
 * `primary = Color(0xFF1F6F78)` is flagged even though the argument is named.
 * Narrowed to these two declarations rather than widened in
 * config/detekt/detekt.yml, which has a single repo-wide config: an override
 * there would switch the rule off for every module.
 *
 * What keeps these honest is GawiColorSchemeTest, which asserts the contrast of
 * every pair the app draws. That is a property naming could never have carried.
 */

/**
 * The light scheme — docs/ux/visual-identity.md §7.2's "teal habitat".
 *
 * §3 fixes four roles and reserves one colour; the rest are derived from the
 * three hue families those four imply — teal at OKLCH hue 206, gold at 89, a
 * teal-tinted neutral at 209 — plus a warm red at 27 for `error`. Deriving them
 * rather than leaving Material's baseline underneath is the whole point: the
 * baseline is a purple family, so `outline`, `surfaceVariant` and
 * `secondaryContainer` would have shown lavender through a teal app, and §4.1
 * makes `outline` semantic rather than decorative.
 *
 * **`tertiary` is not §3's value, and this is the correction.** §3 lists
 * `#C9A227`, which is 2.31:1 on this surface — and `tertiary` is drawn as plain
 * text (a week streak in `StreakBadge`), so WCAG's 4.5:1 applies to it. §4.1's
 * own requirement is that `tertiary` step *darker* than `primary` in light mode,
 * which `#C9A227` does not: it is lighter. `#665012` satisfies both at 7.36:1
 * with a 1.32 lightness step. The cost, recorded rather than hidden: gold at
 * this lightness reads as bronze.
 *
 * Momo's pinks are deliberately absent. §3 reserves them to the mascot, so
 * none is a role — the UI is the tank and Momo is the only warm thing in it.
 * The values are the character's own, in [com.gawi.core.ui.component.MomoPalette].
 */
@Suppress("MagicNumber")
internal val GawiLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1F6F78),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB4E9F0),
    onPrimaryContainer = Color(0xFF00353A),
    inversePrimary = Color(0xFF7FD4DC),
    secondary = Color(0xFF516F73),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9EC),
    onSecondaryContainer = Color(0xFF1B383C),
    tertiary = Color(0xFF665012),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0DEB3),
    onTertiaryContainer = Color(0xFF372900),
    background = Color(0xFFF4FBFA),
    onBackground = Color(0xFF101C1E),
    surface = Color(0xFFF4FBFA),
    onSurface = Color(0xFF101C1E),
    surfaceVariant = Color(0xFFD6E9EC),
    onSurfaceVariant = Color(0xFF405457),
    surfaceTint = Color(0xFF1F6F78),
    inverseSurface = Color(0xFF222E30),
    inverseOnSurface = Color(0xFFE7F3F4),
    error = Color(0xFFB6322D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD4CE),
    onErrorContainer = Color(0xFF590A0B),
    outline = Color(0xFF5B6D70),
    outlineVariant = Color(0xFFC3D3D6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5FDFE),
    surfaceDim = Color(0xFFC6D8DB),
    surfaceContainer = Color(0xFFE3F0F2),
    surfaceContainerHigh = Color(0xFFDBE9EC),
    surfaceContainerHighest = Color(0xFFD3E3E6),
    surfaceContainerLow = Color(0xFFEBF6F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    primaryFixed = Color(0xFFB4E9F0),
    primaryFixedDim = Color(0xFF8DD2DA),
    onPrimaryFixed = Color(0xFF003237),
    onPrimaryFixedVariant = Color(0xFF065860),
    secondaryFixed = Color(0xFFCAE4E7),
    secondaryFixedDim = Color(0xFFABCBCF),
    onSecondaryFixed = Color(0xFF143034),
    onSecondaryFixedVariant = Color(0xFF365356),
    tertiaryFixed = Color(0xFFEEDDB1),
    tertiaryFixedDim = Color(0xFFD6C290),
    onTertiaryFixed = Color(0xFF372900),
    onTertiaryFixedVariant = Color(0xFF5C4B1A),
)

/**
 * The dark scheme — the same habitat with the tank lights off.
 *
 * §7.2 settled the scheme on this half rather than the light one: dark is where
 * coral Momo has nowhere to hide against deep teal, which is what decided §3's
 * candidate A over the coral-primary alternative.
 *
 * **`tertiary` is not §3's value here either.** §3 lists `#E8C55E`, whose
 * luminance ratio against this scheme's `primary` is 1.02 — identical lightness,
 * distinguished by hue alone. That is the exact failure §4.1 measured and
 * rejected, and it would take the day-versus-week streak distinction out in
 * greyscale and under deuteranopia. `#C9A227` restores the step at 1.42 while
 * staying above 4.5:1 on this surface.
 *
 * `secondaryContainer` is darker than a straight mirror of the light scheme
 * would make it, and that came out of a device pass rather than a table. It is a
 * *ground*: `RetroStrip` fills today's cell with it and then draws the weekday
 * letter in `onSurfaceVariant` on top. At the lighter value that pair measured
 * 4.24:1 — a real text failure, on the one cell every user looks at every day.
 * Recessive content needs a dark enough ground to be recessive *on*, which a
 * mid-tone container does not give. The cost is that today's cell reads as a
 * slightly subtler fill: 1.59:1 against `surface` rather than 1.96:1, still more
 * visible than the light scheme's own 1.21:1.
 *
 * The `*Fixed` roles are shared with the light scheme by definition — Material
 * specifies them as theme-invariant — so the two declarations repeat those
 * twelve values rather than either scheme owning them.
 */
@Suppress("MagicNumber")
internal val GawiDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF7FD4DC),
    onPrimary = Color(0xFF003237),
    primaryContainer = Color(0xFF0F545C),
    onPrimaryContainer = Color(0xFFB2E7EE),
    inversePrimary = Color(0xFF1F6F78),
    secondary = Color(0xFFA1C1C6),
    onSecondary = Color(0xFF1B3235),
    secondaryContainer = Color(0xFF273F42),
    onSecondaryContainer = Color(0xFFCAE4E7),
    tertiary = Color(0xFFC9A227),
    onTertiary = Color(0xFF2B2000),
    tertiaryContainer = Color(0xFF544310),
    onTertiaryContainer = Color(0xFFEEDDB1),
    background = Color(0xFF0E1A1C),
    onBackground = Color(0xFFDCEEF0),
    surface = Color(0xFF0E1A1C),
    onSurface = Color(0xFFDCEEF0),
    surfaceVariant = Color(0xFF253336),
    onSurfaceVariant = Color(0xFFA2B5B9),
    surfaceTint = Color(0xFF7FD4DC),
    inverseSurface = Color(0xFFDEEBED),
    inverseOnSurface = Color(0xFF1A2628),
    error = Color(0xFFEF958B),
    onError = Color(0xFF490D0B),
    errorContainer = Color(0xFF7F211D),
    onErrorContainer = Color(0xFFFFD4CE),
    outline = Color(0xFF7D9093),
    outlineVariant = Color(0xFF324042),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF344345),
    surfaceDim = Color(0xFF0A1618),
    surfaceContainer = Color(0xFF192628),
    surfaceContainerHigh = Color(0xFF223032),
    surfaceContainerHighest = Color(0xFF2C3A3D),
    surfaceContainerLow = Color(0xFF152022),
    surfaceContainerLowest = Color(0xFF030C0E),
    primaryFixed = Color(0xFFB4E9F0),
    primaryFixedDim = Color(0xFF8DD2DA),
    onPrimaryFixed = Color(0xFF003237),
    onPrimaryFixedVariant = Color(0xFF065860),
    secondaryFixed = Color(0xFFCAE4E7),
    secondaryFixedDim = Color(0xFFABCBCF),
    onSecondaryFixed = Color(0xFF143034),
    onSecondaryFixedVariant = Color(0xFF365356),
    tertiaryFixed = Color(0xFFEEDDB1),
    tertiaryFixedDim = Color(0xFFD6C290),
    onTertiaryFixed = Color(0xFF372900),
    onTertiaryFixedVariant = Color(0xFF5C4B1A),
)

/**
 * A palette role that another module has to **reproduce** rather than consume.
 *
 * The two [ColorScheme]s stay `internal`, and a surface inside the app reads
 * `MaterialTheme.colorScheme` — it must not come here. What this exists for is
 * the surfaces that cannot: a Glance tree is `RemoteViews` under the
 * composition and cannot consume [GawiTheme] (docs/architecture.md §2), and a
 * platform window or an adaptive icon is XML, which cannot read Kotlin at all.
 * Those surfaces have to draw the same colours from their own values, so the
 * only question is whether the second copy is derived or typed out again.
 *
 * **Typing it out again is the debt this avoids.** Six literals in
 * `WidgetPalette` with only `surface` pinned to its original would mean
 * retuning `onSurface`, `primary` or `outline` here moves the app's checkboxes
 * and leaves the widget's glyph where it was, with every test in `:widget`
 * still green — docs/ux/visual-identity.md §7.4 named that as the widget set's debt
 * to clear, and §2 claimed no mechanism could reduce the two copies to one.
 * A plain [Color] is not a theme, which is why one can: the widget now derives
 * its `ColorProvider`s from these values and holds no hex of its own.
 *
 * Only the roles a reproducing surface actually draws are listed. Adding an
 * entry is a deliberate widening of that edge, not a convenience.
 */
enum class GawiRole {
    /** The ground every other role in this list is measured against. */
    Surface,

    /** Body text and any glyph that is not carrying a semantic role. */
    OnSurface,

    /** Captions and secondary lines — a date, a unit, a "was 12". */
    OnSurfaceVariant,

    /** A day streak, and a completed checkbox. Semantic, per §4.1. */
    Primary,

    /** A week streak. Never interchangeable with [Primary] — see `StreakUi`. */
    Tertiary,

    /** An outstanding checkbox, and a broken streak. Semantic, per §4.1. */
    Outline,

    /**
     * A ground, not an ink: Momo's tank colour on a widget — the Momo widget's
     * whole background and the pill behind her face on the large Today body
     * (docs/ux/widget.md §7). The first role in this list that nothing draws
     * text *in*.
     */
    PrimaryContainer,

    /** The one ink drawn on [PrimaryContainer]: the Momo widget's caption. */
    OnPrimaryContainer,

    /**
     * A fill, not an ink: an outstanding segment of the large Today body's woven
     * day band, against [Primary] for a woven one. The same low-contrast role
     * `RetroStrip` borders a shut cell with; here the pair it forms with
     * [Primary] is what carries the meaning, and `GawiColorSchemeTest` holds
     * that pair to the non-text floor.
     */
    OutlineVariant,
}

/**
 * One [GawiRole]'s value in the named scheme.
 *
 * `darkTheme` rather than a `ColorScheme`, because the callers that need this
 * have no composition to read one from: they are asked for both values at once
 * and hand the pair to something that resolves it later (a Glance day/night
 * `ColorProvider`) or to a test comparing an XML copy against both.
 *
 * Every *ink* role listed clears WCAG 4.5:1 against [GawiRole.Surface] in the
 * matching scheme, which is what makes deriving safe for text: light 16.59,
 * 7.63, 5.56, 7.36 and 5.18; dark 14.82, 8.32, 10.44, 7.34 and 5.31, in the
 * order the first five ink entries are declared. The last three are a ground,
 * its ink and a fill: [GawiRole.OnPrimaryContainer] clears 4.5:1
 * on [GawiRole.PrimaryContainer] (light 10.10, dark 6.37) and
 * [GawiRole.OutlineVariant] sits 3:1 or more from [GawiRole.Primary] (light
 * 3.78, dark 6.34), which is the non-text floor for a pair of fills whose
 * difference is the information. `GawiColorSchemeTest` asserts the pairs the
 * app draws and `WidgetPaletteTest` asserts these against the floor on the
 * widget's own grounds, so a retune that breaks either fails a test.
 */
fun gawiRole(role: GawiRole, darkTheme: Boolean): Color {
    val scheme = if (darkTheme) GawiDarkColors else GawiLightColors
    return when (role) {
        GawiRole.Surface -> scheme.surface
        GawiRole.OnSurface -> scheme.onSurface
        GawiRole.OnSurfaceVariant -> scheme.onSurfaceVariant
        GawiRole.Primary -> scheme.primary
        GawiRole.Tertiary -> scheme.tertiary
        GawiRole.Outline -> scheme.outline
        GawiRole.PrimaryContainer -> scheme.primaryContainer
        GawiRole.OnPrimaryContainer -> scheme.onPrimaryContainer
        GawiRole.OutlineVariant -> scheme.outlineVariant
    }
}

/**
 * The colour a host should paint its window before Compose runs.
 *
 * Exists because that window is not drawn by this module and cannot be. A
 * platform window is painted from an XML theme attribute before `setContent`,
 * XML cannot read Kotlin, and so `:app` keeps a hand-copy of this value in
 * `values/colors.xml`. This is the seam that makes the copy *checkable*:
 * `WindowBackgroundTest` compares the resource against this function in both
 * themes, so retuning `surface` fails a test instead of shipping a cold start
 * that flashes one colour and settles on another.
 *
 * `surface` and not `background`: they are equal by design and
 * `GawiColorSchemeTest` pins that, but what a window sits under is the surface
 * the first screen draws.
 *
 * Kept as its own name rather than folded into [gawiRole], because the caller
 * is XML and the question it asks is "what goes behind the window", not "what
 * is the surface role" — the two are equal here by a decision that could be
 * revisited.
 */
fun gawiWindowBackground(darkTheme: Boolean): Color = gawiRole(GawiRole.Surface, darkTheme)

/**
 * The launcher icon's ground, for `:app`'s `LauncherIconTest` to pin
 * `@color/ic_launcher_background` against — the second scheme colour that has
 * to exist in XML too (an adaptive icon is XML). Light only, and not a theme
 * choice: launchers cache icons, and on Android 13+ a dark home screen shows
 * the themed monochrome layer instead (docs/ux/visual-identity.md §7.1).
 */
fun gawiLauncherBackground(): Color = GawiLightColors.primaryContainer
