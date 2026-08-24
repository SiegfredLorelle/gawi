package com.gawi.core.domain.event

import com.gawi.core.domain.id.EventId
import java.time.Instant

/**
 * Envelope around a payload, mirroring the events table columns
 * (architecture §3). [tzOffsetMin] is the device UTC offset at write time.
 *
 * **It is read by projection, in exactly one place**, and this comment used to
 * say it never was. `Projector.applyCreation` pairs it with [occurredAt] to
 * derive the calendar date a habit was created on, because `HabitCreated`
 * carries no date and adding one would be a payload schema bump for every
 * client. Both fields are written once and never change, so the derivation is a
 * pure function of immutable log data and every replay produces the same
 * answer — which is the property architecture §5 protects when it says a date
 * is decided once and stored. Nothing else here reads it, and a second reader
 * should have a reason as narrow as that one.
 */
data class Event(val id: EventId, val occurredAt: Instant, val tzOffsetMin: Int, val payload: EventPayload)
