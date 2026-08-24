package com.gawi.core.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema migration, and so far only one.
 *
 * **All of these are derived-table migrations, and that is not a coincidence.**
 * [GawiDatabase]'s KDoc splits schema change in two: a derived table can be
 * changed freely because the log rebuilds it, while the `events` table is never
 * migrated at all — payloads carry a schema version and readers upcast on
 * decode (architecture §3). A migration here that touched `events` would be a
 * bug in the design rather than in the code.
 *
 * Which is why none of these have to *move data*. Getting the columns into
 * shape is the whole job; filling them is [com.gawi.core.data.PROJECTION_VERSION]'s,
 * and a schema change that alters what a projection writes has to bump that too
 * or the new columns stay empty until something else forces a replay.
 */
internal object Migrations {

    /**
     * Adds `habits.created_on` for `HabitState.createdOn`.
     *
     * `ALTER TABLE … ADD COLUMN` rather than the drop-and-recreate the database
     * KDoc describes. Both end in the same place here — `PROJECTION_VERSION`
     * went to 2 in the same change, so the next start replays the log and
     * rewrites every row regardless — and adding a column cannot get the
     * recreated schema subtly wrong. Hand-written DDL has to match what Room
     * generates exactly, down to column order, and a mismatch is a crash on
     * open rather than a failing test.
     *
     * The column is nullable with no default, which is also what the projection
     * writes for a habit whose `HabitCreated` has not arrived. So a row that
     * somehow survives without being rewritten says "unknown" rather than
     * claiming a date, which is the honest reading either way.
     */
    val V1_TO_V2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE habits ADD COLUMN created_on TEXT")
        }
    }

    /** In order. Private, so [addGawiMigrations] is the only way to apply them. */
    private val ALL = listOf(V1_TO_V2)

    /**
     * Adds every migration to a builder, one call each.
     *
     * A fold rather than `addMigrations(*ALL)`: a spread copies the array on
     * every call and detekt objects to it, and the list is the wrong shape to
     * hand out anyway — a caller that took it could add some and not others.
     * Applying them is the only thing anyone needs to do with them, so that is
     * the only thing offered.
     */
    fun <T : RoomDatabase> RoomDatabase.Builder<T>.addGawiMigrations(): RoomDatabase.Builder<T> =
        ALL.fold(this) { builder, migration -> builder.addMigrations(migration) }
}
