package com.gawi.feature.today.testsupport

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** A completion the ViewModel asked for, so a test can say which call it made. */
data class Toggle(val habitId: HabitId, val logicalDate: LocalDate, val undo: Boolean)

/**
 * The repository as the Today ViewModel uses it: one observable, two commands,
 * and honest failure for everything else.
 *
 * Backed by a [MutableSharedFlow] with no replay so that the state before the
 * first emission is observable — otherwise Loading could never be asserted.
 *
 * Suppressed at the declaration: the interface it implements carries the same
 * suppression for the same reason, which is a command per user action.
 */
@Suppress("TooManyFunctions")
class FakeHabitRepository : HabitRepository {

    private val snapshots = MutableSharedFlow<TodaySnapshot>(replay = 0)

    val toggles = mutableListOf<Toggle>()

    /** What the next command returns. Rejections are values, so this is one. */
    var result: CommandResult<Unit> = CommandResult.Accepted(Unit)

    /**
     * Emits once the ViewModel is really collecting.
     *
     * Consuming the Loading state does not prove that: it is `stateIn`'s cached
     * initial value and is served without the upstream having been subscribed
     * yet. Emitting into no subscriber with no replay drops the value silently,
     * so waiting here is what makes the tests deterministic rather than lucky.
     */
    suspend fun emit(snapshot: TodaySnapshot) {
        snapshots.subscriptionCount.first { it > 0 }
        snapshots.emit(snapshot)
    }

    suspend fun emit(habits: List<TodayHabit>) = emit(todaySnapshot(habits))

    override fun observeToday(): Flow<TodaySnapshot> = snapshots

    override suspend fun addCompletion(habitId: HabitId, logicalDate: LocalDate, note: String?): CommandResult<Unit> {
        toggles += Toggle(habitId, logicalDate, undo = false)
        return result
    }

    override suspend fun undoCompletion(habitId: HabitId, logicalDate: LocalDate): CommandResult<Unit> {
        toggles += Toggle(habitId, logicalDate, undo = true)
        return result
    }

    // Deliberately loud rather than TODO() or a quiet default: reaching one of
    // these from the Today view is a mistake worth failing the test that made it.
    override suspend fun createHabit(metadata: HabitMetadata): CommandResult<HabitId> = unused()

    override suspend fun updateHabit(habitId: HabitId, metadata: HabitMetadata): CommandResult<Unit> = unused()

    override suspend fun archiveHabit(habitId: HabitId): CommandResult<Unit> = unused()

    override suspend fun unarchiveHabit(habitId: HabitId): CommandResult<Unit> = unused()

    override suspend fun updateNote(habitId: HabitId, logicalDate: LocalDate, text: String): CommandResult<Unit> = unused()

    override fun observeHabit(habitId: HabitId): Flow<TodayHabit?> = unused()

    override fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String?>> = unused()

    override suspend fun refreshStreaks() = unused()

    override suspend fun rebuildProjections() = unused()

    private fun unused(): Nothing = error("the Today view does not use this")
}
