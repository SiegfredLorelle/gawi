package com.gawi.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The append-only event log, mirroring architecture §3 column for column.
 *
 * A dumb column mirror: no type converters and no exposure to the domain's
 * `@JvmInline` id classes, which Room handles poorly. Translation lives in the
 * mappers, so the one place that can produce an invalid row is also the one
 * place that validates.
 *
 * There is no index beyond the primary key. `Projector.rebuild` sorts in
 * memory anyway, so an `(occurred_at, id)` index would buy no read and cost
 * write time on the append path, which is the hot one.
 */
@Entity(tableName = "events")
internal data class EventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "tz_offset_min")
    val tzOffsetMin: Int,
    @ColumnInfo(name = "payload")
    val payload: String,
)
