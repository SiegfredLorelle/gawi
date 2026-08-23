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
 * Momo's `#E0708F` is deliberately absent. §3 reserves it to the mascot, so it
 * is not a role — the UI is the tank and Momo is the only warm thing in it.
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
