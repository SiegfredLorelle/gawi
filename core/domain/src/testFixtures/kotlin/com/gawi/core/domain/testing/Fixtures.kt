package com.gawi.core.domain.testing

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.event.EventPayload
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitMetadata
import java.time.Instant
import java.time.LocalDate

/*
 * Deterministic ids, envelopes and payloads for every module's tests.
 *
 * Test fixtures rather than test sources, so the same builders reach :core:data
 * directly and every Android module through :core:testing, which re-exports
 * them. This module is pure JVM and cannot depend on an Android library, which
 * is why these live here and the read-model builders live there.
 */

/** Deterministic canonical UUID whose numeric tail is [n] — ordered like n. */
fun uuid(n: Int): String = "00000000-0000-7000-8000-" + n.toString(HEX).padStart(UUID_TAIL_DIGITS, '0')

private const val HEX = 16
private const val UUID_TAIL_DIGITS = 12

fun eventId(n: Int): EventId = EventId(uuid(n))

fun habitId(n: Int): HabitId = HabitId(uuid(n))

fun event(id: Int, atMillis: Long, payload: EventPayload): Event =
    Event(eventId(id), Instant.ofEpochMilli(atMillis), tzOffsetMin = 0, payload = payload)

/**
 * An event at a real instant, in a real offset.
 *
 * Separate from [event] rather than a defaulted parameter on it: the offset only
 * matters where a date is derived from the envelope, which is one register in
 * `Projector`, and epoch millis are unreadable at the scale that test needs.
 */
fun eventAt(id: Int, at: String, tzOffsetMin: Int, payload: EventPayload): Event =
    Event(eventId(id), Instant.parse(at), tzOffsetMin = tzOffsetMin, payload = payload)

fun habitCreated(habit: HabitId, name: String = "read", schedule: Schedule = Schedule.Daily, tag: String? = null): HabitCreated =
    HabitCreated(habit, name, icon = "book", color = "#aabbcc", schedule = schedule, tag = tag)

fun habitUpdated(habit: HabitId, name: String = "read", schedule: Schedule = Schedule.Daily, tag: String? = null): HabitUpdated =
    HabitUpdated(habit, name, icon = "book", color = "#aabbcc", schedule = schedule, tag = tag)

fun completionAdded(habit: HabitId, date: String, note: String? = null): CompletionAdded =
    CompletionAdded(habit, LocalDate.parse(date), note)

fun metadata(name: String = "read", schedule: Schedule = Schedule.Daily, tag: String? = null): HabitMetadata =
    HabitMetadata(name = name, icon = "book", color = "#aabbcc", schedule = schedule, tag = tag)
