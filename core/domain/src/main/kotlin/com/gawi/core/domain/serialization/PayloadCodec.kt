package com.gawi.core.domain.serialization

import com.gawi.core.domain.event.EventPayload
import kotlinx.serialization.json.Json

/**
 * Per-type codec. `decode` dispatches on the stored schema version and
 * upcasts old shapes to the current domain type — a version's decoder is
 * never deleted, so a years-old log always replays (architecture §3).
 *
 * An upcast that cannot make sense of a stored payload must fail with
 * `require` or `check`. [EventCodec] funnels exactly those into
 * [EventCodecException] so callers have one exception to catch; anything
 * thrown outside that set escapes the funnel and breaks the promise.
 */
internal interface PayloadCodec<T : EventPayload> {
    val type: String
    val currentVersion: Int

    fun encode(payload: T): EncodedPayload

    fun decode(schemaVersion: Int, json: String): T
}

/**
 * One shared configuration for all wire JSON. `ignoreUnknownKeys` lets a
 * rolled-back build read the known fields of rows written by a newer one.
 */
internal val wireJson = Json { ignoreUnknownKeys = true }
