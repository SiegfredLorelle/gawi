package com.gawi.feature.insights.testsupport

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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The repository as the history grid uses it: one single-habit read, one ranged
 * completions read, and honest failure for everything else.
 *
 * **The narrowest of the four copies of this fake, and that is the point.** Each
 * records what its own module's tests assert — `:feature:habits`' answers seven
 * lists because it holds the editor, the list, detail and notes; `:widget`'s
 * records a `Write`. This one records the *ranges* it was asked for, because
 * that is the whole behaviour under test: the grid must ask for the month it is
 * showing and no other.
 *
 * Suppressed at the declaration for the reason the interface carries the same
 * suppression: it is a command per user action, none of which this screen makes.
 */
@Suppress("TooManyFunctions")
class FakeHabitRepository : HabitRepository {

    /** What [observeHabit] resolves to. Null is "no habit with that id". */
    var habit: TodayHabit? = null

    /** Set to fail the habit read the way the real one can. */
    var habitFailure: Throwable? = null

    /** Every id a read was made for, so a screen reading the wrong habit fails. */
    val observedIds = mutableListOf<HabitId>()

    /**
     * Resolves only for the habit that was configured.
     *
     * Matching on the id rather than answering every request with [habit] is
     * what stops a screen that read the *wrong* habit from passing.
     */
    private fun configured(habitId: HabitId): TodayHabit? = habit?.takeIf { it.habit.id == habitId }

    /**
     * The lean single-habit read, which is the one the history screen uses.
     *
     * [observeHabitDetail] is deliberately loud here even though it would work:
     * it runs a completions query for the retro strip and carries a streak, and
     * a screen reaching for it would be waiting on rows it discards. Failing
     * makes that a red test rather than a slow screen.
     */
    override fun observeHabit(habitId: HabitId): Flow<TodayHabit?> {
        observedIds += habitId
        return habitFailure?.let { flow<TodayHabit?> { throw it } } ?: flowOf(configured(habitId))
    }

    override fun observeHabitDetail(habitId: HabitId): Flow<HabitDetail?> = unused()

    /**
     * Every window the grid asked for, in order.
     *
     * The month being drawn is the ViewModel's only query parameter, so this
     * list is how a test sees that stepping a month re-read the log rather than
     * re-labelling the same cells.
     */
    val ranges = mutableListOf<ClosedRange<LocalDate>>()

    /**
     * Completions across every month, mapped to the note on each.
     *
     * Filtered to the requested window on the way out rather than returned
     * whole. Returning everything would make a grid that ignored its own range
     * pass — which is exactly the bug [ranges] exists to catch, so the fake must
     * not paper over it from the other side.
     */
    var completions: Map<LocalDate, String?> = emptyMap()

    /** Set to fail the completions read the way the real one can. */
    var completionsFailure: Throwable? = null

    override fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String?>> {
        observedIds += habitId
        ranges += from..to
        return completionsFailure?.let { flow<Map<LocalDate, String?>> { throw it } }
            ?: flowOf(completions.filterKeys { day -> day in from..to })
    }

    // Deliberately loud rather than TODO() or a quiet default: reaching one of
    // these from a read-only screen is a mistake worth failing the test that
    // made it. Every write is in here — the grid makes none (insights.md §3).
    override fun observeToday(): Flow<TodaySnapshot> = unused()

    /**
     * Every habit, archived included — what the adherence list filters and the
     * tag totals do not.
     */
    var allHabits: List<HabitState> = emptyList()

    override fun observeAllHabits(): Flow<List<HabitState>> = listFailure?.let { flow<List<HabitState>> { throw it } } ?: flowOf(allHabits)

    /** Set to fail the list read the way the real one can. */
    var listFailure: Throwable? = null

    /**
     * Per-tag totals over the window, and the windows asked for.
     *
     * Shares the [ranges] list with the two other ranged reads on purpose: the
     * screen is meant to ask all of them for the *same* period, and a test that
     * recorded them separately could not see them drift apart.
     */
    var tagEffort: List<TagEffort> = emptyList()

    override fun observeTagEffort(from: LocalDate, to: LocalDate): Flow<List<TagEffort>> {
        ranges += from..to
        return effortFailure?.let { flow<List<TagEffort>> { throw it } } ?: flowOf(tagEffortByWindow[from..to] ?: tagEffort)
    }

    /**
     * Tag totals for one window in particular, ahead of [tagEffort] — how a test
     * gives the previous period a different top tag from the current one.
     */
    val tagEffortByWindow = mutableMapOf<ClosedRange<LocalDate>, List<TagEffort>>()

    /** Set to fail the tag read the way the real one can. */
    var effortFailure: Throwable? = null

    /**
     * Completions across every habit, filtered to the window on the way out for
     * the same reason [completions] is.
     */
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

    private val context = MutableSharedFlow<ReadContext>(replay = 0)

    /**
     * Emits the logical date and week start, waiting for a subscriber first.
     *
     * Replay 0 so `Loading` is observable — the same reason the other fakes here
     * withhold their first value, and what makes these tests deterministic
     * rather than lucky.
     */
    suspend fun emitContext(today: LocalDate = TODAY, weekStart: DayOfWeek = DayOfWeek.MONDAY) {
        context.subscriptionCount.first { it > 0 }
        context.emit(ReadContext(today, weekStart))
    }

    /** Set to fail the context read; the real one fails if settings cannot be read. */
    var contextFailure: Throwable? = null

    override fun observeReadContext(): Flow<ReadContext> = contextFailure?.let { cause -> flow { throw cause } } ?: context.asSharedFlow()

    override suspend fun createHabit(metadata: HabitMetadata): CommandResult<HabitId> = unused()

    override suspend fun updateHabit(habitId: HabitId, metadata: HabitMetadata): CommandResult<Unit> = unused()

    override suspend fun archiveHabit(habitId: HabitId): CommandResult<Unit> = unused()

    override suspend fun unarchiveHabit(habitId: HabitId): CommandResult<Unit> = unused()

    override suspend fun addCompletion(habitId: HabitId, logicalDate: LocalDate, note: String?): CommandResult<Unit> = unused()

    override suspend fun undoCompletion(habitId: HabitId, logicalDate: LocalDate): CommandResult<Unit> = unused()

    override suspend fun updateNote(habitId: HabitId, logicalDate: LocalDate, text: String): CommandResult<Unit> = unused()

    override suspend fun refreshStreaks() = unused()

    override suspend fun rebuildProjections() = unused()

    private fun unused(): Nothing = error("the history screen does not use this")
}
