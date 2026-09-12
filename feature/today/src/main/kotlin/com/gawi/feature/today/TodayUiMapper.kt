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
        subject = subject(mood, inputs, live),
    )
}

/**
 * The habit Momo names and carries the gills of (docs/ux/momo.md §3), or null
 * when she has none and is drawn full.
 *
 * **Two moods can name a habit and they name different ones.** Regenerating
 * names the break that just happened; worried names the run closest to
 * becoming the next one. Content and thriving name nobody — there is no risk
 * to report, so the drawing reports none.
 *
 * The count comes from the habit that was named rather than from a second
 * lookup, which is what makes [MascotSubject]'s invariant hold by construction.
 */
private fun subject(mood: Mood, inputs: MoodInputs, live: List<TodayHabit>): MascotSubject? {
    val id = when (mood) {
        Mood.REGENERATING -> Mascot.recentlyBrokenHabits(inputs).firstOrNull()
        Mood.WORRIED -> Mascot.weakestOutstandingHabit(inputs)
        Mood.CONTENT, Mood.THRIVING -> null
    }
    // A null id matches no row, which is the same answer as a row that has gone
    // — and the archived filter above is what keeps the second unreachable.
    return live.firstOrNull { it.habit.id == id }?.let { MascotSubject(it.habit.name, it.streak.spare) }
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
