package com.gawi.widget.testsupport

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Read-model rows as the repository hands them over.
 *
 * A third `Fixtures.kt`, and per the precedent the other two set, deliberately
 * not shared: a test fixture is part of the test, and a shared one grows
 * parameters for callers it cannot see.
 */
fun habitId(n: Int): HabitId = HabitId("00000000-0000-7000-8000-" + n.toString(16).padStart(12, '0'))

/** Suppressed at the declaration: a fixture builder's parameters are its point. */
@Suppress("LongParameterList")
fun todayHabit(
    id: HabitId = habitId(1),
    name: String = "read",
    archived: Boolean = false,
    completedToday: Boolean = false,
    weekCount: Int = 0,
    streak: StreakSnapshot = StreakSnapshot.NONE,
    schedule: Schedule = Schedule.Daily,
): TodayHabit = TodayHabit(
    habit = HabitState(
        id = id,
        name = name,
        icon = "book",
        color = "#aabbcc",
        schedule = schedule,
        tag = null,
        archived = archived,
        // Unknown, because nothing the widget draws asks when a habit started.
        createdOn = null,
    ),
    completedToday = completedToday,
    note = null,
    weekCount = weekCount,
    streak = streak,
)

/**
 * Deliberately not today's real date. Every assertion about which logical date a
 * tap writes to is only worth something if `LocalDate.now()` would give a
 * different answer — the same reason `FakeDeviceClock` has to be moved off UTC.
 */
val FIXED_DATE: LocalDate = LocalDate.parse("2026-08-17")

fun todaySnapshot(habits: List<TodayHabit> = emptyList(), today: LocalDate = FIXED_DATE): TodaySnapshot = TodaySnapshot(
    habits = habits,
    today = today,
    now = today.atTime(9, 0),
    reminderTime = LocalTime.of(21, 0),
    dayCutoff = LocalTime.MIDNIGHT,
    weekStart = DayOfWeek.MONDAY,
)
