package com.gawi.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * WCAG relative contrast between two opaque colours.
 *
 * Test fixtures rather than production because nothing the app draws needs to
 * know a ratio — [glyphColorOn] only needs to know which side of the pivot a
 * colour falls on. Published from `:core:ui` rather than held in `:core:testing`
 * because the colours it measures are this module's: a test set that wants the
 * formula should not take a dependency on the read model and Room with it. The tests need the number so they can assert the property
 * the pivot and the palette exist to deliver rather than the constants that
 * deliver it.
 *
 * [luminance] *is* the WCAG relative-luminance formula — the sRGB
 * linearisation and the 0.2126/0.7152/0.0722 weighting — so the arithmetic is
 * not repeated here. It reads RGB and ignores alpha, which is right for a
 * resolved colour; a translucent one has to be composited first, the way
 * `glyphColorOn` does it.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val high = maxOf(a.luminance(), b.luminance())
    val low = minOf(a.luminance(), b.luminance())
    return (high + WCAG_OFFSET) / (low + WCAG_OFFSET)
}

/** WCAG 2.1 AA for normal-sized text. */
const val WCAG_TEXT_FLOOR = 4.5f

/**
 * WCAG 2.1 AA for a user-interface component or a meaningful graphic — 1.4.11.
 *
 * The floor a habit's colour badge and the widget band's two fills are held
 * to, because each is a graphic that carries meaning rather than text. The
 * glyph drawn *on* a badge is text and takes [WCAG_TEXT_FLOOR].
 */
const val WCAG_NON_TEXT_FLOOR = 3.0f

/** WCAG's constant, which keeps the ratio finite when one side is pure black. */
private const val WCAG_OFFSET = 0.05f
