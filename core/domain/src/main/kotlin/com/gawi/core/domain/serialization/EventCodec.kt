package com.gawi.core.domain.serialization

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.EventPayload
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.event.HabitUpdated
import java.time.DateTimeException

/**
 * The single entry point between domain payloads and their persisted form.
 * The API is strings and domain types only — no serialization library
 * leaks out of this package.
 *
 * Every decode failure — unknown type, unknown schema version, or a corrupt
 * body — surfaces as [EventCodecException], so callers have one exception
 * to catch. The MVP log has a single writer, so an unknown shape is
 * corruption, not forward compatibility, and must fail loudly.
 */
class EventCodec {

    fun encode(payload: EventPayload): EncodedPayload = when (payload) {
        is HabitCreated -> HabitCreatedCodec.encode(payload)
        is HabitUpdated -> HabitUpdatedCodec.encode(payload)
        is HabitArchived -> HabitArchivedCodec.encode(payload)
        is HabitUnarchived -> HabitUnarchivedCodec.encode(payload)
        is CompletionAdded -> CompletionAddedCodec.encode(payload)
        is CompletionTombstoned -> CompletionTombstonedCodec.encode(payload)
        is CompletionNoteUpdated -> CompletionNoteUpdatedCodec.encode(payload)
    }

    fun decode(type: String, schemaVersion: Int, json: String): EventPayload {
        val codec = codecs[type] ?: throw EventCodecException("unknown event type: $type")
        return decodeWith(codec, type, schemaVersion, json)
    }

    // SerializationException is an IllegalArgumentException, as are the
    // canonical-form and schedule-kind require()s; dates throw DateTimeException.
    private fun decodeWith(codec: PayloadCodec<out EventPayload>, type: String, schemaVersion: Int, json: String): EventPayload = try {
        codec.decode(schemaVersion, json)
    } catch (cause: IllegalArgumentException) {
        throw EventCodecException("corrupt $type v$schemaVersion payload", cause)
    } catch (cause: DateTimeException) {
        throw EventCodecException("corrupt $type v$schemaVersion payload", cause)
    }

    private companion object {
        val codecs: Map<String, PayloadCodec<out EventPayload>> = listOf(
            HabitCreatedCodec,
            HabitUpdatedCodec,
            HabitArchivedCodec,
            HabitUnarchivedCodec,
            CompletionAddedCodec,
            CompletionTombstonedCodec,
            CompletionNoteUpdatedCodec,
        ).associateBy { it.type }
    }
}
