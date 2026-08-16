package com.gawi.core.domain.serialization

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.serialization.wire.CompletionAddedWireV1
import com.gawi.core.domain.serialization.wire.CompletionNoteUpdatedWireV1
import com.gawi.core.domain.serialization.wire.CompletionTombstonedWireV1
import com.gawi.core.domain.serialization.wire.HabitArchivedWireV1
import com.gawi.core.domain.serialization.wire.HabitCreatedWireV1
import com.gawi.core.domain.serialization.wire.HabitUnarchivedWireV1
import com.gawi.core.domain.serialization.wire.HabitUpdatedWireV1
import com.gawi.core.domain.serialization.wire.toWireV1

internal object HabitCreatedCodec : PayloadCodec<HabitCreated> {
    override val type = "HabitCreated"
    override val currentVersion = 1

    override fun encode(payload: HabitCreated) = EncodedPayload(type, currentVersion, wireJson.encodeToString(payload.toWireV1()))

    override fun decode(schemaVersion: Int, json: String): HabitCreated = when (schemaVersion) {
        1 -> wireJson.decodeFromString<HabitCreatedWireV1>(json).toDomain()
        else -> throw EventCodecException("$type v$schemaVersion unknown")
    }
}

internal object HabitUpdatedCodec : PayloadCodec<HabitUpdated> {
    override val type = "HabitUpdated"
    override val currentVersion = 1

    override fun encode(payload: HabitUpdated) = EncodedPayload(type, currentVersion, wireJson.encodeToString(payload.toWireV1()))

    override fun decode(schemaVersion: Int, json: String): HabitUpdated = when (schemaVersion) {
        1 -> wireJson.decodeFromString<HabitUpdatedWireV1>(json).toDomain()
        else -> throw EventCodecException("$type v$schemaVersion unknown")
    }
}

internal object HabitArchivedCodec : PayloadCodec<HabitArchived> {
    override val type = "HabitArchived"
    override val currentVersion = 1

    override fun encode(payload: HabitArchived) = EncodedPayload(type, currentVersion, wireJson.encodeToString(payload.toWireV1()))

    override fun decode(schemaVersion: Int, json: String): HabitArchived = when (schemaVersion) {
        1 -> wireJson.decodeFromString<HabitArchivedWireV1>(json).toDomain()
        else -> throw EventCodecException("$type v$schemaVersion unknown")
    }
}

internal object HabitUnarchivedCodec : PayloadCodec<HabitUnarchived> {
    override val type = "HabitUnarchived"
    override val currentVersion = 1

    override fun encode(payload: HabitUnarchived) = EncodedPayload(type, currentVersion, wireJson.encodeToString(payload.toWireV1()))

    override fun decode(schemaVersion: Int, json: String): HabitUnarchived = when (schemaVersion) {
        1 -> wireJson.decodeFromString<HabitUnarchivedWireV1>(json).toDomain()
        else -> throw EventCodecException("$type v$schemaVersion unknown")
    }
}

internal object CompletionAddedCodec : PayloadCodec<CompletionAdded> {
    override val type = "CompletionAdded"
    override val currentVersion = 1

    override fun encode(payload: CompletionAdded) = EncodedPayload(type, currentVersion, wireJson.encodeToString(payload.toWireV1()))

    override fun decode(schemaVersion: Int, json: String): CompletionAdded = when (schemaVersion) {
        1 -> wireJson.decodeFromString<CompletionAddedWireV1>(json).toDomain()
        else -> throw EventCodecException("$type v$schemaVersion unknown")
    }
}

internal object CompletionTombstonedCodec : PayloadCodec<CompletionTombstoned> {
    override val type = "CompletionTombstoned"
    override val currentVersion = 1

    override fun encode(payload: CompletionTombstoned) = EncodedPayload(type, currentVersion, wireJson.encodeToString(payload.toWireV1()))

    override fun decode(schemaVersion: Int, json: String): CompletionTombstoned = when (schemaVersion) {
        1 -> wireJson.decodeFromString<CompletionTombstonedWireV1>(json).toDomain()
        else -> throw EventCodecException("$type v$schemaVersion unknown")
    }
}

internal object CompletionNoteUpdatedCodec : PayloadCodec<CompletionNoteUpdated> {
    override val type = "CompletionNoteUpdated"
    override val currentVersion = 1

    override fun encode(payload: CompletionNoteUpdated) = EncodedPayload(type, currentVersion, wireJson.encodeToString(payload.toWireV1()))

    override fun decode(schemaVersion: Int, json: String): CompletionNoteUpdated = when (schemaVersion) {
        1 -> wireJson.decodeFromString<CompletionNoteUpdatedWireV1>(json).toDomain()
        else -> throw EventCodecException("$type v$schemaVersion unknown")
    }
}
