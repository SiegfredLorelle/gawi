package com.gawi.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.gawi.core.data.db.dao.CompletionExportDao
import com.gawi.core.data.db.dao.CompletionProjectionDao
import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.db.dao.HabitProjectionDao
import com.gawi.core.data.db.dao.HabitStreakDao
import com.gawi.core.data.db.dao.ProjectionMetaDao
import com.gawi.core.data.db.dao.ReadModelDao
import com.gawi.core.data.db.entity.CompletionEntity
import com.gawi.core.data.db.entity.EventEntity
import com.gawi.core.data.db.entity.HabitEntity
import com.gawi.core.data.db.entity.HabitStreakEntity
import com.gawi.core.data.db.entity.ProjectionMetaEntity

/**
 * The one database: an append-only `events` table, and — as the projection
 * lands — the derived tables the UI reads through `Flow` queries.
 *
 * Two rules govern schema change here, and they are not the same rule:
 *
 * A **derived** table change is cheap. Bump [DATABASE_VERSION], write a
 * migration that drops and recreates derived tables only, and bump the
 * projection version so the next start replays the log into them.
 *
 * A change to `events` is not a migration at all. The log is never rewritten;
 * payloads carry a schema version and readers upcast on decode (architecture
 * §3). Replaying a years-old log through current code must always work, which
 * is also why `fallbackToDestructiveMigration` must never be configured on
 * this database — it would delete the only copy of the user's history.
 */
@Database(
    entities = [
        EventEntity::class,
        HabitEntity::class,
        CompletionEntity::class,
        HabitStreakEntity::class,
        ProjectionMetaEntity::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true,
)
internal abstract class GawiDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    abstract fun habitProjectionDao(): HabitProjectionDao

    abstract fun completionProjectionDao(): CompletionProjectionDao

    abstract fun completionExportDao(): CompletionExportDao

    abstract fun habitStreakDao(): HabitStreakDao

    abstract fun projectionMetaDao(): ProjectionMetaDao

    abstract fun readModelDao(): ReadModelDao
}

internal const val DATABASE_VERSION = 2

/** File name of the on-device database. */
internal const val DATABASE_NAME = "gawi.db"
