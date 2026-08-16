package com.gawi.core.domain.event

import com.gawi.core.domain.id.EventId
import java.time.Instant

/**
 * Envelope around a payload, mirroring the events table columns
 * (architecture §3). [tzOffsetMin] is the device UTC offset at write time,
 * kept for audit only — projection never reads it.
 */
data class Event(val id: EventId, val occurredAt: Instant, val tzOffsetMin: Int, val payload: EventPayload)
