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

    /**
     * Material's minimum interactive size, and the floor for anything a finger
     * has to hit.
     *
     * Named here because bare `selectable`, `toggleable` and `clickable` do
     * **not** apply it — only a Material component's own
     * `minimumInteractiveComponentSize` does. So a control made interactive by a
     * modifier has to reach this itself, which is how three separate pickers
     * came to declare the same 48 with three different explanations of it.
     *
     * Distinct from [IconBox] rather than a bigger version of it. 40dp is the
     * circle a habit's icon is *drawn* in, and where that circle sits in a list
     * row the row is the target. This is the floor for a control that **is** the
     * target.
     */
    val TouchTarget: Dp = 48.dp
}
