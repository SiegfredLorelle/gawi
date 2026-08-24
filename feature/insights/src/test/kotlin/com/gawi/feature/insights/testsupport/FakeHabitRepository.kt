package com.gawi.feature.insights.testsupport

import com.gawi.core.data.model.HabitDetail
import com.gawi.core.data.model.TagEffort
import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitMetadata
import com.gawi.core.domain.projection.HabitState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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

    /** What [observeHabitDetail] resolves to. Null is "no habit with that id". */
    var habit: TodayHabit? = null

    /** The logical date the detail read is answering for. */
    var today: LocalDate = TODAY

    /** Set to fail the detail read the way the real one can. */
    var detailFailure: Throwable? = null

    /** Every id a read was made for, so a screen reading the wrong habit fails. */
    val observedIds = mutableListOf<HabitId>()

    /**
     * Resolves only for the habit that was configured.
     *
     * Matching on the id rather than answering every request with [habit] is
     * what stops a screen that read the *wrong* habit from passing.
     */
    private fun configured(habitId: HabitId): TodayHabit? = habit?.takeIf { it.habit.id == habitId }

    override fun observeHabitDetail(habitId: HabitId): Flow<HabitDetail?> {
        observedIds += habitId
        val detail = configured(habitId)?.let { HabitDetail(habit = it, today = today, recent = emptyMap()) }
        return detailFailure?.let { flow<HabitDetail?> { throw it } } ?: flowOf(detail)
    }

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

    override fun observeAllHabits(): Flow<List<HabitState>> = unused()

    override fun observeHabit(habitId: HabitId): Flow<TodayHabit?> = unused()

    override fun observeTagEffort(from: LocalDate, to: LocalDate): Flow<List<TagEffort>> = unused()

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
