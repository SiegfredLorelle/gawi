package com.gawi.core.data.db.mapper

import com.gawi.core.data.db.entity.EventEntity
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.serialization.EventCodec
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
