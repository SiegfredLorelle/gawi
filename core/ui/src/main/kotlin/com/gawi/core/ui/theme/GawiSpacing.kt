package com.gawi.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Named dimensions, so a composable body holds no bare numbers.
 *
 * Not a spacing system, and narrower than it looks. The app's colours are
 * designed — the scheme in docs/ux/visual-identity.md §7.2 and the habit hues
 * in §6 — but §8 of the same document keeps dimensions out of that brief. So
 * these are the few measurements the screens actually use, named where more
 * than one place has to agree on them, and nothing inferred from them.
 *
 * The type scale brings no spacing scale with it either: [GawiTypography]
 * changes the face and leaves Material's sizes and line heights alone, so there
 * is no new rhythm here for one to be derived from.
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
     * `minimumInteractiveComponentSize` does. A control made interactive by a
     * modifier has to reach this floor itself.
     *
     * Distinct from [IconBox] rather than a bigger version of it. 40dp is the
     * circle a habit's icon is *drawn* in, and where that circle sits in a list
     * row the row is the target. This is the floor for a control that **is** the
     * target.
     */
    val TouchTarget: Dp = 48.dp
}
