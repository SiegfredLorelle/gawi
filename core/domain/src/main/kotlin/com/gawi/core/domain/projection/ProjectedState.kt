package com.gawi.core.domain.projection

import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate

/**
 * LWW ordering (architecture §3): by occurred-at, ties broken by event id —
 * UUIDv7 makes this a deterministic total order.
 */
data class WriteStamp(val occurredAt: Instant, val eventId: EventId) : Comparable<WriteStamp> {
    override fun compareTo(other: WriteStamp): Int = compareValuesBy(this, other, WriteStamp::occurredAt, WriteStamp::eventId)
}

/** The habit fields that move together under whole-record LWW. */
data class HabitMetadata(val name: String, val icon: String, val color: String, val schedule: Schedule, val tag: String?)

/**
 * Everything the log has said about one habit id. Metadata (whole-record
 * LWW over HabitCreated/HabitUpdated) and the archived flag (LWW over
 * HabitArchived/HabitUnarchived) are independent registers, so events for a
 * habit whose create has not arrived yet still converge. [metadata] is null
 * until any metadata write arrives.
 */
data class HabitRecord(
    val metadata: HabitMetadata? = null,
    val metadataStamp: WriteStamp? = null,
    val archived: Boolean = false,
    val archiveStamp: WriteStamp? = null,
    /**
     * The calendar date the habit was created on, from the creating event's own
     * envelope — see `Projector.applyCreation`.
     *
     * A third register, and the only one that is **earliest**-wins rather than
     * last-write-wins: a habit was created once, so two `HabitCreated` events
     * for one id (reachable only through sync) resolve to the earlier of them
     * rather than to the later. [createdStamp] is what that comparison is made
     * on, so the outcome does not depend on arrival order.
     *
     * Null until a `HabitCreated` arrives. `HabitUpdated` alone is enough to
     * materialize a [HabitState] — metadata is its own register — so a habit can
     * be renderable while this is still unknown.
     */
    val createdOn: LocalDate? = null,
    val createdStamp: WriteStamp? = null,
)

/** A habit as the UI sees it — only materialized once metadata exists. */
data class HabitState(
    val id: HabitId,
    val name: String,
    val icon: String,
    val color: String,
    val schedule: Schedule,
    val tag: String?,
    val archived: Boolean,
    /**
     * The date this habit came into existence, or null if the log has not said.
     *
     * **Nullable on purpose, and callers have to handle it.** Metadata and
     * creation are separate registers ([HabitRecord]), so a `HabitUpdated` that
     * arrives before its `HabitCreated` makes a habit renderable with no known
     * start. Unreachable locally — a create always precedes its own updates —
     * and reachable once Phase 2 sync can deliver a log out of order.
     *
     * What it is for: a window that reaches back before a habit existed yields
     * a completion rate that is arithmetically right and meaningless
     * (docs/ux/insights.md §4). This is what lets a screen clip such a window
     * rather than draw a number that accuses the user of missing days that were
     * never offered.
     *
     * **Not a logical date.** It is the calendar date in the offset the creating
     * event was written at, because the day cutoff *as it was then* is not
     * recorded anywhere. The consequence is bounded at one day, for a habit
     * created between midnight and the cutoff, and it errs later — a start date
     * a day late shortens the window rather than lengthening it, which is the
     * direction that cannot manufacture a miss.
     */
    val createdOn: LocalDate?,
)

data class CompletionKey(val habitId: HabitId, val logicalDate: LocalDate)

/** One note write: inline on an add (writeId == parentAddId) or a CompletionNoteUpdated. */
data class NoteWrite(val parentAddId: EventId, val text: String, val stamp: WriteStamp) {
    val writeId: EventId get() = stamp.eventId
}

/**
 * All the log has said about one (habit, logical date) cell. Duplicate live
 * adds collapse to a single completion (architecture §4); every add id is
 * kept so a tombstone kills exactly the ids it references and undo can
 * enumerate what is live. Note writes are retained even when their parent
 * add is dead — liveness is re-resolved at read time, which is what makes
 * apply order-independent.
 */
data class CompletionCell(
    val liveAddIds: Set<EventId> = emptySet(),
    val tombstonedAddIds: Set<EventId> = emptySet(),
    val noteWrites: Map<EventId, NoteWrite> = emptyMap(),
) {
    val isCompleted: Boolean get() = liveAddIds.isNotEmpty()

    /**
     * The note the cell shows (architecture §4): LWW across writes whose
     * parent add is live; a winning empty text is a clear and shows as null.
     */
    fun displayedNote(): String? = noteWrites.values
        .filter { it.parentAddId in liveAddIds }
        .maxByOrNull { it.stamp }
        ?.text
        ?.takeIf { it.isNotEmpty() }
}

/**
 * A pure, order-independent function of the event set (architecture §4).
 * The bookkeeping members ([addIdToKey], [pendingTombstones],
 * [pendingNoteWrites]) park references that arrive before their target —
 * sync makes that ordering possible — and participate in equality so the
 * incremental-vs-rebuild invariant compares them too.
 *
 * **[addIdToKey] indexes [completions], and every key it holds resolves.**
 * `Projector.applyAdd` is the only writer and adds to both maps in one `copy`;
 * nothing ever removes from either, since tombstoning moves an id between a
 * cell's live and dead sets rather than dropping it. Callers therefore follow
 * an [addIdToKey] hit with `completions.getValue(key)` on purpose — a miss
 * means the state came from somewhere other than `Projector`, and that is a
 * broken invariant worth failing on, not a completion to report as missing.
 */
data class ProjectedState(
    val habitRecords: Map<HabitId, HabitRecord> = emptyMap(),
    val completions: Map<CompletionKey, CompletionCell> = emptyMap(),
    val addIdToKey: Map<EventId, CompletionKey> = emptyMap(),
    val pendingTombstones: Set<EventId> = emptySet(),
    val pendingNoteWrites: Map<EventId, NoteWrite> = emptyMap(),
) {

    fun habit(id: HabitId): HabitState? = habitRecords[id]?.let { record ->
        record.metadata?.let { m ->
            HabitState(id, m.name, m.icon, m.color, m.schedule, m.tag, record.archived, record.createdOn)
        }
    }

    /**
     * Whether the log says this habit is archived, independent of whether its
     * metadata has arrived. [habit] deliberately answers null until a habit
     * can be displayed, so commands must ask here instead — the archive
     * register is its own LWW register precisely so `HabitArchived` merging
     * ahead of `HabitCreated` still counts.
     */
    fun isArchived(habitId: HabitId): Boolean = habitRecords[habitId]?.archived == true

    /** What an undo command must tombstone: every live add the local log knows for the cell. */
    fun liveAddIds(habitId: HabitId, logicalDate: LocalDate): Set<EventId> =
        completions[CompletionKey(habitId, logicalDate)]?.liveAddIds.orEmpty()

    /** The completed logical dates for a habit — the streak calculators' input. */
    fun completedDates(habitId: HabitId): Set<LocalDate> = completions.entries.mapNotNullTo(mutableSetOf()) { (key, cell) ->
        key.logicalDate.takeIf { key.habitId == habitId && cell.isCompleted }
    }

    companion object {
        val EMPTY = ProjectedState()
    }
}
