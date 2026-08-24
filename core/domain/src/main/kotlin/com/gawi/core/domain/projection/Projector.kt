package com.gawi.core.domain.projection

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.model.HabitId
import java.time.ZoneOffset

/**
 * The replay side of the command-vs-replay split (architecture §1.6):
 * [apply] is total over well-formed events — it never validates, never
 * throws, and is commutative and idempotent, so any arrival order and any
 * duplication converge to the same state. Sync and import depend on this.
 */
object Projector {

    fun apply(state: ProjectedState, event: Event): ProjectedState {
        val stamp = WriteStamp(event.occurredAt, event.id)
        return when (val payload = event.payload) {
            // Two registers from one event: whole-record metadata, and the
            // creation date. They are separate because they resolve differently
            // — the newest metadata wins, the *earliest* creation does.
            is HabitCreated ->
                applyCreation(applyMetadata(state, payload.habitId, payload.toMetadata(), stamp), payload.habitId, event, stamp)

            is HabitUpdated -> applyMetadata(state, payload.habitId, payload.toMetadata(), stamp)

            is HabitArchived -> applyArchived(state, payload.habitId, archived = true, stamp)

            is HabitUnarchived -> applyArchived(state, payload.habitId, archived = false, stamp)

            is CompletionAdded -> applyAdd(state, event.id, payload, stamp)

            is CompletionTombstoned -> applyTombstone(state, payload.completionEventId)

            is CompletionNoteUpdated ->
                applyNoteWrite(state, NoteWrite(payload.completionEventId, payload.text, stamp))
        }
    }

    /**
     * Drop-and-replay (architecture §4). Sorting is cosmetic — [apply] is
     * order-independent — but gives rebuilds a canonical fold order.
     */
    fun rebuild(events: List<Event>): ProjectedState = events
        .sortedWith(compareBy({ it.occurredAt }, { it.id }))
        .fold(ProjectedState.EMPTY, ::apply)

    private fun HabitCreated.toMetadata() = HabitMetadata(name, icon, color, schedule, tag)

    private fun HabitUpdated.toMetadata() = HabitMetadata(name, icon, color, schedule, tag)

    private fun applyMetadata(state: ProjectedState, habitId: HabitId, metadata: HabitMetadata, stamp: WriteStamp): ProjectedState {
        val record = state.habitRecords[habitId] ?: HabitRecord()
        val current = record.metadataStamp
        if (current != null && current >= stamp) return state
        val updated = record.copy(metadata = metadata, metadataStamp = stamp)
        return state.copy(habitRecords = state.habitRecords + (habitId to updated))
    }

    /**
     * The creation register: earliest wins, and the only place projection reads
     * [Event.tzOffsetMin].
     *
     * **Why the offset rather than a payload field.** `HabitCreated` carries no
     * date, and adding one would be an event-payload schema bump for every
     * client — while the envelope already records the moment *and* the local
     * offset it was written at. Both are stored at command time and neither can
     * change, so this is a pure function of immutable log data: every replay, on
     * every device, forever, produces the same date. That is the property
     * architecture §5 is protecting when it says a date is decided once and
     * stored, and it is why reading the offset here does not weaken it.
     *
     * **Earliest wins, not last-write-wins.** A habit is created once. Two
     * `HabitCreated` events for one id can only arrive through sync, and the
     * later one is a duplicate of a fact the earlier one already recorded — so
     * taking the earlier keeps this commutative and idempotent like every other
     * register here. Note the comparison is the inverse of the two below.
     */
    private fun applyCreation(state: ProjectedState, habitId: HabitId, event: Event, stamp: WriteStamp): ProjectedState {
        val record = state.habitRecords[habitId] ?: HabitRecord()
        val current = record.createdStamp
        if (current != null && current <= stamp) return state
        val on = event.occurredAt.atOffset(ZoneOffset.ofTotalSeconds(event.tzOffsetMin * SECONDS_PER_MINUTE)).toLocalDate()
        val updated = record.copy(createdOn = on, createdStamp = stamp)
        return state.copy(habitRecords = state.habitRecords + (habitId to updated))
    }

    private fun applyArchived(state: ProjectedState, habitId: HabitId, archived: Boolean, stamp: WriteStamp): ProjectedState {
        val record = state.habitRecords[habitId] ?: HabitRecord()
        val current = record.archiveStamp
        if (current != null && current >= stamp) return state
        val updated = record.copy(archived = archived, archiveStamp = stamp)
        return state.copy(habitRecords = state.habitRecords + (habitId to updated))
    }

    private fun applyAdd(state: ProjectedState, addId: EventId, payload: CompletionAdded, stamp: WriteStamp): ProjectedState {
        if (addId in state.addIdToKey) return state
        val key = CompletionKey(payload.habitId, payload.logicalDate)
        val deadOnArrival = addId in state.pendingTombstones
        var cell = state.completions[key] ?: CompletionCell()
        cell = if (deadOnArrival) {
            cell.copy(tombstonedAddIds = cell.tombstonedAddIds + addId)
        } else {
            cell.copy(liveAddIds = cell.liveAddIds + addId)
        }
        val inlineNote = payload.note?.let { NoteWrite(addId, it, stamp) }
        val adopted = state.pendingNoteWrites.values.filter { it.parentAddId == addId }
        val newWrites = listOfNotNull(inlineNote) + adopted
        if (newWrites.isNotEmpty()) {
            cell = cell.copy(noteWrites = cell.noteWrites + newWrites.associateBy { it.writeId })
        }
        return state.copy(
            completions = state.completions + (key to cell),
            addIdToKey = state.addIdToKey + (addId to key),
            pendingTombstones = state.pendingTombstones - addId,
            pendingNoteWrites = state.pendingNoteWrites - adopted.map { it.writeId }.toSet(),
        )
    }

    private fun applyTombstone(state: ProjectedState, addId: EventId): ProjectedState {
        val key = state.addIdToKey[addId]
            ?: return state.copy(pendingTombstones = state.pendingTombstones + addId)
        val cell = state.completions.getValue(key)
        return if (addId in cell.tombstonedAddIds) {
            state
        } else {
            val updated = cell.copy(
                liveAddIds = cell.liveAddIds - addId,
                tombstonedAddIds = cell.tombstonedAddIds + addId,
            )
            state.copy(completions = state.completions + (key to updated))
        }
    }

    private fun applyNoteWrite(state: ProjectedState, write: NoteWrite): ProjectedState {
        val key = state.addIdToKey[write.parentAddId]
            ?: return state.copy(pendingNoteWrites = state.pendingNoteWrites + (write.writeId to write))
        val cell = state.completions.getValue(key)
        val updated = cell.copy(noteWrites = cell.noteWrites + (write.writeId to write))
        return state.copy(completions = state.completions + (key to updated))
    }

    /** `tzOffsetMin` is minutes; `ZoneOffset` wants seconds. */
    private const val SECONDS_PER_MINUTE = 60
}
