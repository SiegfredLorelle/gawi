package com.gawi.core.domain.streak

import com.gawi.core.domain.mascot.Mascot
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
 * **A non-null [brokenOn] therefore means nothing was completed at or after it**,
 * and that is what delivers today-view §4's mended-habit exit with no rule of
 * its own: a later completion would have restarted the run, and a run that then
 * broke again would carry the later date. So a caller asking "is this habit
 * still broken" never has to ask "has it been mended since" as a second
 * question. [Mascot.recentlyBroken] leans on this; a change here that let a
 * break outlive its repair would break it silently.
 *
 * A habit with no completions at all is [NONE] — not a break, just nothing yet.
 *
 * Produced by [Streaks.snapshot], which is pure in the completion set, so this
 * survives a projection rebuild unchanged.
 */
data class StreakSnapshot(
    val current: Int,
    val previous: Int,
    val brokenOn: LocalDate?,
    /**
     * Spare lives left in this run, 0..[Streaks.MAX_SPARE] — the gills Momo
     * draws (docs/ux/momo.md §3).
     *
     * **Zero whenever [current] is**, and not by coincidence: a run can only
     * break once the last spare is spent, so a broken streak has none left by
     * construction. That is what makes "zero spare" and "regenerating" one
     * moment seen twice, which is the agreement momo.md §3 draws the face on.
     */
    val spare: Int,
) {

    companion object {
        /** No completions yet: nothing running, nothing broken, nothing spare. */
        val NONE = StreakSnapshot(current = 0, previous = 0, brokenOn = null, spare = 0)
    }
}
