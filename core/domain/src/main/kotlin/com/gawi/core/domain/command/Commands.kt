package com.gawi.core.domain.command

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitMetadata
import com.gawi.core.domain.projection.ProjectedState
import java.time.LocalDate

/**
 * The command side of the split (architecture §1.6): pure validation of
 * "the user is trying to do this now" against the projected state. Accepted
 * results carry payloads only — the caller stamps ids and timestamps; the
 * domain has no clock, so "today" is always a parameter. Nothing here is
 * consulted during replay; replay accepts what commands reject.
 */
object Commands {

    /** Retroactive logging window (architecture §5): up to this many days before today. */
    const val RETRO_WINDOW_DAYS = 3L

    fun createHabit(habitId: HabitId, metadata: HabitMetadata): CommandResult<HabitCreated> = if (metadata.name.isBlank()) {
        CommandResult.Rejected(CommandError.BlankName)
    } else {
        CommandResult.Accepted(metadata.asCreated(habitId))
    }

    fun updateHabit(state: ProjectedState, habitId: HabitId, metadata: HabitMetadata): CommandResult<HabitUpdated> = when {
        state.habit(habitId) == null -> CommandResult.Rejected(CommandError.HabitNotFound)
        metadata.name.isBlank() -> CommandResult.Rejected(CommandError.BlankName)
        else -> CommandResult.Accepted(metadata.asUpdated(habitId))
    }

    fun archiveHabit(state: ProjectedState, habitId: HabitId): CommandResult<HabitArchived> = if (state.habit(habitId) == null) {
        CommandResult.Rejected(CommandError.HabitNotFound)
    } else {
        CommandResult.Accepted(HabitArchived(habitId))
    }

    fun unarchiveHabit(state: ProjectedState, habitId: HabitId): CommandResult<HabitUnarchived> = if (state.habit(habitId) == null) {
        CommandResult.Rejected(CommandError.HabitNotFound)
    } else {
        CommandResult.Accepted(HabitUnarchived(habitId))
    }

    /**
     * Logging into an already-completed cell is accepted: the fresh add is
     * harmless under idempotent collapse and is what makes add-undo-add
     * work (architecture §4).
     */
    fun addCompletion(
        state: ProjectedState,
        habitId: HabitId,
        logicalDate: LocalDate,
        today: LocalDate,
        note: String? = null,
    ): CommandResult<CompletionAdded> {
        val habit = state.habit(habitId)
        return when {
            habit == null -> CommandResult.Rejected(CommandError.HabitNotFound)

            habit.archived -> CommandResult.Rejected(CommandError.HabitIsArchived)

            logicalDate.isAfter(today) -> CommandResult.Rejected(CommandError.FutureLogicalDate)

            logicalDate.isBefore(today.minusDays(RETRO_WINDOW_DAYS)) ->
                CommandResult.Rejected(CommandError.RetroWindowExceeded)

            else -> CommandResult.Accepted(CompletionAdded(habitId, logicalDate, note))
        }
    }

    /**
     * Undo tombstones every live add the local log knows for the cell
     * (architecture §4), so a merge-duplicate the undo already saw cannot
     * resurrect the completion.
     *
     * Checks archived before liveness, matching [updateCompletionNote]; the
     * habit-level gate is the coarser one, as in [addCompletion].
     *
     * There is deliberately no `HabitNotFound` branch. A completion can
     * legitimately exist before its `HabitCreated` arrives under log merge
     * (architecture §1.3), which is why [ProjectedState] models metadata as
     * nullable and parks early-arriving references — refusing to undo a
     * completion the user can see, because its habit metadata has not synced
     * yet, would be worse than an imprecise error.
     *
     * The archived gate reads [ProjectedState.isArchived], not
     * `habit(id)?.archived`: the latter is null whenever metadata is missing,
     * which would wave through an undo on a habit the log already knows was
     * archived. Missing metadata means unknown, never not-archived.
     */
    fun undoCompletion(state: ProjectedState, habitId: HabitId, logicalDate: LocalDate): CommandResult<List<CompletionTombstoned>> {
        val liveIds = state.liveAddIds(habitId, logicalDate)
        return when {
            state.isArchived(habitId) -> CommandResult.Rejected(CommandError.HabitIsArchived)
            liveIds.isEmpty() -> CommandResult.Rejected(CommandError.CompletionNotFound)
            else -> CommandResult.Accepted(liveIds.sorted().map(::CompletionTombstoned))
        }
    }

    /**
     * [text] may be empty — that is a clear, a valid write that wins LWW like
     * any other.
     *
     * Archived is checked before liveness so an archived habit reports
     * [CommandError.HabitIsArchived] whatever the completion's state, exactly
     * as [undoCompletion] does; an unknown event id is still
     * [CommandError.CompletionNotFound], since the cell cannot be resolved
     * without one. The same no-`HabitNotFound` reasoning and the same
     * [ProjectedState.isArchived] gate apply here.
     */
    fun updateCompletionNote(state: ProjectedState, completionEventId: EventId, text: String): CommandResult<CompletionNoteUpdated> {
        val key = state.addIdToKey[completionEventId]
            ?: return CommandResult.Rejected(CommandError.CompletionNotFound)
        val cell = state.completions.getValue(key)
        return when {
            state.isArchived(key.habitId) -> CommandResult.Rejected(CommandError.HabitIsArchived)
            completionEventId !in cell.liveAddIds -> CommandResult.Rejected(CommandError.CompletionNotFound)
            else -> CommandResult.Accepted(CompletionNoteUpdated(completionEventId, text))
        }
    }

    private fun HabitMetadata.asCreated(habitId: HabitId) = HabitCreated(habitId, name, icon, color, schedule, tag)

    private fun HabitMetadata.asUpdated(habitId: HabitId) = HabitUpdated(habitId, name, icon, color, schedule, tag)
}
