package com.gawi.core.testing

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
import com.gawi.core.domain.testing.habitId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.LocalDate

/** One completion written or undone, so a test can assert the habit, the day and the direction. */
data class Completion(val habitId: HabitId, val logicalDate: LocalDate, val note: String? = null, val undo: Boolean = false)

/**
 * The repository as every screen and the widget use it, recording what each
 * asks for and answering with what a test configured.
 *
 * **Two shapes of read, chosen by what a test sets.** A ViewModel test wants to
 * observe `Loading` before the first value, so the hot reads — [observeToday]
 * and [observeAllHabits] — are `MutableSharedFlow`s with no replay, fed by
 * [emitToday] and [emitHabits], which wait for a subscriber first: emitting
 * into no subscriber with no replay drops the value silently, and waiting is
 * what makes those tests deterministic rather than lucky. A widget or Insights
 * test wants a cold, finite read of a value it set, so setting [snapshot] or
 * [allHabits] switches that read to `flowOf` that value. [failWith] puts
 * [observeToday] on the cold path too, counting [reads] and throwing for the
 * first [failTimes] of them, which is how a test tells a transient failure
 * from a permanent one.
 *
 * **Every unused member is loud.** Reaching one from a screen is a mistake
 * worth failing the test that made it, so none returns a quiet default.
 *
 * Suppressed at the declaration: the interface it implements carries the same
 * suppression for the same reason, which is a command per user action.
 */
