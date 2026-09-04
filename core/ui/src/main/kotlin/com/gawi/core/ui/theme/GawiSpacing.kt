package com.gawi.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Named dimensions, so a composable body holds no bare numbers.
 *
 * Not a spacing system, and narrower than it looks. The app's colours are
 * designed now — the scheme in docs/ux/visual-identity.md §7.2 and the habit
 * hues in §6 — but §8 of the same document records that dimensions were
 * genuinely not part of that brief. So these stay what they have always been:
 * the few measurements the screens actually use, named where more than one
 * place needs to agree on them, and nothing inferred from them.
 *
 * **The type scale did not bring a spacing scale with it.** [GawiTypography]
 * moves the app to Outfit and touches no dimension, deliberately: it changes
 * the face and leaves Material's metrics alone, so there is no new rhythm here
 * for a spacing scale to be derived from. Dimensions are still what §8 of
 * docs/ux/visual-identity.md says they are — not part of that brief.
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
