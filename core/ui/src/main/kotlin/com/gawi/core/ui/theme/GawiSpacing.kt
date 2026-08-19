package com.gawi.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Named dimensions, so a composable body holds no bare numbers.
 *
 * Not a design system and not trying to be one — Momo's visual language is PRD
 * OQ-4 and undesigned. These are the few measurements the Today view actually
 * uses, named where more than one place needs to agree on them.
 */
object GawiSpacing {

    /** Padding inside a list row, and the gutter down the sides of a screen. */
    val Row: Dp = 16.dp

    /** Between a row's icon, its text and its trailing content. */
    val Gap: Dp = 12.dp

    /** Between two stacked lines inside a row. */
    val Line: Dp = 2.dp

    /** The tinted circle a habit's icon sits in. */
    val IconBox: Dp = 40.dp
}