@Suppress("TooManyFunctions")
class FakeHabitRepository(
    /** When set, [observeToday] is a cold read of this; when null, the hot flow fed by [emitToday]. */
    var snapshot: TodaySnapshot? = null,
    /** Makes [observeToday] throw the way the real read path can — `SQLiteException` is a `RuntimeException`. */
    var failWith: Throwable? = null,
    /**
     * How many reads fail before one succeeds. Unbounded by default, so
     * [failWith] alone still means "always fails"; a finite value is what lets a
     * test tell a transient failure from a permanent one, which is the whole
     * difference the read's retry exists for.
     */
    private val failTimes: Int = Int.MAX_VALUE,
) : HabitRepository {

    // ----- Today

    private val snapshots = MutableSharedFlow<TodaySnapshot>(replay = 0)

    /** Reads of today attempted on the cold path, so a test can assert the retry actually retried. */
    var reads = 0
        private set

    override fun observeToday(): Flow<TodaySnapshot> = if (snapshot == null && failWith == null) {
        snapshots
    } else {
        flow {
            reads++
            failWith?.let { if (reads <= failTimes) throw it }
            emit(snapshot ?: todaySnapshot())
        }
    }

    suspend fun emitToday(snapshot: TodaySnapshot) {
        snapshots.subscriptionCount.first { it > 0 }
        snapshots.emit(snapshot)
    }

    suspend fun emitToday(habits: List<TodayHabit>) = emitToday(todaySnapshot(habits))

    // ----- Every habit

    private val habits = MutableSharedFlow<List<HabitState>>(replay = 0)

    /** When set, [observeAllHabits] is a cold read of this, archived included; when null, the hot flow fed by [emitHabits]. */
    var allHabits: List<HabitState>? = null

    /** Set to fail the list read the way the real one can. */
    var listFailure: Throwable? = null

    suspend fun emitHabits(habits: List<HabitState>) {
        this.habits.subscriptionCount.first { it > 0 }
        this.habits.emit(habits)
    }

    override fun observeAllHabits(): Flow<List<HabitState>> =
        listFailure?.let { flow<List<HabitState>> { throw it } } ?: allHabits?.let { flowOf(it) } ?: habits

    // ----- One habit

    /** What [observeHabit] and [observeHabitDetail] resolve to. Null is "no habit with that id". */
    var habit: TodayHabit? = null

    /** The logical date the detail read is answering for. */
    var today: LocalDate = FIXED_DATE

    /** The completed cells in the strip window, mapped to the note on each. */
    var recent: Map<LocalDate, String?> = emptyMap()

    /** Set to fail the single read the way the real one can. */
    var habitFailure: Throwable? = null

    /** Every id a read was made for, so a screen reading the wrong habit fails. */
    val observedIds = mutableListOf<HabitId>()

    /**
     * Resolves only for the habit that was configured. Matching on the id rather
     * than answering every request with [habit] is what stops a screen that read
     * the *wrong* habit from passing.
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

    // ----- Ranged reads

    /**
     * Every window a ranged read asked for, in order, shared by all three ranged
     * reads on purpose: a screen is meant to ask them for the *same* period, and
     * a test that recorded them separately could not see them drift apart. The
     * month being drawn is the history screen's only query parameter, so this
     * is how a test sees that stepping a month re-read the log.
     */
    val ranges = mutableListOf<ClosedRange<LocalDate>>()

    /**
     * One habit's completions across every month, mapped to the note on each,
     * filtered to the requested window on the way out. Returning everything
     * would make a grid that ignored its own range pass — the bug [ranges]
     * exists to catch — so the fake must not paper over it from the other side.
     */
    var completedDates: Map<LocalDate, String?> = emptyMap()

    /** Set to fail the completions read the way the real one can. */
    var completionsFailure: Throwable? = null

    override fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String?>> {
        observedIds += habitId
        ranges += from..to
        return completionsFailure?.let { flow<Map<LocalDate, String?>> { throw it } }
            ?: flowOf(completedDates.filterKeys { day -> day in from..to })
    }

    /** Per-tag totals over any window, unless [tagEffortByWindow] names the window. */
    var tagEffort: List<TagEffort> = emptyList()

    /** Tag totals for one window in particular — how a test gives the previous period a different top tag. */
    val tagEffortByWindow = mutableMapOf<ClosedRange<LocalDate>, List<TagEffort>>()

    /** Set to fail the tag read the way the real one can. */
    var effortFailure: Throwable? = null

    override fun observeTagEffort(from: LocalDate, to: LocalDate): Flow<List<TagEffort>> {
        ranges += from..to
        return effortFailure?.let { flow<List<TagEffort>> { throw it } } ?: flowOf(tagEffortByWindow[from..to] ?: tagEffort)
    }

    /** Completions across every habit, filtered to the window for the reason [completedDates] is. */
    var completionsByHabit: Map<HabitId, Set<LocalDate>> = emptyMap()

    override fun observeCompletionDatesByHabit(from: LocalDate, to: LocalDate): Flow<Map<HabitId, Set<LocalDate>>> {
        ranges += from..to
        return flowOf(
            completionsByHabit
                .mapValues { (_, dates) -> dates.filter { it in from..to }.toSet() }
                // Absent rather than empty, matching the real read: a habit with
                // nothing in the window is not a habit with an empty set.
                .filterValues { it.isNotEmpty() },
        )
    }

    // ----- Read context

    private val context = MutableSharedFlow<ReadContext>(replay = 0)

    /** Emits the logical date and week start, waiting for a subscriber first, for the reason [emitToday] does. */
    suspend fun emitContext(today: LocalDate = FIXED_DATE, weekStart: DayOfWeek = DayOfWeek.MONDAY) {
        context.subscriptionCount.first { it > 0 }
        context.emit(ReadContext(today, weekStart))
    }

    /** Set to fail the context read; the real one fails if settings cannot be read. */
    var contextFailure: Throwable? = null

    override fun observeReadContext(): Flow<ReadContext> = contextFailure?.let { cause -> flow { throw cause } } ?: context.asSharedFlow()

    // ----- Commands

    /** What the next command returns. Rejections are values, so this is one. */
    var result: CommandResult<Unit> = CommandResult.Accepted(Unit)

    /**
     * Set to make the next command *throw* rather than reject. The write path
     * can: `appendLocked` consults `SettingsSource.current()` on every write, and
     * that refuses to guess when the preferences file cannot be read. Distinct
     * from [result], which is the rejection-as-a-value path.
     */
    var commandFailure: Throwable? = null

    private fun failIfAsked() {
        commandFailure?.let { throw it }
    }

    /** The id a create hands back, so a caller could navigate to it. */
    var mintedId: HabitId = habitId(MINTED_ID_TAIL)

    val created = mutableListOf<HabitMetadata>()
    val updated = mutableListOf<Pair<HabitId, HabitMetadata>>()
    val archived = mutableListOf<HabitId>()
    val unarchived = mutableListOf<HabitId>()

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

    /**
     * Completions written and undone, in order. The habit id is recorded rather
     * than dropped, so a write aimed at the wrong habit is a failing test rather
     * than an invisible one; the direction is recorded because a second tap
     * undoing is the Today row's whole behaviour.
     */
    val completions = mutableListOf<Completion>()

    /** The completions added, as habit, day and note. */
    val completed: List<Triple<HabitId, LocalDate, String?>>
        get() = completions.filterNot { it.undo }.map { Triple(it.habitId, it.logicalDate, it.note) }

    /** The completions undone, as habit and day. */
    val undone: List<Pair<HabitId, LocalDate>>
        get() = completions.filter { it.undo }.map { it.habitId to it.logicalDate }

    override suspend fun addCompletion(habitId: HabitId, logicalDate: LocalDate, note: String?): CommandResult<Unit> {
        failIfAsked()
        completions += Completion(habitId, logicalDate, note, undo = false)
        return result
    }

    override suspend fun undoCompletion(habitId: HabitId, logicalDate: LocalDate): CommandResult<Unit> {
        failIfAsked()
        completions += Completion(habitId, logicalDate, undo = true)
        return result
    }

    /** Notes written, in order. An empty text is a clear and is recorded as one. */
    val notes = mutableListOf<Triple<HabitId, LocalDate, String>>()

    override suspend fun updateNote(habitId: HabitId, logicalDate: LocalDate, text: String): CommandResult<Unit> {
        failIfAsked()
        notes += Triple(habitId, logicalDate, text)
        return result
    }

    override suspend fun refreshStreaks() = unused()

    override suspend fun rebuildProjections() = unused()

    private fun unused(): Nothing = error("no screen or widget reaches this")

    private companion object {
        /** Far from the ids tests hand out, so a create's answer cannot collide with a fixture. */
        const val MINTED_ID_TAIL = 99
    }
}
