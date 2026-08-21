package com.gawi.widget.testsupport

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitMetadata
import com.gawi.core.domain.projection.HabitState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/** One recorded write, so a test can assert the date as well as the habit. */
data class Write(val kind: String, val habitId: HabitId, val logicalDate: LocalDate)

/**
 * Just enough repository for the widget's two call sites.
 *
 * The unused members throw rather than returning something harmless: the widget
 * reaches exactly two of them, and a fake that quietly answers a third would let
 * a future call site go untested while looking covered.
 *
 * [failWith] makes the *read* throw, which is the failure the widget has to
 * absorb — `SQLiteException` is a `RuntimeException` and the settings store
 * refuses to guess a cutoff, so neither is hypothetical.
 */
@Suppress("TooManyFunctions")
class FakeHabitRepository(private var snapshot: TodaySnapshot = todaySnapshot(), private val failWith: Throwable? = null) :
    HabitRepository {

    val writes = mutableListOf<Write>()

    override fun observeToday(): Flow<TodaySnapshot> = flow {
        failWith?.let { throw it }
        emit(snapshot)
    }

    override suspend fun addCompletion(habitId: HabitId, logicalDate: LocalDate, note: String?): CommandResult<Unit> {
        writes += Write("add", habitId, logicalDate)
        return CommandResult.Accepted(Unit)
    }

    override suspend fun undoCompletion(habitId: HabitId, logicalDate: LocalDate): CommandResult<Unit> {
        writes += Write("undo", habitId, logicalDate)
        return CommandResult.Accepted(Unit)
    }

    override suspend fun createHabit(metadata: HabitMetadata): CommandResult<HabitId> = error("not reached by the widget")

    override suspend fun updateHabit(habitId: HabitId, metadata: HabitMetadata): CommandResult<Unit> = error("not reached by the widget")

    override suspend fun archiveHabit(habitId: HabitId): CommandResult<Unit> = error("not reached by the widget")

    override suspend fun unarchiveHabit(habitId: HabitId): CommandResult<Unit> = error("not reached by the widget")

    override suspend fun updateNote(habitId: HabitId, logicalDate: LocalDate, text: String): CommandResult<Unit> =
        error("not reached by the widget")

    override fun observeAllHabits(): Flow<List<HabitState>> = error("not reached by the widget")

    override fun observeHabit(habitId: HabitId): Flow<TodayHabit?> = error("not reached by the widget")

    override fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String?>> =
        error("not reached by the widget")

    override suspend fun refreshStreaks() = error("not reached by the widget")

    override suspend fun rebuildProjections() = error("not reached by the widget")
}
