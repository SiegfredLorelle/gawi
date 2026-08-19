package com.gawi.core.domain.streak

import java.time.LocalDate

/**
 * A habit's streak as of some "today", in the schedule's own unit — days for
 * a daily habit, weeks for a weekly one.
 *
 * [previous] and [brokenOn] carry what the Today view needs to render a break
 * honestly: a lost run shows its old length beside the zero rather than simply
 * vanishing (docs/ux/today-view.md §5). They describe the *same* break that
 * [current] being zero reports, so exactly one of the two states is live:
 * either [current] is positive and there is no break to describe, or [current]
 * is zero and [previous]/[brokenOn] say what was lost and when.
 *
 * A habit with no completions at all is [NONE] — not a break, just nothing yet.
 *
 * Produced by [Streaks.snapshot], which is pure in the completion set, so this
 * survives a projection rebuild unchanged.
 */
data class StreakSnapshot(val current: Int, val previous: Int, val brokenOn: LocalDate?) {

    companion object {
        /** No completions yet: nothing running, nothing broken. */
        val NONE = StreakSnapshot(current = 0, previous = 0, brokenOn = null)
    }
}
