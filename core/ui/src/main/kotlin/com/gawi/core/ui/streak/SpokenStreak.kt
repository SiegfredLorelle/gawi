package com.gawi.core.ui.streak

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.gawi.core.ui.R

/**
 * What a streak *says*, in place of whatever it draws — or null for
 * [StreakUi.None], which draws nothing to describe.
 *
 * The drawn forms are each surface's own: a bare `3` on a Today row, `3w` over a
 * caption on habit detail. A screen reader cannot tell those apart, and TalkBack
 * 17 read a Today badge as *"1"*, *"7"* and *"1 w"* (docs/running.md §4,
 * 2026-09-02). The spoken form says the unit — *"3 days in a row"*, *"1 week in
 * a row"*, *"Streak broken, was 12 days"* — and it is here rather than in either
 * feature because both draw it (AGENTS.md's `:core:ui` rule, caught at the
 * second copy this time rather than the third). The widget keeps its own
 * `spokenLabel`: its rows carry nothing but a name and a number, so *"7 days"*
 * is enough there and the phrase would be noise.
 *
 * A caller puts this on a node with `clearAndSetSemantics`, not `semantics`: on
 * that TalkBack a node carrying both text and a description is read twice.
 */
@Composable
fun spokenStreak(streak: StreakUi): String? = when (streak) {
    StreakUi.None -> null

    is StreakUi.Days -> pluralStringResource(R.plurals.ui_streak_days_spoken, streak.count, streak.count)

    is StreakUi.Weeks -> pluralStringResource(R.plurals.ui_streak_weeks_spoken, streak.count, streak.count)

    is StreakUi.Broken -> pluralStringResource(
        if (streak.weekly) R.plurals.ui_streak_broken_weeks_spoken else R.plurals.ui_streak_broken_days_spoken,
        streak.previous,
        streak.previous,
    )
}
