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
 * [brokenOn] is the first day — or, for a weekly habit, the first week start —
 * on which [current] reads zero, which is the day the break becomes visible
 * rather than the day the completion was missed. Those are not the same day:
 * an unfinished day does not break a streak, so a miss on Friday still shows a
 * live run all Saturday and only reads zero on Sunday. Dating it from when it
 * reads zero is what lets a caller ask "did this break just now" by comparing
 * against today, and it is never in the future.
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
