package com.gawi.core.domain.serialization.export

import com.gawi.core.domain.serialization.EventCodec
import kotlinx.serialization.json.jsonObject

/**
 * The event log as an export file, and back.
 *
 * The single entry point between a stored log and the JSON a user keeps
 * (PRD §5, architecture §6) — the same job [EventCodec] does for one payload
 * and a database column, one level up. Like that one, its API is strings and
 * domain types, so no serialization library leaks out of this package.
 *
 * **This lives in `:core:domain`, and that is what keeps the payload opaque
 * elsewhere.** Embedding a payload in the file as real nested JSON means
 * calling a parser on `EncodedPayload.json`, i.e. knowing that the domain's
 * payload string is JSON at all. Keeping that knowledge in the one package
 * that already has it is why `:core:data` needs no serialization dependency —
 * so that dependency ever appearing there is a signal this boundary was
 * crossed, which is a better guard than a comment in a build file.
 *
 * **Byte-exactness is an explicit non-goal.** Re-serialising JSON is not the
 * identity on arbitrary text — an escaped `\u0041` comes back as a plain `A` —
 * so a golden-file test pinning bytes would turn a formatting decision into a
 * test failure. What is guaranteed, and tested, is that the events survive:
 * the same ids, the same instants to the millisecond, and the same payload
 * keys — including keys this build does not know about.
 */
class EventLogCodec(payloads: EventCodec) {

    private val reader = ExportReader(payloads)

    /**
     * The whole log as an export envelope.
     *
     * Payloads are copied, never decoded, so a log holding an event type this
     * build does not know — or a payload from a newer schema version — still
     * exports. That is not a corner case to tolerate but the point: a log this
     * build cannot fully read is the one a user most needs to get off the
     * device. The only thing that fails here is payload text that is not JSON
     * at all, which no writer can produce and which means the database itself
     * is damaged.
     */
    fun encode(events: List<EncodedEvent>, meta: ExportMeta): String {
        val envelope = ExportEnvelopeV1(
            format = EXPORT_FORMAT,
            formatVersion = EXPORT_FORMAT_VERSION,
            exportedAt = meta.exportedAt.toString(),
            appVersion = meta.appVersion,
            eventCount = events.size,
            events = events.map { it.toWire() },
        )
        return exportJson.encodeToString(envelope)
    }

    /**
     * Reads an export file, whole or not at all.
     *
     * Every event is decoded once to prove this build can read it and the
     * result is discarded; what comes back is the file's own payload text. See
     * [ExportReader] for why the version is checked before the envelope is,
     * and why one bad event refuses the lot.
     */
    fun decode(text: String): ExportRead = reader.read(text)

    private fun EncodedEvent.toWire() = ExportedEventV1(
        id = id.value,
        occurredAt = occurredAt.toString(),
        tzOffsetMin = tzOffsetMin,
        type = payload.type,
        schemaVersion = payload.schemaVersion,
        payload = exportJson.parseToJsonElement(payload.json).jsonObject,
    )
}
