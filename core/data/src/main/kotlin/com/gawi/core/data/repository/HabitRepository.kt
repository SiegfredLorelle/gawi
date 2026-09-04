package com.gawi.core.data.repository

import com.gawi.core.data.model.HabitDetail
import com.gawi.core.data.model.ReadContext
import com.gawi.core.data.model.TagEffort
import com.gawi.core.data.model.TodayHabit
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
// A function count is not a reason to split what the KDoc above explains is
// one aggregate: a command per user action, plus the queries a screen needs.
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
     * One habit as it stands today, archived or not — null once it no longer
     * exists.
     *
     * The lean single-habit read: metadata, completion, week count and streak,
     * and nothing dated beyond that. What the editor wants, since a form is
     * about what a habit *is*.
     *
     * Kept beside [observeHabitDetail] rather than folded into it. An earlier
     * revision replaced this one outright on the grounds that there should be
     * exactly one way to ask for a single habit; that was the wrong cut. The two
     * ask different questions — this one "what is this habit", the other "what
     * is this habit, on what day, with which recent cells" — and answering the
     * first through the second makes the editor run and *wait on* a completions
     * query it discards, because `combine` withholds its first emission until
     * every source has emitted. Both are built on one private row query, so the
     * duplication is a wrapper rather than a second definition.
     */
    fun observeHabit(habitId: HabitId): Flow<TodayHabit?>

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
     * Completed logical dates in a range, for **every** habit at once.
     *
     * The app-wide counterpart of [observeCompletedDates], and one grouped query
     * rather than one per habit: an adherence list over a year would otherwise
     * open a flow per habit and combine them, which is more subscriptions and
     * more invalidation for the same rows.
     *
     * A habit with nothing completed in the range is **absent** rather than
     * present with an empty set. The caller knows which habits exist — that is
     * [observeAllHabits]' job — and inventing empty entries here would make this
     * read answer a question about habits when it only knows about completions.
     *
     * Notes are dropped. Nothing app-wide draws one, and carrying them would
     * make the payload proportional to the notes rather than to the dates.
     */
    fun observeCompletionDatesByHabit(from: LocalDate, to: LocalDate): Flow<Map<HabitId, Set<LocalDate>>>

    /**
     * The logical date and the week start, re-emitted when either changes.
     *
     * For a screen that needs to know what day it is and cannot work it out —
     * architecture §5 puts the day cutoff in the data layer, and a screen that
     * resolved its own date would hold a clock and a zone.
     *
     * **Deliberately not [observeToday].** That one sweeps every habit's streak
     * when it is subscribed, which is right for the screen it was built for and
     * wrong as a way of asking the date: a read should not write. This is the
     * same underlying flow with none of that hanging off it.
     *
     * Both values together rather than two flows, for the reason
     * [com.gawi.core.data.model.ReadContext] gives.
     */
    fun observeReadContext(): Flow<ReadContext>

    /**
     * Completions per tag over an inclusive date range, across every habit —
     * the tag effort distribution's read (docs/ux/insights.md §5).
     *
     * Totals rather than shares, and untagged habits are an entry rather than
     * an omission; [TagEffort] carries the reasoning for both. Archived habits
     * count, because effort spent does not stop having happened.
     *
     * Not per-habit, which is why it takes no [HabitId] and why the screen that
     * draws it has no home in `:feature:habits` (docs/architecture.md §2).
     */
    fun observeTagEffort(from: LocalDate, to: LocalDate): Flow<List<TagEffort>>

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
