package com.gawi.feature.habits.testsupport

import com.gawi.core.data.model.HabitDetail
import com.gawi.core.data.model.ReadContext
import com.gawi.core.data.model.TagEffort
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
 *
 * **One fake per module rather than one shared, and the copies are not
 * interchangeable.** There is nowhere shared to put it — modules that draw
 * habits do not depend on one another, and no test-fixtures publishing is
 * configured in this build — but the stronger reason is that each records what
 * its own tests assert. This is the widest of the three: seven lists, because
 * this module holds the editor, the list, detail and notes. `:feature:today`
 * records only a `Toggle`; `:widget` only a `Write`. A shared fake would be the
 * union of all three and would couple three modules' test needs.
 *
 * Accepted cost: [HabitRepository] gaining a method breaks all three at once.
 * That is a prompt to decide what each fake should answer, not a chore.
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

    /** What [observeHabitDetail] resolves to. Null is "no habit with that id". */
    var habit: TodayHabit? = null

    /** The logical date the detail read is answering for. */
    var today: LocalDate = TODAY

    /** The completed cells in the strip window, mapped to the note on each. */
    var recent: Map<LocalDate, String?> = emptyMap()

    /** Set to fail the single read the way the real one can. */
    var habitFailure: Throwable? = null

    /**
     * Resolves only for the habit that was configured.
     *
     * Matching on the id rather than answering every request with [habit] is
     * what stops a screen that read the *wrong* habit from passing: without it,
     * a detail screen asking for anything at all gets the fixture back.
     */
    private fun configured(habitId: HabitId): TodayHabit? = habit?.takeIf { it.habit.id == habitId }

    override fun observeHabit(habitId: HabitId): Flow<TodayHabit?> {
        observedIds += habitId
        return habitFailure?.let { flow<TodayHabit?> { throw it } } ?: flowOf(configured(habitId))
    }

    override fun observeHabitDetail(habitId: HabitId): Flow<HabitDetail?> {
        observedIds += habitId
        val detail = configured(habitId)?.let { HabitDetail(habit = it, today = today, recent = recent) }
        return habitFailure?.let { flow<HabitDetail?> { throw it } } ?: flowOf(detail)
    }

    val observedIds = mutableListOf<HabitId>()
    val created = mutableListOf<HabitMetadata>()
    val updated = mutableListOf<Pair<HabitId, HabitMetadata>>()
    val archived = mutableListOf<HabitId>()
    val unarchived = mutableListOf<HabitId>()

    /** What the next command returns. Rejections are values, so this is one. */
    var result: CommandResult<Unit> = CommandResult.Accepted(Unit)

    /**
     * Set to make the next command *throw* rather than reject.
     *
     * The write path can: `appendLocked` consults `SettingsSource.current()` on
     * every write, and that refuses to guess when the preferences file cannot be
     * read. Distinct from [result], which is the rejection-as-a-value path.
     */
    var commandFailure: Throwable? = null

    private fun failIfAsked() {
        commandFailure?.let { throw it }
    }

    /** The id a create hands back, so a caller could navigate to it. */
    var mintedId: HabitId = habitId(99)

    private fun <T> resultOf(payload: T): CommandResult<T> = when (val outcome = result) {
        is CommandResult.Rejected -> outcome
        is CommandResult.Accepted -> CommandResult.Accepted(payload)
    }

    override suspend fun createHabit(metadata: HabitMetadata): CommandResult<HabitId> {
        failIfAsked()
        created += metadata
        return resultOf(mintedId)
    }

    override suspend fun updateHabit(habitId: HabitId, metadata: HabitMetadata): CommandResult<Unit> {
        failIfAsked()
        updated += habitId to metadata
        return result
    }

    override suspend fun archiveHabit(habitId: HabitId): CommandResult<Unit> {
        failIfAsked()
        archived += habitId
        return result
    }

    override suspend fun unarchiveHabit(habitId: HabitId): CommandResult<Unit> {
        failIfAsked()
        unarchived += habitId
        return result
    }

    // Deliberately loud rather than TODO() or a quiet default: reaching one of
    // these from a habits screen is a mistake worth failing the test that made it.
    override fun observeToday(): Flow<TodaySnapshot> = unused()

    /**
     * Completions added, in order: which habit, which day, and the note carried.
     *
     * The habit id is recorded rather than dropped, so a write aimed at the
     * wrong habit is a failing test rather than an invisible one.
     */
    val completed = mutableListOf<Triple<HabitId, LocalDate, String?>>()

    /** Completions undone, in order, with the habit each belonged to. */
    val undone = mutableListOf<Pair<HabitId, LocalDate>>()

    override suspend fun addCompletion(habitId: HabitId, logicalDate: LocalDate, note: String?): CommandResult<Unit> {
        failIfAsked()
        completed += Triple(habitId, logicalDate, note)
        return result
    }

    override suspend fun undoCompletion(habitId: HabitId, logicalDate: LocalDate): CommandResult<Unit> {
        failIfAsked()
        undone += habitId to logicalDate
        return result
    }

    /** Notes written, in order. An empty text is a clear and is recorded as one. */
    val notes = mutableListOf<Triple<HabitId, LocalDate, String>>()

    override suspend fun updateNote(habitId: HabitId, logicalDate: LocalDate, text: String): CommandResult<Unit> {
        failIfAsked()
        notes += Triple(habitId, logicalDate, text)
        return result
    }

    override fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String?>> = unused()

    override fun observeTagEffort(from: LocalDate, to: LocalDate): Flow<List<TagEffort>> = unused()

    override fun observeCompletionDatesByHabit(from: LocalDate, to: LocalDate): Flow<Map<HabitId, Set<LocalDate>>> = unused()

    override fun observeReadContext(): Flow<ReadContext> = unused()

    override suspend fun refreshStreaks() = unused()

    override suspend fun rebuildProjections() = unused()

    private fun unused(): Nothing = error("the habits screens do not use this")
}
