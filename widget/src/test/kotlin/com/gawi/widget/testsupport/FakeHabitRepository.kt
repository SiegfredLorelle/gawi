package com.gawi.widget.testsupport

import com.gawi.core.data.model.HabitDetail
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
 *
 * **One fake per module rather than one shared, and the copies are not
 * interchangeable.** There is nowhere shared to put it — modules that draw
 * habits do not depend on one another, and no test-fixtures publishing is
 * configured in this build — but the stronger reason is that each records what
 * its own tests assert. This one records a `Write` naming the kind, because what
 * the widget has to prove is that a tap wrote *something* and which direction.
 * `:feature:today` records a `Toggle` carrying `undo`; `:feature:habits` keeps
 * seven separate lists, down to notes. A shared fake would be the union of all
 * three and would couple three modules' test needs.
 *
 * Accepted cost: [HabitRepository] gaining a method breaks all three at once.
 * That is a prompt to decide what each fake should answer, not a chore.
 */
@Suppress("TooManyFunctions")
class FakeHabitRepository(
    private var snapshot: TodaySnapshot = todaySnapshot(),
    private val failWith: Throwable? = null,
    /**
     * How many reads fail before one succeeds. Unbounded by default, so
     * `failWith` alone still means "always fails"; a finite value is what lets a
     * test tell a transient failure from a permanent one, which is the whole
     * difference the read's retry exists for.
     */
    private val failTimes: Int = Int.MAX_VALUE,
) : HabitRepository {

    val writes = mutableListOf<Write>()

    /** Reads attempted, so a test can assert the retry actually retried. */
    var reads = 0
        private set

    override fun observeToday(): Flow<TodaySnapshot> = flow {
        reads++
        failWith?.let { if (reads <= failTimes) throw it }
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

    override fun observeHabitDetail(habitId: HabitId): Flow<HabitDetail?> = error("not reached by the widget")

    override fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String?>> =
        error("not reached by the widget")

    override suspend fun refreshStreaks() = error("not reached by the widget")

    override suspend fun rebuildProjections() = error("not reached by the widget")
}
