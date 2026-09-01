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
    // Built once and shared by both rules, rather than calling moodInputs()
    // twice. Not a claim that the rows are mapped only once — `remaining` below
    // still maps per row, deliberately: TodaySnapshot.toMoodState is public
    // precisely so the count is taken from the rows it counts, rather than by
    // indexing a parallel list that goes silently wrong the first time either
    // side is filtered.
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
 * **Null is reachable, and it is the whole reason the unnamed line still
 * exists.** `Mascot.mood` reads a broken streak as `REGENERATING` whether or not
 * the habit has been ticked today, but `Mascot.recentlyBrokenHabits` will not
 * name a habit already done — so a weekly habit ticked short of its target gives
 * a regenerating mood with nothing to name, and the panel falls back to
 * `today_mood_regenerating`.
 *
 * The other null path — an id naming no live row — stays unreachable, because
 * the rule drops archived habits with the same filter over the same list that
 * produced the rows. Returned rather than thrown either way: a lookup that
 * throws does it on a screen.
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
