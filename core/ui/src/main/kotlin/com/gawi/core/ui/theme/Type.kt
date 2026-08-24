package com.gawi.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.gawi.core.ui.R

/**
 * Outfit, bundled, as one variable font.
 *
 * The face docs/ux/visual-identity.md §5 was choosing, and the one the design
 * canvas draws in: geometric, OFL-licensed and on Google Fonts, so the file here
 * is the same one the browser previewed. The licence ships at
 * `licenses/Outfit-OFL.txt` — **not** beside the font, because `res/font/` takes
 * font files and XML families only and a resource filename cannot carry
 * uppercase letters, so an `OFL.txt` in there is a build error. §5 said
 * otherwise and has been corrected.
 *
 * One file, 110,884 bytes, against the 200-400KB §5 budgeted — a variable font
 * carries the whole `wght` axis in a single asset rather than one file per
 * weight.
 *
 * **Two entries, because `variationSettings` is what makes the axis real.** Both
 * point at the same file, and without the axis instance Compose would load the
 * font's default named instance for both and the Medium roles would silently
 * render at Regular. Exactly W400 and W500 are registered because those are the
 * only weights Material's fifteen roles ask for, and nothing in this app sets a
 * weight by hand (verified: no `FontWeight` reference in any `:core:ui`,
 * `:feature:*` or `:app` source). Adding a weight is one line, and inventing one
 * nothing draws is the thing §5 warns against.
 */
internal val Outfit = FontFamily(
    Font(
        resId = R.font.outfit,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_REGULAR)),
    ),
    Font(
        resId = R.font.outfit,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(WEIGHT_MEDIUM)),
    ),
)

private const val WEIGHT_REGULAR = 400
private const val WEIGHT_MEDIUM = 500

private val Default = Typography()

private fun TextStyle.inOutfit(): TextStyle = copy(fontFamily = Outfit)

/**
 * The app's type: Material's scale, drawn in [Outfit].
 *
 * **Only the face changes here, and that is deliberate rather than unfinished.**
 * Every size, line height and letter spacing is Material's baseline, untouched.
 * The sizes are the ones all four feature modules have been drawing at since
 * Phase 0, so they are the only part of this already validated on a device;
 * changing the face and the scale in one commit would make any regression
 * unattributable to either. `letterSpacing` is the value most likely to want
 * tuning next — Material's is tuned for Roboto, and Outfit is wider — and that
 * is a change to make while looking at a screen, not while writing this file.
 *
 * **The family is set on all fifteen roles, though §5 names ten.** The ten in
 * §5's table are the roles this app actually draws, and that list is what makes
 * the type reviewable — it is not a list of roles allowed to have a face. Left
 * at the default, the other five would render the *next* screen's
 * `headlineMedium` in Roboto, silently, and look like a design choice rather
 * than a gap. Covering all fifteen invents no sizes, which is what §5 was
 * guarding against, and removes the trap. [GawiTypographyTest] pins it.
 *
 * **The widget does not get this and cannot.** A `RemoteViews` tree resolves
 * only the platform's generic family names, measured on 2026-08-24
 * (docs/ux/visual-identity.md §2), so `:widget` renders in the system sans by
 * necessity. That divergence is the cost §5 accepted in choosing a geometric
 * face; docs/ux/visual-identity.md §5 is where to argue about it.
 */
val GawiTypography: Typography = Typography(
    displayLarge = Default.displayLarge.inOutfit(),
    displayMedium = Default.displayMedium.inOutfit(),
    displaySmall = Default.displaySmall.inOutfit(),
    headlineLarge = Default.headlineLarge.inOutfit(),
    headlineMedium = Default.headlineMedium.inOutfit(),
    headlineSmall = Default.headlineSmall.inOutfit(),
    titleLarge = Default.titleLarge.inOutfit(),
    titleMedium = Default.titleMedium.inOutfit(),
    titleSmall = Default.titleSmall.inOutfit(),
    bodyLarge = Default.bodyLarge.inOutfit(),
    bodyMedium = Default.bodyMedium.inOutfit(),
    bodySmall = Default.bodySmall.inOutfit(),
    labelLarge = Default.labelLarge.inOutfit(),
    labelMedium = Default.labelMedium.inOutfit(),
    labelSmall = Default.labelSmall.inOutfit(),
)
