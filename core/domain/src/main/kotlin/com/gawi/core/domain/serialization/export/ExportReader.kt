package com.gawi.core.domain.serialization.export

import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.serialization.EncodedPayload
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.serialization.EventCodecException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Reads an export file, refusing the whole thing rather than part of it.
 *
 * **The version is read before the envelope is decoded**, off the raw JSON
 * object, and that ordering is the point of this class rather than an
 * implementation detail. Decoding first would mean a v2 envelope that renamed
 * a field failed as "unreadable", so a user whose backup is perfectly intact
 * would be told it was damaged. It is the only copy of their history; the
 * message has to be right.
 *
 * **One bad event refuses the file.** A partial import is a valid event log,
 * which is exactly the problem: the user cannot see which events were dropped
 * and cannot get them back. Refusing is recoverable — a merge is idempotent,
 * so a corrected file simply imports again — and a partial import is not. What
 * makes that affordable is that every refusal names the event by position and
 * id, so an open-format file stays repairable by hand.
 *
 * Refusals travel as a private exception and are turned back into a value at
 * [read]. That keeps the validation ladder readable — every step can give up
 * where it stands — while the caller still sees the [ExportRead] that this
 * project's rejections-are-values rule asks for.
 */
internal class ExportReader(private val payloads: EventCodec) {

    fun read(text: String): ExportRead = try {
        ExportRead.Events(readEnvelope(text))
    } catch (refusal: Refusal) {
        ExportRead.Refused(refusal.reason)
    }

    private fun readEnvelope(text: String): List<EncodedEvent> {
        val root = parse(text)
        requireOurFormat(root)
        val envelope = decodeEnvelope(root)
        if (envelope.eventCount != envelope.events.size) {
            malformed("declared ${envelope.eventCount} events but carries ${envelope.events.size}")
        }
        return envelope.events.mapIndexed(::readEvent)
    }

    private fun parse(text: String): JsonObject {
        val element = try {
            exportJson.parseToJsonElement(text)
        } catch (cause: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException, which is
            // also what a malformed literal inside the text arrives as.
            malformed("not JSON: ${cause.message}")
        }
        return element as? JsonObject ?: refuse(ExportRejection.NotAnExport)
    }

    /**
     * Establishes that this is one of ours and that we can read this version,
     * from the raw object and before any DTO is involved.
     */
    private fun requireOurFormat(root: JsonObject) {
        val format = (root[FORMAT_KEY] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != EXPORT_FORMAT) refuse(ExportRejection.NotAnExport)

        // Absent is a different thing from unreadable, and they get different
        // answers. A file claiming our format with no version at all is not
        // ours — every version we have written has had one. A file that *has*
        // one this cannot read is ours and broken, and must not be reported as
        // somebody else's file: a future envelope writing `2.0` would otherwise
        // be turned away with the least useful message available.
        val stated = root[FORMAT_VERSION_KEY] as? JsonPrimitive ?: refuse(ExportRejection.NotAnExport)
        val version = stated.takeIf { !it.isString }?.content?.toIntOrNull()
            ?: malformed("format_version is not a whole number: ${stated.content}")
        if (version != EXPORT_FORMAT_VERSION) {
            refuse(ExportRejection.UnsupportedFormatVersion(version, EXPORT_FORMAT_VERSION))
        }
    }

    private fun decodeEnvelope(root: JsonObject): ExportEnvelopeV1 = try {
        exportJson.decodeFromJsonElement<ExportEnvelopeV1>(root)
    } catch (cause: IllegalArgumentException) {
        malformed("the envelope is not readable: ${cause.message}")
    }

    private fun readEvent(index: Int, entry: ExportedEventV1): EncodedEvent {
        val id = try {
            EventId(entry.id)
        } catch (cause: IllegalArgumentException) {
            // Rejected rather than lowercased. RFC 9562 asks readers to accept
            // either case, but the events table's primary key is TEXT under a
            // binary collation, so accepting both would let one event live in
            // the log twice under two spellings — and dedupe by id is the whole
            // definition of a merge here. Normalising properly means every
            // id-bearing payload field too, which is a frozen-wire change and
            // belongs with sync. The word "lowercase" is in the message so a
            // hand-fix is one `tr` away.
            malformed(at(index, entry.id, "id must be a lowercase canonical UUIDv7: ${cause.message}"))
        }
        val occurredAt = try {
            Instant.parse(entry.occurredAt)
        } catch (cause: DateTimeParseException) {
            malformed(at(index, entry.id, "occurred_at is not an ISO-8601 instant: ${cause.message}"))
        }
        // Range-checked even though nothing reads it yet, and *because*
        // nothing reads it yet. Stored unvalidated, a hand-mangled offset
        // becomes a DateTimeException the first time something builds a
        // ZoneOffset from it — thrown from inside a transaction, long after
        // the import, on an event the user can no longer identify. Refusing
        // it here keeps the promise the rest of this class makes: every
        // refusal names an event.
        if (entry.tzOffsetMin !in -MAX_OFFSET_MINUTES..MAX_OFFSET_MINUTES) {
            malformed(at(index, entry.id, "tz_offset_min is outside ±$MAX_OFFSET_MINUTES: ${entry.tzOffsetMin}"))
        }
        val payload = EncodedPayload(entry.type, entry.schemaVersion, entry.payload.toString())
        proveReadable(index, entry.id, payload)
        // Truncated rather than refused. The column stores epoch millis, so a
        // third-party writer that reached for a nanosecond clock would
        // otherwise produce a file this app cannot accept for a difference it
        // does not record.
        return EncodedEvent(id, occurredAt.truncatedTo(ChronoUnit.MILLIS), entry.tzOffsetMin, payload)
    }

    /**
     * Decodes the payload to prove this build can read it, and **throws the
     * result away**.
     *
     * That is deliberate and load-bearing. What goes into the log is the
     * file's own payload text; re-encoding the decoded value would rewrite
     * `schema_version` to the current one and drop every key this build does
     * not know, which is the log migrated in place (architecture §3). The next
     * reader will want to use this value. They must not.
     */
    private fun proveReadable(index: Int, id: String, payload: EncodedPayload) {
        try {
            payloads.decode(payload.type, payload.schemaVersion, payload.json)
        } catch (cause: EventCodecException) {
            malformed(at(index, id, cause.message ?: "the payload could not be decoded"))
        }
    }

    private fun at(index: Int, id: String, detail: String) = "event $index ($id): $detail"

    private fun refuse(reason: ExportRejection): Nothing = throw Refusal(reason)

    private fun malformed(detail: String): Nothing = refuse(ExportRejection.Malformed(detail))

    /** Carries a refusal out of the ladder. Never escapes [read]. */
    private class Refusal(val reason: ExportRejection) : RuntimeException(reason.toString())

    private companion object {
        const val FORMAT_KEY = "format"
        const val FORMAT_VERSION_KEY = "format_version"

        /** `ZoneOffset` runs to ±18:00, and nothing outside that is an offset. */
        const val MAX_OFFSET_MINUTES = 18 * 60
    }
}
