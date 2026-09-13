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

    /**
     * Material's minimum interactive size, and the floor for anything a finger
     * has to hit.
     *
     * Named here because bare `selectable`, `toggleable` and `clickable` do
     * **not** apply it — only a Material component's own
     * `minimumInteractiveComponentSize` does. A control made interactive by a
     * modifier has to reach this floor itself, and a Material component handed
     * `onCheckedChange = null` opts out of its own: settings' switch row is
     * both at once, a `toggleable` Row around an inert `Switch`, so the row's
     * own height is the only thing holding this floor.
     *
     * A control drawn larger than a finger needs is a different measurement and
     * does not belong here. This is the floor for a control that **is** the
     * target.
     */
    val TouchTarget: Dp = 48.dp
}
