package com.gawi.core.data.db.mapper

import com.gawi.core.data.db.entity.EventEntity
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.serialization.EncodedPayload
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.serialization.export.EncodedEvent
import java.time.Instant

/**
 * Translation between the domain envelope and its row. This is the only place
 * that knows the events table stores an instant as epoch millis and an id as a
 * string, which is what lets the entity stay a dumb column mirror.
 */
internal fun Event.toEntity(codec: EventCodec): EventEntity {
    val encoded = codec.encode(payload)
    return EventEntity(
        id = id.value,
        type = encoded.type,
        schemaVersion = encoded.schemaVersion,
        occurredAt = occurredAt.toEpochMilli(),
        tzOffsetMin = tzOffsetMin,
        payload = encoded.json,
    )
}

/**
 * Decodes a row. Throws `EventCodecException` on an unknown type or version or
 * a corrupt body, and `IllegalArgumentException` if the stored id is not a
 * canonical UUIDv7 — both deliberately loud. The MVP log has a single writer,
 * so a shape this code cannot read is corruption, not forward compatibility,
 * and silently dropping the row would silently change the user's history.
 */
internal fun EventEntity.toDomain(codec: EventCodec): Event = Event(
    id = EventId(id),
    occurredAt = Instant.ofEpochMilli(occurredAt),
    tzOffsetMin = tzOffsetMin,
    payload = codec.decode(type, schemaVersion, payload),
)

/**
 * A row for an event that arrived from outside — an import today, sync later.
 *
 * The payload text, its type and its schema version are copied verbatim, and
 * this exists **only** so that they are. Reaching for [Event.toEntity] instead
 * would re-encode through the current wire DTO, which upcasts an older payload
 * to the current schema version and drops every key this build does not know:
 * the log migrated in place, which architecture §3 forbids. The two look
 * duplicative and must not be merged.
 */
internal fun EncodedEvent.toEntity(): EventEntity = EventEntity(
    id = id.value,
    type = payload.type,
    schemaVersion = payload.schemaVersion,
    occurredAt = occurredAt.toEpochMilli(),
    tzOffsetMin = tzOffsetMin,
    payload = payload.json,
)

/**
 * A row on its way out to an export. Never decodes, so a log holding an event
 * type this build does not know still exports — which is the log you most need
 * off the device, not a corner case to tolerate.
 *
 * The id is still validated on the way through, because a non-canonical id in
 * *local* storage is corruption rather than foreign input, and this module has
 * been loud about that since the log was built.
 */
internal fun EventEntity.toEncoded(): EncodedEvent = EncodedEvent(
    id = EventId(id),
    occurredAt = Instant.ofEpochMilli(occurredAt),
    tzOffsetMin = tzOffsetMin,
    payload = EncodedPayload(type, schemaVersion, payload),
)
