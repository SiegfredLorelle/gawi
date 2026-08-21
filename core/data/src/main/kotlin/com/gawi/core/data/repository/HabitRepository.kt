package com.gawi.core.data.repository

import com.gawi.core.data.model.HabitDetail
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitMetadata
import com.gawi.core.domain.projection.HabitState
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
 * exceptions are reserved for the real failures: a corrupt log
 * (`EventCodecException`), SQLite itself, and an unreadable settings store,
 * which a command refuses to guess past because it validates against the
 * answer.
 *
 * Nothing above this interface knows events exist (architecture §4).
 */
// One aggregate means one interface: a command per user action, plus the
// queries a screen needs. Splitting it to satisfy a function count would
// advertise an independence between reads and writes that does not exist.
@Suppress("TooManyFunctions")
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
     * Every non-archived habit for the current logical date, with that date and
     * the thresholds the mascot's mood is decided against.
     *
     * Re-emits by itself when the day rolls over and when the reminder
     * threshold passes, so callers never learn either exists — and never hold a
     * clock, a zone or a cutoff of their own. The date a caller writes a
     * completion to is the one it was handed here, not one it resolved.
     */
    fun observeToday(): Flow<TodaySnapshot>

    /**
     * Every habit as it is configured, archived included, ordered by name.
     *
     * The management list's read. Deliberately not a [TodaySnapshot]: it
     * carries no completion state, no week count and no streak, because
     * managing habits is not doing them.
     */
    fun observeAllHabits(): Flow<List<HabitState>>

    /**
     * One habit, archived or not, with the logical date it was read for and the
     * recent cells the retro strip draws — null once it no longer exists.
     *
     * Archived habits are deliberately visible here where `observeToday` hides
     * them: asking for one habit by id is how unarchiving stays reachable.
     *
     * Carries the date rather than leaving the caller to resolve one. A screen
     * cannot: that needs a clock, a zone and the day cutoff (architecture §5),
     * and a stale date lands inside the 3-day window, which *accepts* it.
     */
    fun observeHabitDetail(habitId: HabitId): Flow<HabitDetail?>

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
