package com.gawi.core.data.repository

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitMetadata
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The event store, seen as habits.
 *
 * One interface rather than a read half and a write half: there is one log,
 * one mutex and one in-memory projection behind all of it, and splitting the
 * type would advertise an independence that does not exist.
 *
 * Rejections are values, not exceptions. `RetroWindowExceeded` and `BlankName`
 * are things a user does, and the domain already models them as data. Thrown
 * exceptions are reserved for the two real failures: a corrupt log
 * (`EventCodecException`) and SQLite itself.
 *
 * Nothing above this interface knows events exist (architecture §4).
 */
interface HabitRepository {

    /** Mints the habit id and returns it, so the caller can navigate to it. */
    suspend fun createHabit(metadata: HabitMetadata): CommandResult<HabitId>

    suspend fun updateHabit(habitId: HabitId, metadata: HabitMetadata): CommandResult<Unit>

    suspend fun archiveHabit(habitId: HabitId): CommandResult<Unit>

    suspend fun unarchiveHabit(habitId: HabitId): CommandResult<Unit>

    suspend fun addCompletion(habitId: HabitId, logicalDate: LocalDate, note: String? = null): CommandResult<Unit>

    /**
     * Undoes a completion. Tombstones every live add for that cell in one
     * transaction, which is what keeps undo meaningful after a merge.
     */
    suspend fun undoCompletion(habitId: HabitId, logicalDate: LocalDate): CommandResult<Unit>

    /**
     * Writes the note on a completed cell. Empty text is a real write that
     * clears the note and wins last-write-wins like any other.
     */
    suspend fun updateNote(habitId: HabitId, logicalDate: LocalDate, text: String): CommandResult<Unit>

    /**
     * Every non-archived habit for the current logical date. Re-emits by
     * itself when the day rolls over, so callers never learn rollover exists.
     */
    fun observeToday(): Flow<List<TodayHabit>>

    /** One habit, archived or not — null once it no longer exists. */
    fun observeHabit(habitId: HabitId): Flow<TodayHabit?>

    /** Completed logical dates in a range, mapped to the note showing on each. */
    fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String?>>

    /**
     * Recomputes every cached streak for the current logical date. The only
     * way a streak reaches zero without a new event, so a day-rollover worker
     * will want this.
     */
    suspend fun refreshStreaks()

    /**
     * Drops the derived tables and replays the whole log into them
     * (architecture §4). The log itself is untouched.
     */
    suspend fun rebuildProjections()
}
