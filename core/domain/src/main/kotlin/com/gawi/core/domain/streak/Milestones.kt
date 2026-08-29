package com.gawi.core.domain.streak

/**
 * The streak milestones the PRD (§5) celebrates, and the rule for when one is
 * reached.
 *
 * Two ladders, one per unit, because a streak is counted in the schedule's own
 * unit ([StreakSnapshot.current] is days for a daily habit and weeks for a
 * weekly one) and docs/ux/today-view.md §5 forbids the two ever reading as the
 * same number — so "7" is a rung for days and no rung at all for weeks. Here
 * rather than in a feature because more than one surface may want "reached a
 * rung today" or "the next rung is 30", and a rule copied is a rule that drifts.
 *
 * The numbers are the PRD's; naming each would name it after itself.
 */
@Suppress("MagicNumber")
object Milestones {
    /** Consecutive days, for a daily habit. */
    val DAYS: List<Int> = listOf(7, 30, 100)

    /** Consecutive weeks hitting the target, for a weekly habit. */
    val WEEKS: List<Int> = listOf(4, 12, 52)

    /** The ladder for a schedule's unit. */
    fun ladder(weekly: Boolean): List<Int> = if (weekly) WEEKS else DAYS

    /**
     * The rung a streak going from [from] to [to] crosses on the unit's ladder,
     * or null: `from < m <= to` for some rung m, and the **largest** such m, so
     * a retro fill that takes a habit from 6 to 31 reached 30, not 7. A count
     * that stays put, grows between rungs or falls crosses nothing; a streak
     * that came from nothing or from a break has a [from] of zero.
     */
    fun crossed(from: Int, to: Int, weekly: Boolean): Int? = ladder(weekly).lastOrNull { from < it && it <= to }
}
