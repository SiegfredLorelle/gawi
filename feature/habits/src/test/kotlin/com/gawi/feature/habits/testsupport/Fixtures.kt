package com.gawi.feature.habits.testsupport

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot
import java.time.LocalDate

/**
 * Habits as the repository would hand them over. Named after the `Fixtures.kt`
 * the other modules use, and equally deliberately not shared: this one builds
 * `HabitState`, where Today's builds read-model rows and the core modules' build
 * events and commands.
 */
/**
 * The logical date the detail tests read against.
 *
 * A Tuesday, matching docs/ux/today-view.md §5's worked example — with today at
 * Tue 19, Sat 16 is the oldest open day and Fri 15 is the one drawn shut.
 */
val TODAY: LocalDate = LocalDate.parse("2026-08-18")

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

/**
 * What `observeHabit` returns.
 *
 * The editor reads only its `habit`; detail reads all of it, which is why the
 * completion, week and streak fields are parameters rather than fixed zeroes.
 */
fun todayHabit(
    habit: HabitState = habitState(),
    completedToday: Boolean = false,
    note: String? = null,
    weekCount: Int = 0,
    streak: StreakSnapshot = StreakSnapshot.NONE,
): TodayHabit = TodayHabit(
    habit = habit,
    completedToday = completedToday,
    note = note,
    weekCount = weekCount,
    streak = streak,
)

/** A live run of [current], in whatever unit the habit's schedule counts in. */
fun running(current: Int): StreakSnapshot = StreakSnapshot(current = current, previous = 0, brokenOn = null)

/** A run of [previous] that has since been lost, and reads zero as of [brokenOn]. */
fun broken(previous: Int, brokenOn: LocalDate = LocalDate.parse("2026-08-16")): StreakSnapshot =
    StreakSnapshot(current = 0, previous = previous, brokenOn = brokenOn)
