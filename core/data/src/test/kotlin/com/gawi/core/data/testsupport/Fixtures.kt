package com.gawi.core.data.testsupport

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.event.EventPayload
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitMetadata
import java.time.Instant
import java.time.LocalDate

/**
 * Deterministic ids and envelopes for this module's tests. Deliberately a copy
 * of the shape `:core:domain` uses rather than a shared test-fixtures artifact:
 * the domain's generator manufactures dangling tombstones and ghost habits on
 * purpose, which is exactly what the command path here can never produce, so
 * the two modules want different generators and only these few lines overlap.
 */
fun uuid(n: Int): String = "00000000-0000-7000-8000-" + n.toString(16).padStart(12, '0')

fun eventId(n: Int): EventId = EventId(uuid(n))

fun habitId(n: Int): HabitId = HabitId(uuid(n))

fun event(id: Int, atMillis: Long, payload: EventPayload): Event =
    Event(eventId(id), Instant.ofEpochMilli(atMillis), tzOffsetMin = 0, payload = payload)

fun habitCreated(habit: HabitId, name: String = "read", schedule: Schedule = Schedule.Daily, tag: String? = null): HabitCreated =
    HabitCreated(habit, name, icon = "book", color = "#aabbcc", schedule = schedule, tag = tag)

fun completionAdded(habit: HabitId, date: String, note: String? = null): CompletionAdded =
    CompletionAdded(habit, LocalDate.parse(date), note)

fun metadata(name: String = "read", schedule: Schedule = Schedule.Daily, tag: String? = null): HabitMetadata =
    HabitMetadata(name = name, icon = "book", color = "#aabbcc", schedule = schedule, tag = tag)
