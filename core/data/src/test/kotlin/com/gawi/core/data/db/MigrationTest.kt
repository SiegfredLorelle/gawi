package com.gawi.core.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The migrations, run against the schema that actually shipped.
 *
 * This is the first migration in the repo, so this file is as much a record of
 * how one gets tested as it is a test of that one. What it proves is what
 * `DataModule` bets on by refusing `fallbackToDestructiveMigration`: an existing
 * database *opens* rather than being deleted.
 *
 * **The event log is the assertion that matters.** Derived rows are disposable —
 * `PROJECTION_VERSION` replays them on the next start — so a migration that lost
 * a habit row is recoverable and one that lost an event is not. Both are
 * checked, in that order of seriousness.
 *
 * **The old schema is read from `core/data/schemas/`, not retyped here.** Those
 * JSONs are exported and committed for exactly this (RoomConventionPlugin), they
 * carry the `CREATE TABLE` Room itself generated, and they carry the identity
 * hash Room checks on open — so a v1 database built from them is the one that
 * shipped rather than an approximation of it. Retyping the DDL would make this
 * test agree with itself instead of with history.
 *
 * `MigrationTestHelper` would do this, and is deliberately not used: on an
 * Android library module it resolves to the variant that needs an
 * `Instrumentation`, which means pulling `androidx.test` in for one test — the
 * dependency the screen tests already go out of their way to avoid. Room's own
 * builder is used instead, so what is exercised is the production open path.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = RuntimeEnvironment.getApplication()

    /**
     * A v1 database with an event and a habit row survives to v2, and the new
     * column arrives empty.
     *
     * Empty is correct rather than a shortfall: the column says when a habit was
     * created, a migration has no way to know, and `PROJECTION_VERSION` fills it
     * by replaying the log. Asserting null is asserting the migration does not
     * *invent* a date — the failure that would be invisible afterwards, since a
     * plausible date reads exactly like a real one.
     */
    @Test
    fun `v1 opens at v2 with the log intact and the new column empty`() {
        val name = "migration-v1-to-v2.db"
        context.getDatabasePath(name).also { it.parentFile?.mkdirs() }.delete()

        createV1(name) { db ->
            db.execSQL(
                """
                INSERT INTO events (id, type, schema_version, occurred_at, tz_offset_min, payload)
                VALUES ('$EVENT_ID', 'habit_created', 1, 1000, 0, '{}')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO habits (habit_id, name, icon, color, schedule_kind, times_per_week, tag, archived)
                VALUES ('$HABIT_ID', 'read', 'book', '#aabbcc', 'daily', NULL, 'mind', 0)
                """.trimIndent(),
            )
        }

        val database = with(Migrations) {
            Room.databaseBuilder(context, GawiDatabase::class.java, name)
                .addGawiMigrations()
                .build()
        }

        database.openHelper.readableDatabase.use { db ->
            db.query("SELECT id FROM events").use { events ->
                assertTrue("the migration lost the event log", events.moveToFirst())
                assertEquals(EVENT_ID, events.getString(0))
            }
            db.query("SELECT name, created_on FROM habits").use { habits ->
                assertTrue("the migration lost the habit row", habits.moveToFirst())
                assertEquals("read", habits.getString(0))
                assertTrue("the migration invented a creation date", habits.isNull(1))
            }
        }
        database.close()
    }

    /**
     * Builds the v1 database from the committed schema and hands it to [seed].
     *
     * `room_master_table` and its identity hash are part of the schema rather
     * than an implementation detail to skip: Room reads that hash on open and
     * refuses a database whose schema it cannot verify, so a v1 file without it
     * would fail for a reason that has nothing to do with the migration.
     */
    private fun createV1(name: String, seed: (SQLiteDatabase) -> Unit) {
        // org.json rather than kotlinx-serialization: it is on the platform
        // already, and :core:data does not otherwise have a JSON parser on its
        // test classpath. Reading a build artifact is not the wire format.
        val schema = JSONObject(File(V1_SCHEMA).readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                fun ddl(json: JSONObject) = json.getString("createSql").replace("\${TABLE_NAME}", table)

                db.execSQL(ddl(entity))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(ddl(indices.getJSONObject(j)))
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                arrayOf(schema.getString("identityHash")),
            )
            db.version = 1
            seed(db)
        }
    }

    private companion object {
        const val V1_SCHEMA = "schemas/com.gawi.core.data.db.GawiDatabase/1.json"
        const val EVENT_ID = "00000000-0000-7000-8000-000000000001"
        const val HABIT_ID = "00000000-0000-7000-8000-00000000000a"
    }
}
