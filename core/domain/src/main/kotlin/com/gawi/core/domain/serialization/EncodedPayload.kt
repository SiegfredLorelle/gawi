package com.gawi.core.domain.serialization

/**
 * A payload as persisted: the `type`, `schema_version`, and `payload`
 * columns of the events table (architecture §3). The discriminator and
 * version live here, never inside the JSON.
 */
data class EncodedPayload(val type: String, val schemaVersion: Int, val json: String)

/** Thrown when a stored payload cannot be decoded: unknown type or schema version, or a corrupt body. */
class EventCodecException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
