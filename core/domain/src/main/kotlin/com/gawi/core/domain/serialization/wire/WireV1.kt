package com.gawi.core.domain.serialization.wire

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/*
 * Version 1 wire shapes. These are FROZEN: once a version has been written
 * to a log, its DTO is never edited again. A payload change means a new
 * WireV2 DTO beside these, `currentVersion = 2` in the codec, and one new
 * decode branch whose v1 mapper supplies defaults for the new fields —
 * that mapping IS the upcast (architecture §3, "upcast on read").
 *
 * Field names are snake_case to match the events table's column style;
 * dates travel as ISO-8601 strings; ids as canonical UUID strings.
 */

@Serializable
internal data class ScheduleWireV1(val kind: String, @SerialName("times_per_week") val timesPerWeek: Int? = null) {
    fun toDomain(): Schedule = when (kind) {
        KIND_DAILY -> Schedule.Daily
        KIND_WEEKLY -> Schedule.Weekly(requireNotNull(timesPerWeek) { "weekly schedule without times_per_week" })
        else -> throw IllegalArgumentException("unknown schedule kind: $kind")
    }

    companion object {
        const val KIND_DAILY = "daily"
        const val KIND_WEEKLY = "weekly"

        fun of(schedule: Schedule): ScheduleWireV1 = when (schedule) {
            is Schedule.Daily -> ScheduleWireV1(KIND_DAILY)
            is Schedule.Weekly -> ScheduleWireV1(KIND_WEEKLY, schedule.timesPerWeek)
        }
    }
}

@Serializable
internal data class HabitCreatedWireV1(
    @SerialName("habit_id") val habitId: String,
    val name: String,
    val icon: String,
    val color: String,
    val schedule: ScheduleWireV1,
    val tag: String? = null,
) {
    fun toDomain() = HabitCreated(HabitId(habitId), name, icon, color, schedule.toDomain(), tag)
}

internal fun HabitCreated.toWireV1() = HabitCreatedWireV1(habitId.value, name, icon, color, ScheduleWireV1.of(schedule), tag)

@Serializable
internal data class HabitUpdatedWireV1(
    @SerialName("habit_id") val habitId: String,
    val name: String,
    val icon: String,
    val color: String,
    val schedule: ScheduleWireV1,
    val tag: String? = null,
) {
    fun toDomain() = HabitUpdated(HabitId(habitId), name, icon, color, schedule.toDomain(), tag)
}

internal fun HabitUpdated.toWireV1() = HabitUpdatedWireV1(habitId.value, name, icon, color, ScheduleWireV1.of(schedule), tag)

@Serializable
internal data class HabitArchivedWireV1(@SerialName("habit_id") val habitId: String) {
    fun toDomain() = HabitArchived(HabitId(habitId))
}

internal fun HabitArchived.toWireV1() = HabitArchivedWireV1(habitId.value)

@Serializable
internal data class HabitUnarchivedWireV1(@SerialName("habit_id") val habitId: String) {
    fun toDomain() = HabitUnarchived(HabitId(habitId))
}

internal fun HabitUnarchived.toWireV1() = HabitUnarchivedWireV1(habitId.value)

@Serializable
internal data class CompletionAddedWireV1(
    @SerialName("habit_id") val habitId: String,
    @SerialName("logical_date") val logicalDate: String,
    val note: String? = null,
) {
    fun toDomain() = CompletionAdded(HabitId(habitId), LocalDate.parse(logicalDate), note)
}

internal fun CompletionAdded.toWireV1() = CompletionAddedWireV1(habitId.value, logicalDate.toString(), note)

@Serializable
internal data class CompletionTombstonedWireV1(@SerialName("completion_event_id") val completionEventId: String) {
    fun toDomain() = CompletionTombstoned(EventId(completionEventId))
}

internal fun CompletionTombstoned.toWireV1() = CompletionTombstonedWireV1(completionEventId.value)

@Serializable
internal data class CompletionNoteUpdatedWireV1(@SerialName("completion_event_id") val completionEventId: String, val text: String) {
    fun toDomain() = CompletionNoteUpdated(EventId(completionEventId), text)
}

internal fun CompletionNoteUpdated.toWireV1() = CompletionNoteUpdatedWireV1(completionEventId.value, text)
