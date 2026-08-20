package com.gawi.feature.habits.testsupport

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot

/**
 * Habits as the repository would hand them over. Named after the `Fixtures.kt`
 * the other modules use, and equally deliberately not shared: this one builds
 * `HabitState`, where Today's builds read-model rows and the core modules' build
 * events and commands.
 */
fun habitId(n: Int): HabitId = HabitId("00000000-0000-7000-8000-" + n.toString(16).padStart(12, '0'))

/**
 * Suppressed at the declaration: a fixture builder's parameters are its whole
 * point. Every one is defaulted, so a test names only the field it is about.
 */
@Suppress("LongParameterList")
fun habitState(
    id: HabitId = habitId(1),
    name: String = "read",
    icon: String = "📖",
    color: String = "#7E57C2",
    schedule: Schedule = Schedule.Daily,
    tag: String? = null,
    archived: Boolean = false,
): HabitState = HabitState(
    id = id,
    name = name,
    icon = icon,
    color = color,
    schedule = schedule,
    tag = tag,
    archived = archived,
)

/** What `observeHabit` returns — the editor reads only its `habit`. */
fun todayHabit(habit: HabitState = habitState()): TodayHabit = TodayHabit(
    habit = habit,
    completedToday = false,
    note = null,
    weekCount = 0,
    streak = StreakSnapshot.NONE,
)
