package com.gawi.feature.today

import androidx.compose.ui.graphics.Color
import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.model.toMoodState
import com.gawi.core.domain.mascot.Mascot
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot

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
    if (habits.isEmpty()) return TodayUiState.Empty(mood)
    return TodayUiState.Habits(
        rows = habits.map { it.toRowUi() },
        mood = mood,
        // Asked of the same rule the mood asked, from the same snapshot, and
        // filtered the same way, so the count and the face cannot disagree.
        // Mascot.mood drops archived habits itself; leaving them in here would
        // make that agreement observeToday's property rather than this
        // function's, and it would break the day a detail screen reuses this.
        // §1's app-bar chip will read this count.
        remaining = habits.count { !it.habit.archived && Mascot.isOutstanding(it.toMoodState(), today, weekStart) },
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

/**
 * A habit's stored colour, or null if it is not one.
 *
 * `HabitState.color` is an unvalidated string off the event log — no command
 * checks it and no projection normalises it — so a row has to survive anything
 * in there rather than crash the screen. Hand-rolled because
 * `android.graphics.Color.parseColor` would put Robolectric on this module's
 * test classpath for what is a string parse.
 */
internal fun parseHabitColor(hex: String): Color? {
    val digits = hex.removePrefix("#")
    // Every guard as one expression, because a hash, a length and a digit set
    // are three ways of saying the same thing: this either is a colour or is
    // not. toLongOrNull would otherwise accept a leading sign, making "#-abcde"
    // six characters that parse negative and mask into an arbitrary opaque
    // colour rather than falling back to a theme role.
    val argb = when {
        digits.length == hex.length -> null

        !digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } -> null

        // Color(Long) reads 0xAARRGGBB. Color(ULong) is the raw packed encoding
        // and would read these digits as a different colour space entirely.
        digits.length == RGB_DIGITS -> digits.toLongOrNull(radix = HEX_RADIX)?.or(OPAQUE_ALPHA)

        digits.length == ARGB_DIGITS -> digits.toLongOrNull(radix = HEX_RADIX)

        else -> null
    }
    return argb?.let { Color(it) }
}

private const val HEX_RADIX = 16
private const val RGB_DIGITS = 6
private const val ARGB_DIGITS = 8
private const val OPAQUE_ALPHA = 0xFF000000L
