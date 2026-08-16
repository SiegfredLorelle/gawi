package com.gawi.core.domain.event

import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import java.time.LocalDate

/**
 * The seven event payloads of the MVP log (architecture §3). Domain types
 * only — the wire shape lives in internal serialization DTOs, so these
 * classes carry no serialization annotations and can be refactored without
 * touching stored JSON.
 */
sealed interface EventPayload

/** A new habit. Also acts as a whole-record metadata write under LWW. */
data class HabitCreated(
    val habitId: HabitId,
    val name: String,
    val icon: String,
    val color: String,
    val schedule: Schedule,
    val tag: String?,
) : EventPayload

/** Whole-record replacement of habit metadata (architecture §3: no per-field merge). */
data class HabitUpdated(
    val habitId: HabitId,
    val name: String,
    val icon: String,
    val color: String,
    val schedule: Schedule,
    val tag: String?,
) : EventPayload

data class HabitArchived(val habitId: HabitId) : EventPayload

data class HabitUnarchived(val habitId: HabitId) : EventPayload

/**
 * A completion for a habit on a logical date. [logicalDate] is computed and
 * stored at log time (architecture §5); replay never re-buckets it. The
 * event's own id is the completion's identity — tombstones reference it.
 */
data class CompletionAdded(val habitId: HabitId, val logicalDate: LocalDate, val note: String?) : EventPayload

/** Undo: kills exactly the referenced CompletionAdded, regardless of order or timestamps. */
data class CompletionTombstoned(val completionEventId: EventId) : EventPayload

/**
 * A note write against a specific CompletionAdded. Empty [text] is a valid
 * write that clears the note and wins LWW like any other (architecture §3).
 */
data class CompletionNoteUpdated(val completionEventId: EventId, val text: String) : EventPayload
