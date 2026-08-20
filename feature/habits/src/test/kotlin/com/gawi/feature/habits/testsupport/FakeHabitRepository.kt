package com.gawi.feature.habits.testsupport

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitMetadata
import com.gawi.core.domain.projection.HabitState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * The repository as the habits screens use it: one observable list, one single
 * read, the four metadata commands, and honest failure for everything else.
 *
 * Backed by a [MutableSharedFlow] with no replay so the state before the first
 * emission is observable — otherwise Loading could never be asserted.
 *
 * Suppressed at the declaration: the interface it implements carries the same
 * suppression for the same reason, which is a command per user action.
 */
@Suppress("TooManyFunctions")
class FakeHabitRepository : HabitRepository {

    private val habits = MutableSharedFlow<List<HabitState>>(replay = 0)

    /** Set to fail the list read the way the real one can — see the ViewModel. */
    var listFailure: Throwable? = null

    /**
     * Emits once the ViewModel is really collecting.
     *
     * Consuming Loading does not prove that: it is `stateIn`'s cached initial
     * value, served before the upstream is subscribed. Emitting into no
     * subscriber with no replay drops the value silently, so waiting here is
     * what makes these tests deterministic rather than lucky.
     */
    suspend fun emit(habits: List<HabitState>) {
        this.habits.subscriptionCount.first { it > 0 }
        this.habits.emit(habits)
    }

    override fun observeAllHabits(): Flow<List<HabitState>> = listFailure?.let { flow<List<HabitState>> { throw it } } ?: habits

    /** What [observeHabit] resolves to. Null is "no habit with that id". */
    var habit: TodayHabit? = null

    /** Set to fail the single read the way the real one can. */
    var habitFailure: Throwable? = null

    override fun observeHabit(habitId: HabitId): Flow<TodayHabit?> {
        observedIds += habitId
        return habitFailure?.let { flow<TodayHabit?> { throw it } } ?: flowOf(habit)
    }

    val observedIds = mutableListOf<HabitId>()
    val created = mutableListOf<HabitMetadata>()
    val updated = mutableListOf<Pair<HabitId, HabitMetadata>>()
    val archived = mutableListOf<HabitId>()
    val unarchived = mutableListOf<HabitId>()

    /** What the next command returns. Rejections are values, so this is one. */
    var result: CommandResult<Unit> = CommandResult.Accepted(Unit)

    /** The id a create hands back, so a caller could navigate to it. */
    var mintedId: HabitId = habitId(99)

    private fun <T> resultOf(payload: T): CommandResult<T> = when (val outcome = result) {
        is CommandResult.Rejected -> outcome
        is CommandResult.Accepted -> CommandResult.Accepted(payload)
    }

    override suspend fun createHabit(metadata: HabitMetadata): CommandResult<HabitId> {
        created += metadata
        return resultOf(mintedId)
    }

    override suspend fun updateHabit(habitId: HabitId, metadata: HabitMetadata): CommandResult<Unit> {
        updated += habitId to metadata
        return result
    }

    override suspend fun archiveHabit(habitId: HabitId): CommandResult<Unit> {
        archived += habitId
        return result
    }

    override suspend fun unarchiveHabit(habitId: HabitId): CommandResult<Unit> {
        unarchived += habitId
        return result
    }

    // Deliberately loud rather than TODO() or a quiet default: reaching one of
    // these from a habits screen is a mistake worth failing the test that made it.
    override fun observeToday(): Flow<TodaySnapshot> = unused()

    override suspend fun addCompletion(habitId: HabitId, logicalDate: LocalDate, note: String?): CommandResult<Unit> = unused()

    override suspend fun undoCompletion(habitId: HabitId, logicalDate: LocalDate): CommandResult<Unit> = unused()

    override suspend fun updateNote(habitId: HabitId, logicalDate: LocalDate, text: String): CommandResult<Unit> = unused()

    override fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String?>> = unused()

    override suspend fun refreshStreaks() = unused()

    override suspend fun rebuildProjections() = unused()

    private fun unused(): Nothing = error("the habits screens do not use this")
}
