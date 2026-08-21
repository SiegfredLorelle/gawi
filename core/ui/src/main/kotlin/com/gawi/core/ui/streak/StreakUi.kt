package com.gawi.core.ui.streak

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot

/**
 * A streak as it is drawn.
 *
 * Sealed, and split by unit, because docs/ux/today-view.md §5 says a daily
 * streak is a count of days and a weekly one is a count of weeks and "the two
 * must never be styled as the same number". Making them different types is what
 * enforces that through exhaustiveness rather than through a convention someone
 * forgets.
 *
 * Shared rather than per-feature, unlike `HabitsMessage` and `TodayMessage`:
 * those are duplicated because each is a statement about its own screen, and
 * "days versus weeks versus broken" is not. PRD §6.6 puts a streak on two
 * surfaces, the Today view and habit detail, and the rule deciding which number
 * they show has to be one rule or the two will drift.
 *
 * What is **not** shared is the rendering. This module has no `res/` by design
 * — `Notice` takes strings rather than ids for that reason — and the two
 * surfaces want different treatments anyway: a compact trailing badge on a
 * Today row, a header on detail. Each feature draws this its own way from its
 * own strings.
 */
sealed interface StreakUi {

    /** No completions ever — nothing to draw. */
    data object None : StreakUi

    data class Days(val count: Int) : StreakUi

    data class Weeks(val count: Int) : StreakUi

    /** Zero now, with what was lost kept as context — §5's "was 4". */
    data class Broken(val previous: Int, val weekly: Boolean) : StreakUi
}

/**
 * §5's two streak rules, both of which fall out of the branch order.
 *
 * A positive `current` always renders, so an unfinished day still shows its
 * live streak and a row unchecked at 09:00 never reads `0` — per
 * `Streaks.dayStreak`, an unfinished current day has not broken anything, it
 * has simply not extended it. And a break always keeps `previous`, so "was 4"
 * is available whenever there is a break to describe.
 *
 * The cases are exhaustive rather than defensive: [StreakSnapshot] documents
 * that exactly one of its two states is live, so a positive `current` never
 * carries a break and a zero one carries `brokenOn` unless nothing has ever
 * happened.
 */
fun StreakSnapshot.toUi(schedule: Schedule): StreakUi = when {
    current > 0 && schedule is Schedule.Weekly -> StreakUi.Weeks(current)
    current > 0 -> StreakUi.Days(current)
    brokenOn != null -> StreakUi.Broken(previous = previous, weekly = schedule is Schedule.Weekly)
    else -> StreakUi.None
}
