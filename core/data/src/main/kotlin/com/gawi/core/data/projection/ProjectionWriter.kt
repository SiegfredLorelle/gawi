package com.gawi.core.data.projection

import com.gawi.core.data.db.dao.CompletionProjectionDao
import com.gawi.core.data.db.dao.HabitProjectionDao
import com.gawi.core.data.db.dao.HabitStreakDao
import com.gawi.core.data.db.mapper.toEntity
import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.CompletionKey
import com.gawi.core.domain.projection.ProjectedState
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.streak.Streaks
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/**
 * Keeps the derived tables in step with the projected state.
 *
 * Two things about this class are load-bearing.
 *
 * **It writes an exact row delta rather than re-projecting.** The reason is
 * write amplification, not `Flow` churn: re-projecting would issue one row
 * write per completion in the whole log on every checkbox tap, inside the
 * transaction the tap is waiting on. Room invalidates per table, so a surgical
 * write does *not* stop an observer waking — that is `distinctUntilChanged`'s
 * job on the way out.
 *
 * **It skips writes that would not change the value.** Room's invalidation is
 * per table, so an upsert that stores what is already stored still wakes every
 * observer of that table. Adding a completion that is already there is a
 * genuine no-op the projector accepts silently, and it has to stay one here.
 *
 * The rows an event touches are derived by taking cheap candidate keys from
 * the payload and then diffing before against after on exactly those keys.
 * Pattern-matching the payload alone would be wrong: `Projector.applyAdd` can
 * land dead-on-arrival against a parked tombstone and can adopt parked note
 * writes, so which rows moved is a property of the transition, not of the
 * payload.
 */
internal class ProjectionWriter @Inject constructor(
    private val habits: HabitProjectionDao,
    private val completions: CompletionProjectionDao,
    private val streaks: HabitStreakDao,
) {

    /** Writes what changed between [before] and [after]. */
    suspend fun applyDelta(before: ProjectedState, after: ProjectedState, events: List<Event>, today: LocalDate, weekStart: DayOfWeek) {
        val candidateCells = events.flatMapTo(mutableSetOf()) { candidateCells(it, after) }
        val candidateHabits = events.flatMapTo(mutableSetOf()) { candidateHabits(it, after) }
        // A cell moving is also a reason to revisit its habit's streak.
        candidateHabits += candidateCells.map { it.habitId }

        candidateCells
            .filter { before.completions[it] != after.completions[it] }
            .forEach { writeCell(after, it) }

        candidateHabits
            .filter { before.habit(it) != after.habit(it) }
            .forEach { writeHabit(after, it) }

        // Every candidate habit, not only the ones whose completions moved: a
        // schedule change alone re-denominates the streak, because daily and
        // weekly runs are counted in different units. Streak rows that come
        // out identical are skipped by writeStreak, so the extra reads are the
        // whole cost.
        candidateHabits.forEach { writeStreak(after, it, today, weekStart) }
    }

    /**
     * Recomputes every habit's streak for [today].
     *
     * This is the only way a streak reaches zero with no new event, and it is
     * also what keeps the rebuild invariant true across a day boundary: an
     * append only touches the habits it affected, so without a full sweep an
     * untouched habit would keep yesterday's numbers while a rebuild would
     * recompute them.
     */
    suspend fun refreshStreaks(state: ProjectedState, today: LocalDate, weekStart: DayOfWeek) {
        state.habitRecords.keys.forEach { writeStreak(state, it, today, weekStart) }
    }

    /** Drops all derived rows and writes [state] out in full. */
    suspend fun rebuild(state: ProjectedState, today: LocalDate, weekStart: DayOfWeek) {
        habits.deleteAll()
        completions.deleteAll()
        streaks.deleteAll()

        state.habitRecords.keys.forEach { writeHabit(state, it) }
        state.completions.keys.forEach { writeCell(state, it) }
        refreshStreaks(state, today, weekStart)
    }

    private suspend fun writeHabit(state: ProjectedState, habitId: HabitId) {
        // Null means the log mentions this habit but its metadata has not
        // arrived yet — there is nothing renderable, so there is no row.
        val habit = state.habit(habitId)
        when {
            habit == null -> {
                habits.delete(habitId.value)
                streaks.delete(habitId.value)
            }

            else -> {
                val row = habit.toEntity()
                if (habits.find(habitId.value) != row) habits.upsert(row)
            }
        }
    }

    private suspend fun writeCell(state: ProjectedState, key: CompletionKey) {
        val cell = state.completions[key]
        when {
            cell?.isCompleted != true -> completions.delete(key.habitId.value, key.logicalDate.toString())

            else -> {
                val row = key.toEntity(cell.displayedNote())
                if (completions.find(key.habitId.value, key.logicalDate.toString()) != row) {
                    completions.upsert(row)
                }
            }
        }
    }

    private suspend fun writeStreak(state: ProjectedState, habitId: HabitId, today: LocalDate, weekStart: DayOfWeek) {
        val habit = state.habit(habitId)
        when (habit) {
            // No metadata means no schedule, and a streak has no unit without
            // one. writeHabit has already removed any stale row.
            null -> Unit

            else -> {
                val snapshot = Streaks.snapshot(state.completedDates(habitId), habit.schedule, today, weekStart)
                val row = snapshot.toEntity(habitId, today)
                if (streaks.find(habitId.value) != row) streaks.upsert(row)
            }
        }
    }

    /**
     * Cells this event could have moved. Resolved against the state *after*
     * the event, so a tombstone or note write that has just found its parent
     * resolves, and one still parked resolves to nothing.
     */
    private fun candidateCells(event: Event, after: ProjectedState): Set<CompletionKey> = when (val payload = event.payload) {
        is CompletionAdded -> setOf(CompletionKey(payload.habitId, payload.logicalDate))
        is CompletionTombstoned -> setOfNotNull(after.addIdToKey[payload.completionEventId])
        is CompletionNoteUpdated -> setOfNotNull(after.addIdToKey[payload.completionEventId])
        is HabitCreated, is HabitUpdated, is HabitArchived, is HabitUnarchived -> emptySet()
    }

    private fun candidateHabits(event: Event, after: ProjectedState): Set<HabitId> = when (val payload = event.payload) {
        is HabitCreated -> setOf(payload.habitId)
        is HabitUpdated -> setOf(payload.habitId)
        is HabitArchived -> setOf(payload.habitId)
        is HabitUnarchived -> setOf(payload.habitId)
        is CompletionAdded -> setOf(payload.habitId)
        is CompletionTombstoned -> setOfNotNull(after.addIdToKey[payload.completionEventId]?.habitId)
        is CompletionNoteUpdated -> setOfNotNull(after.addIdToKey[payload.completionEventId]?.habitId)
    }
}
