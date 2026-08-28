package com.gawi.widget.testsupport

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** WCAG AA for normal text. Float, to match what [contrastRatio] returns. */
const val MIN_CONTRAST = 4.5f

/**
 * WCAG 2.1 contrast, over Compose's own relative luminance.
 *
 * [luminance] *is* the WCAG relative-luminance formula — the sRGB linearisation
 * and the 0.2126/0.7152/0.0722 weighting — so hand-rolling it here, in the
 * arithmetic two tests depend on getting right, only added a second place for it
 * to be wrong. `core/ui/theme/HabitColor.kt` was already calling the library
 * version.
 *
 * It reads RGB and ignores alpha, which is fine for every operand it is given
 * here: each is a resolved colour off a `ColorProvider`, not a translucent tint.
 * A translucent one would have to be composited first, the way `glyphColorOn`
 * does it.
 *
 * Shared by `WidgetTextColourTest`, which measures what the widget draws, and
 * `WidgetPaletteTest`, which measures the palette it draws it from. It lives
 * here rather than in either because a second copy is how the two would drift
 * apart while both stayed green.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + WCAG_OFFSET) / (minOf(la, lb) + WCAG_OFFSET)
}

/** WCAG's constant, which keeps the ratio finite when one side is pure black. */
private const val WCAG_OFFSET = 0.05f
