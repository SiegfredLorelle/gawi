package com.gawi.core.data.db.mapper

import com.gawi.core.data.db.entity.CompletionEntity
import com.gawi.core.data.db.entity.HabitEntity
import com.gawi.core.data.db.entity.HabitStreakEntity
import com.gawi.core.data.db.entity.TodayHabitRow
import com.gawi.core.data.model.TodayHabit
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.CompletionKey
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot
import java.time.LocalDate

/**
 * Translation between the derived tables and the shapes above this module.
 *
 * The schedule vocabulary matches the wire format's on purpose: one spelling
 * of "daily" and "weekly" in the repository, whatever is reading it.
 */
internal const val SCHEDULE_DAILY = "daily"
internal const val SCHEDULE_WEEKLY = "weekly"

internal fun Schedule.toKind(): String = when (this) {
    is Schedule.Daily -> SCHEDULE_DAILY
    is Schedule.Weekly -> SCHEDULE_WEEKLY
}

internal fun Schedule.toTimesPerWeek(): Int? = when (this) {
    is Schedule.Daily -> null
    is Schedule.Weekly -> timesPerWeek
}

/**
 * Rebuilds a schedule from its two columns. A weekly row with no target is
 * corruption rather than a default worth guessing at, and `Schedule.Weekly`
 * would reject the guess anyway.
 */
internal fun schedule(kind: String, timesPerWeek: Int?): Schedule = when (kind) {
    SCHEDULE_DAILY -> Schedule.Daily

    SCHEDULE_WEEKLY -> Schedule.Weekly(
        requireNotNull(timesPerWeek) { "weekly habit row has no times_per_week" },
    )

    else -> error("unknown schedule kind: $kind")
}

internal fun HabitState.toEntity(): HabitEntity = HabitEntity(
    habitId = id.value,
    name = name,
    icon = icon,
    color = color,
    scheduleKind = schedule.toKind(),
    timesPerWeek = schedule.toTimesPerWeek(),
    tag = tag,
    archived = archived,
)

internal fun HabitEntity.toDomain(): HabitState = HabitState(
    id = HabitId(habitId),
    name = name,
    icon = icon,
    color = color,
    schedule = schedule(scheduleKind, timesPerWeek),
    tag = tag,
    archived = archived,
)

internal fun CompletionKey.toEntity(note: String?): CompletionEntity = CompletionEntity(
    habitId = habitId.value,
    logicalDate = logicalDate.toString(),
    note = note,
)

internal fun StreakSnapshot.toEntity(habitId: HabitId, computedFor: LocalDate): HabitStreakEntity = HabitStreakEntity(
    habitId = habitId.value,
    currentStreak = current,
    previousStreak = previous,
    brokenOn = brokenOn?.toString(),
    computedForDate = computedFor.toString(),
)

internal fun TodayHabitRow.toDomain(): TodayHabit = TodayHabit(
    habit = HabitState(
        id = HabitId(habitId),
        name = name,
        icon = icon,
        color = color,
        schedule = schedule(scheduleKind, timesPerWeek),
        tag = tag,
        archived = archived,
    ),
    completedToday = completedToday,
    note = note,
    weekCount = weekCount,
    streak = StreakSnapshot(
        current = currentStreak,
        previous = previousStreak,
        brokenOn = brokenOn?.let(LocalDate::parse),
    ),
)
