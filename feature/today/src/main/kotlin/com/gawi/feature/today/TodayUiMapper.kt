package com.gawi.feature.today

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.model.toMoodState
import com.gawi.core.domain.mascot.Mascot
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.mascot.MoodInputs
import com.gawi.core.domain.model.Schedule
import com.gawi.core.ui.streak.toUi
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
    // Mapped once and shared: both rules read it, and building it twice would
    // be two mappings of the same rows for one screen.
    val inputs = moodInputs()
    val mood = Mascot.mood(inputs)
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
        regeneratingHabit = if (mood == Mood.REGENERATING) regeneratingHabitName(inputs, live) else null,
    )
}

/**
 * The name the regenerating line uses: the most recently broken habit's
 * (docs/ux/today-view.md §6), resolved here rather than in the composable
 * because which habit is a decision and this file is where decisions are
 * asserted without a device.
 *
 * Gated on the mood by the caller, not here. `Mascot.recentlyBrokenHabits`
 * answers whether anything broke; whether that is worth saying is the
 * precedence table's answer, and it already gave it.
 *
 * Null when nothing broke, or when the id names no live row. Neither is
 * reachable from the one call site: it runs only under the mood gate, and a mood
 * of `REGENERATING` means the rule found a break, while the id always resolves
 * because the rule drops archived habits with the same filter over the same list
 * that produced the rows. Returned rather than thrown anyway, because a lookup
 * that throws does it on a screen, and because that makes
 * `today_mood_regenerating` a default the panel can still fall back to rather
 * than a branch that cannot compile away.
 */
private fun regeneratingHabitName(inputs: MoodInputs, live: List<TodayHabit>): String? {
    val id = Mascot.recentlyBrokenHabits(inputs).firstOrNull() ?: return null
    return live.firstOrNull { it.habit.id == id }?.habit?.name
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
