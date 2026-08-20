package com.gawi.core.domain.serialization.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/*
 * Version 1 of the export envelope. FROZEN on the same terms as the payload
 * wire shapes next door: once a version has been written to a file somebody
 * kept, its DTO is never edited again. A change means an ExportEnvelopeV2
 * beside this one and a second branch in the reader.
 *
 * Two version numbers travel in an export and they mean different things.
 * `format_version` here describes the *envelope* — how to find the events.
 * `schema_version` on each entry describes one *payload* and belongs to
 * EventCodec, which has upcast it on read since the log was built. A v2
 * envelope full of v1 payloads is an ordinary file, and so is the reverse.
 *
 * Field names are snake_case to match both the wire shapes and the events
 * table's columns; instants are ISO-8601 strings and ids canonical UUIDs.
 */

@Serializable
internal data class ExportEnvelopeV1(
    val format: String,
    @SerialName("format_version") val formatVersion: Int,
    @SerialName("exported_at") val exportedAt: String,
    @SerialName("app_version") val appVersion: String,
    @SerialName("event_count") val eventCount: Int,
    val events: List<ExportedEventV1>,
)

@Serializable
internal data class ExportedEventV1(
    val id: String,
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("tz_offset_min") val tzOffsetMin: Int,
    val type: String,
    @SerialName("schema_version") val schemaVersion: Int,
    // A JsonObject, not a String. The payload is nested as real JSON so the
    // file is one a reader or `jq` can walk, which is what the PRD's "open
    // formats" promise is worth. It is also never routed through a payload
    // wire DTO in either direction, so unknown keys survive the round trip.
    val payload: JsonObject,
)

/** The marker that says a file is ours before anything else is read off it. */
internal const val EXPORT_FORMAT = "gawi.event-log"

/** The envelope version this build writes, and the only one it reads. */
internal const val EXPORT_FORMAT_VERSION = 1

/**
 * The JSON configuration for export files, and deliberately not `wireJson`.
 *
 * Pretty-printed, because an export is a document somebody opens, greps and
 * pipes through `jq` — the "open formats" half of the PRD's data-ownership
 * promise — whereas a wire payload is a database column nobody looks at.
 * `ignoreUnknownKeys` for the same reason it is set next door: a field a newer
 * build added to the envelope must not stop this one reading the rest.
 */
internal val exportJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}
