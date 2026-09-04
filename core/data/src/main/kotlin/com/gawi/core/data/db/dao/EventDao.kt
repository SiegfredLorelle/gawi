package com.gawi.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gawi.core.data.db.entity.EventEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and appends the event log. There is no update and no delete: fixes and
 * undos are new events (architecture §1.5).
 */
@Dao
internal interface EventDao {

    /**
     * Appends events. Conflicts abort rather than being ignored: this log has a
     * single writer behind one mutex, so a repeated event id is a bug in this
     * module and not a merge to absorb. Absorbing one is [insertMerging]'s job.
     */
    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    /**
     * Appends foreign events, skipping every id the log already holds, which is
     * what makes an import a merge rather than a replace.
     *
     * IGNORE and never REPLACE. The id *is* the event's identity, so a row
     * arriving under an id already present is the same event and the stored
     * bytes win; REPLACE is a delete and an insert in SQLite and would rewrite
     * history the user already had.
     *
     * Returns one row id per input, in order, with [ROW_NOT_INSERTED] for each
     * one skipped. The values are SQLite rowids and mean nothing here beyond
     * that sentinel — counting them is how a merge reports what was new
     * without a second query.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMerging(events: List<EventEntity>): List<Long>

    /**
     * The whole log in the total order architecture §3 fixes — `occurred_at`
     * then id, which UUIDv7 makes deterministic.
     */
    @Query("SELECT * FROM events ORDER BY occurred_at, id")
    suspend fun loadAll(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    /**
     * The same count, observed — whether there is anything here to lose.
     *
     * **The only `Flow` query outside `ReadModelDao`, and deliberately so.** That
     * interface serves screens with projected rows; this is a fact about the log
     * itself, and its one reader is the export nudge
     * ([com.gawi.core.data.backup.ExportJournal]), which is not a projection of
     * anything.
     *
     * A `Flow` rather than [count] because the alternative was recomputing it
     * whenever the *preferences* file emitted, which both misses a write to this
     * table and re-runs on writes that cannot have changed it. Room invalidates
     * per table, so this wakes on any append and on nothing else.
     */
    @Query("SELECT COUNT(*) FROM events")
    fun observeCount(): Flow<Int>
}

/** What [EventDao.insertMerging] returns for a row an existing id displaced. */
internal const val ROW_NOT_INSERTED = -1L
