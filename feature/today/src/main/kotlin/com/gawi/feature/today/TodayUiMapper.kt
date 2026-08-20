package com.gawi.feature.today

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.model.toMoodState
import com.gawi.core.domain.mascot.Mascot
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.ui.theme.parseHabitColor

/**
 * The read model as the screen draws it — docs/ux/today-view.md §5's rules, in
 * the one place they can be asserted without a device.
 *
 * Here rather than in the composables because these are decisions, not layout:
 * which unit a streak is counted in, whether a habit is still outstanding, what
 * an unparseable colour falls back to. A composable can get those wrong only in
 * a screenshot; a function gets them wrong in a test.
 */
internal fun TodaySnapshot.toUiState(): TodayUiState {
    val mood = Mascot.mood(moodInputs())
    // Filtered once, at the top, so the rows, the count and the face cannot
    // disagree. Mascot.mood drops archived habits itself; doing it here too is
    // what makes that agreement this function's property rather than
    // observeToday's, which filters in SQL — and it is what keeps a detail
    // screen honest when it reuses this. §1's app-bar chip reads the count.
    val live = habits.filterNot { it.habit.archived }
    if (live.isEmpty()) return TodayUiState.Empty(mood)
    return TodayUiState.Habits(
        rows = live.map { it.toRowUi() },
        mood = mood,
        remaining = live.count { Mascot.isOutstanding(it.toMoodState(), today, weekStart) },
        logicalDate = today,
    )
}

internal fun TodayHabit.toRowUi(): HabitRowUi = HabitRowUi(
    id = habit.id,
    name = habit.name,
    icon = habit.icon,
    iconTint = parseHabitColor(habit.color),
    completed = completedToday,
    weekProgress = when (val schedule = habit.schedule) {
        is Schedule.Daily -> null
        is Schedule.Weekly -> WeekProgress(done = weekCount, target = schedule.timesPerWeek)
    },
    streak = streak.toUi(habit.schedule),
)

/**
 * §5's two streak rules, both of which fall out of the branch order.
 *
 * A positive `current` always renders, so an unfinished day still shows its
 * live streak and a row unchecked at 09:00 never reads `0` — per
 * `Streaks.dayStreak`, an unfinished current day has not broken anything, it
 * has simply not extended it. And a break always keeps `previous`, so "was 4"
 * is available whenever there is a break to describe.
 *
 * The cases are exhaustive rather than defensive: `StreakSnapshot` documents
 * that exactly one of its two states is live, so a positive `current` never
 * carries a break and a zero one carries `brokenOn` unless nothing has ever
 * happened.
 */
internal fun StreakSnapshot.toUi(schedule: Schedule): StreakUi = when {
    current > 0 && schedule is Schedule.Weekly -> StreakUi.Weeks(current)
    current > 0 -> StreakUi.Days(current)
    brokenOn != null -> StreakUi.Broken(previous = previous, weekly = schedule is Schedule.Weekly)
    else -> StreakUi.None
}
