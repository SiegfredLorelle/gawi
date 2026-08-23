package com.gawi.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * WCAG relative contrast between two opaque colours.
 *
 * In test code rather than production because nothing the app draws needs to
 * know a ratio — [glyphColorOn] only needs to know which side of the pivot a
 * colour falls on. The tests need the number so they can assert the property
 * the pivot and the palette exist to deliver rather than the constants that
 * deliver it.
 *
 * Shared by [HabitColorTest] and [GawiColorSchemeTest] rather than copied into
 * each. Both need the identical formula, and a hand-copied contrast decision is
 * this module's own cautionary tale: the habit icon badge was written three
 * times before it was shared, which meant three copies of the same judgement
 * and a fix that could land in one of them and look complete.
 *
 * `:widget` keeps its own copy, unavoidably — that module cannot depend on
 * `:core:ui` because a Glance tree is `RemoteViews` and cannot consume a
 * Compose theme.
 */
internal fun contrastRatio(a: Color, b: Color): Float {
    val high = maxOf(a.luminance(), b.luminance())
    val low = minOf(a.luminance(), b.luminance())
    return (high + WCAG_OFFSET) / (low + WCAG_OFFSET)
}

/** WCAG 2.1 AA for normal-sized text. */
internal const val WCAG_TEXT_FLOOR = 4.5f

/**
 * WCAG 2.1 AA for a user-interface component or a meaningful graphic — 1.4.11.
 *
 * The floor a habit's colour badge is held to, because it is a graphic that
 * carries meaning rather than text. The glyph drawn *on* it is text and takes
 * [WCAG_TEXT_FLOOR].
 */
internal const val WCAG_NON_TEXT_FLOOR = 3.0f

private const val WCAG_OFFSET = 0.05f
