package com.gawi.core.data.model

import com.gawi.core.domain.mascot.HabitMoodState
import com.gawi.core.domain.mascot.MoodInputs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One reading of the Today view: the rows, the logical date they were queried
 * for, and the wall clock and thresholds the mascot's mood is decided against.
 *
 * Shaped field for field like [MoodInputs], with [TodayHabit] where that has
 * [HabitMoodState], because that is what it is — the rows a screen draws plus
 * the four values `:core:domain` cannot see for itself.
 *
 * Emitting this rather than a bare list is what makes the mood and the rows one
 * observation. Two flows would let a screen hold rows for the 20th beside a
 * mood decided for the 19th, which is the disagreement [MoodInputs.today] is
 * documented to prevent and cannot prevent on its own if the two lists come
 * from different collections.
 *
 * It also gives the UI the one thing it otherwise has no honest way to know.
 * [TodayHabit] carries no date, and deriving one above this layer would need a
 * clock, a zone and the cutoff — so a tap would resolve its own "today" a
 * moment after the query resolved its, and at the cutoff the two disagree. The
 * date a tap writes to is this one, the date its row was drawn for.
 */
data class TodaySnapshot(
    val habits: List<TodayHabit>,
    val today: LocalDate,
    val now: LocalDateTime,
    val reminderTime: LocalTime,
    val dayCutoff: LocalTime,
    val weekStart: DayOfWeek,
) {

    /**
     * The one construction site [MoodInputs] is documented to have.
     *
     * `:core:domain` may not see [com.gawi.core.data.settings.UserSettings], so
     * copying the three settings across is this layer's job, and doing it in one
     * named place is what makes "field for field with no recomputation" a claim
     * a reader can check.
     */
    fun moodInputs(): MoodInputs = MoodInputs(
        habits = habits.map { it.toMoodState() },
        today = today,
        now = now,
        reminderTime = reminderTime,
        dayCutoff = dayCutoff,
        weekStart = weekStart,
    )
}

/**
 * The mood's view of one row — the mapping [HabitMoodState] documents, with
 * nothing recomputed.
 *
 * Public and separate from [TodaySnapshot.moodInputs] because the Today view
 * needs it per row to ask `Mascot.isOutstanding` for its remaining count. The
 * alternative is indexing into `moodInputs().habits` in parallel with
 * `snapshot.habits`, which is correct today and silently wrong the first time
 * either list is filtered.
 *
 * [TodayHabit.weekCount] already counts today's completion, which is what
 * [HabitMoodState.completionsThisWeek] requires — the two agree by accident of
 * neither, and `TodayMoodTest` pins it.
 */
fun TodayHabit.toMoodState(): HabitMoodState = HabitMoodState(
    schedule = habit.schedule,
    archived = habit.archived,
    completedToday = completedToday,
    completionsThisWeek = weekCount,
    streak = streak,
)
