package com.gawi.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.gawi.core.data.db.entity.EventEntity

/**
 * Reads and appends the event log. There is no update and no delete: fixes and
 * undos are new events (architecture §1.5).
 */
@Dao
internal interface EventDao {

    /**
     * Appends events. Conflicts abort rather than being ignored: the MVP log
     * has a single writer behind one mutex, so a repeated event id is a bug in
     * this module and not a merge to absorb. Phase 2's foreign-event append is
     * where dedupe-by-uuid belongs, and it will want its own entry point.
     */
    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    /**
     * The whole log in the total order architecture §3 fixes — `occurred_at`
     * then id, which UUIDv7 makes deterministic.
     */
    @Query("SELECT * FROM events ORDER BY occurred_at, id")
    suspend fun loadAll(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int
}
