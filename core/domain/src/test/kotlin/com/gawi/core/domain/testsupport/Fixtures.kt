package com.gawi.core.domain.testsupport

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.event.EventPayload
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate

/** Deterministic canonical UUID whose numeric tail is [n] — ordered like n. */
fun uuid(n: Int): String = "00000000-0000-7000-8000-" + n.toString(16).padStart(12, '0')

fun eventId(n: Int): EventId = EventId(uuid(n))

fun habitId(n: Int): HabitId = HabitId(uuid(n))

fun event(id: Int, atMillis: Long, payload: EventPayload): Event =
    Event(eventId(id), Instant.ofEpochMilli(atMillis), tzOffsetMin = 0, payload = payload)

fun habitCreated(habit: HabitId, name: String = "read", schedule: Schedule = Schedule.Daily, tag: String? = null): HabitCreated =
    HabitCreated(habit, name, icon = "book", color = "#aabbcc", schedule = schedule, tag = tag)

fun habitUpdated(habit: HabitId, name: String = "read", schedule: Schedule = Schedule.Daily, tag: String? = null): HabitUpdated =
    HabitUpdated(habit, name, icon = "book", color = "#aabbcc", schedule = schedule, tag = tag)

fun completionAdded(habit: HabitId, date: String, note: String? = null): CompletionAdded =
    CompletionAdded(habit, LocalDate.parse(date), note)
