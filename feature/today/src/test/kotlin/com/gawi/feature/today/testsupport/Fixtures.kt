package com.gawi.feature.today.testsupport

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Rows and snapshots as the repository would hand them over. Named after the
 * `Fixtures.kt` both core modules already use, and equally deliberately not
 * shared with them: this one builds read-model output, where theirs build
 * events and commands.
 */
fun habitId(n: Int): HabitId = HabitId("00000000-0000-7000-8000-" + n.toString(16).padStart(12, '0'))

/**
 * Suppressed at the declaration: a fixture builder's parameters are its whole
 * point. Every one of them is defaulted, so a test names only the field it is
 * about and the list never appears at a call site.
 */
@Suppress("LongParameterList")
fun todayHabit(
    id: HabitId = habitId(1),
    name: String = "read",
    icon: String = "book",
    color: String = "#aabbcc",
    schedule: Schedule = Schedule.Daily,
    tag: String? = null,
    archived: Boolean = false,
    completedToday: Boolean = false,
    note: String? = null,
    weekCount: Int = 0,
    streak: StreakSnapshot = StreakSnapshot.NONE,
): TodayHabit = TodayHabit(
    habit = HabitState(
        id = id,
        name = name,
        icon = icon,
        color = color,
        schedule = schedule,
        tag = tag,
        archived = archived,
    ),
    completedToday = completedToday,
    note = note,
    weekCount = weekCount,
    streak = streak,
)

val TODAY: LocalDate = LocalDate.parse("2026-08-17")

/**
 * Suppressed at the declaration: a fixture builder's parameters are its whole
 * point. Every one of them is defaulted, so a test names only the field it is
 * about and the list never appears at a call site.
 */
@Suppress("LongParameterList")
fun todaySnapshot(
    habits: List<TodayHabit> = emptyList(),
    today: LocalDate = TODAY,
    now: LocalDateTime = today.atTime(9, 0),
    reminderTime: LocalTime = LocalTime.of(21, 0),
    dayCutoff: LocalTime = LocalTime.MIDNIGHT,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): TodaySnapshot = TodaySnapshot(habits, today, now, reminderTime, dayCutoff, weekStart)
