package com.gawi.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * A habit's stored colour, or null if it is not one.
 *
 * `HabitState.color` is an unvalidated string off the event log — no command
 * checks it and no projection normalises it — so anything drawing a habit has
 * to survive whatever is in there rather than crash the screen. Hand-rolled
 * because `android.graphics.Color.parseColor` would put Robolectric on the
 * test classpath of every module that draws a habit, for what is a string parse.
 *
 * Shared rather than duplicated: the Today view reads it for a row's icon tint
 * and the habits editor reads it to preview the swatch you are picking. Two
 * copies of a parser is two answers to "is this a colour".
 */
fun parseHabitColor(hex: String): Color? {
    val digits = hex.removePrefix("#")
    // Every guard as one expression, because a hash, a length and a digit set
    // are three ways of saying the same thing: this either is a colour or is
    // not. toLongOrNull would otherwise accept a leading sign, making "#-abcde"
    // six characters that parse negative and mask into an arbitrary opaque
    // colour rather than falling back to a theme role.
    val argb = when {
        digits.length == hex.length -> null

        !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> null

        // Color(Long) reads 0xAARRGGBB. Color(ULong) is the raw packed encoding
        // and would read these digits as a different colour space entirely.
        digits.length == RGB_DIGITS -> digits.toLongOrNull(radix = HEX_RADIX)?.or(OPAQUE_ALPHA)

        digits.length == ARGB_DIGITS -> digits.toLongOrNull(radix = HEX_RADIX)

        else -> null
    }
    return argb?.let { Color(it) }
}

/**
 * The colour a habit's icon glyph should take when it sits on [tint].
 *
 * Composited first, because [luminance] is WCAG relative luminance over the RGB
 * channels and ignores alpha entirely. A translucent tint would otherwise be
 * judged on the colour it nominally is rather than the colour it renders as —
 * translucent white reads as bright and picks a dark glyph, while what the user
 * sees is mostly [background] showing through, which in dark mode is nearly
 * black. Compositing makes a fully transparent tint fall out correctly too: it
 * resolves to [background] and the glyph contrasts against that.
 *
 * A decision rather than layout, which is why it is a function with a test and
 * not a line inside a composable.
 */
fun glyphColorOn(tint: Color, background: Color): Color =
    if (tint.compositeOver(background).luminance() > CONTRAST_PIVOT) Color.Black else Color.White

/** Above this, a background is light enough to want dark text on it. */
private const val CONTRAST_PIVOT = 0.5f

private const val HEX_RADIX = 16
private const val RGB_DIGITS = 6
private const val ARGB_DIGITS = 8
private const val OPAQUE_ALPHA = 0xFF000000L
