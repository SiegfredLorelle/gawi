package com.gawi.core.data.repository

import androidx.room.withTransaction
import com.gawi.core.data.PROJECTION_VERSION
import com.gawi.core.data.db.GawiDatabase
import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.db.dao.ProjectionMetaDao
import com.gawi.core.data.db.dao.ROW_NOT_INSERTED
import com.gawi.core.data.db.dao.ReadModelDao
import com.gawi.core.data.db.entity.EventEntity
import com.gawi.core.data.db.entity.ProjectionMetaEntity
import com.gawi.core.data.db.mapper.toDomain
import com.gawi.core.data.db.mapper.toEntity
import com.gawi.core.data.model.HabitDetail
import com.gawi.core.data.model.ReadContext
import com.gawi.core.data.model.TagEffort
import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.projection.ProjectionListener
import com.gawi.core.data.projection.ProjectionWriter
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.data.time.DeviceClock
import com.gawi.core.domain.command.CommandError
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.command.Commands
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.event.EventPayload
import com.gawi.core.domain.id.UuidV7Generator
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitMetadata
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.projection.ProjectedState
import com.gawi.core.domain.projection.Projector
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.serialization.export.EncodedEvent
import com.gawi.core.domain.time.logicalDate
import com.gawi.core.domain.time.reminderOn
import com.gawi.core.domain.time.weekStartOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The event store.
 *
 * **The in-memory [ProjectedState] is the command-side authority; the Room
 * tables are the read model.** Commands need bookkeeping that no sane read
 * schema holds — which adds are live in a cell, which tombstones and note
 * writes are parked waiting for a parent that has not arrived — so the state
 * is folded from the log once and carried. The read path never consults it.
 * That is also why the ordering between Room's post-commit invalidation and
 * the in-memory publish below does not matter: nothing reads both.
 *
 * **This type must be a singleton.** It owns the state and the mutex, so two
 * instances would be two command authorities disagreeing in silence — as
 * load-bearing as the same rule on `UuidV7Generator`, which two instances of
 * can collide outright on the sync dedupe key.
 *
 * The mutex is not reentrant, so every public entry point takes the lock and
 * delegates to a private `…Locked` body that never does.
 *
 * Known cost: `Projector.rebuild` folds with immutable map copies, so the
 * start-up fold is quadratic in the number of habits and cells. At the PRD's
 * ~2k events a year that is milliseconds; it would not be at ten times that.
 * The fold is forced onto a background dispatcher below, and
 * `rebuildProjections` is not something to reach for casually.
 */
// Wider than the interface it implements, because every public entry point
// takes the mutex and delegates to a private body that must not. The
// constructor is wide for a related reason: each dao and seam is separately
// replaceable in tests, and folding them into a holder would hide what this
// depends on rather than reduce it.
@Suppress("TooManyFunctions", "LongParameterList")
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
internal class OfflineFirstHabitRepository @Inject constructor(
    private val database: GawiDatabase,
    private val events: EventDao,
    private val readModel: ReadModelDao,
    private val meta: ProjectionMetaDao,
    private val writer: ProjectionWriter,
    private val codec: EventCodec,
    private val ids: UuidV7Generator,
    private val clock: DeviceClock,
    private val settings: SettingsSource,
    private val projectionListener: ProjectionListener,
) : HabitRepository {

    private val mutex = Mutex()

    /** Null until the log has been folded; only ever touched under [mutex]. */
    private var state: ProjectedState? = null

    override suspend fun createHabit(metadata: HabitMetadata): CommandResult<HabitId> = mutex.withLock {
        val before = initialised()
        // Minted before validating, so a rejection burns an id. Ids are not
        // scarce and the generator's monotonicity does not care about gaps.
        val habitId = HabitId(ids.next().value)
        when (val decision = Commands.createHabit(habitId, metadata)) {
            is CommandResult.Rejected -> decision

            is CommandResult.Accepted -> {
                appendLocked(before, listOf(decision.payload))
                CommandResult.Accepted(habitId)
            }
        }
    }

    override suspend fun updateHabit(habitId: HabitId, metadata: HabitMetadata): CommandResult<Unit> =
        commit { Commands.updateHabit(it, habitId, metadata).asPayloads() }

    override suspend fun archiveHabit(habitId: HabitId): CommandResult<Unit> = commit { Commands.archiveHabit(it, habitId).asPayloads() }

    override suspend fun unarchiveHabit(habitId: HabitId): CommandResult<Unit> =
        commit { Commands.unarchiveHabit(it, habitId).asPayloads() }

    override suspend fun addCompletion(habitId: HabitId, logicalDate: LocalDate, note: String?): CommandResult<Unit> = commit { state ->
        Commands.addCompletion(state, habitId, logicalDate, todayFor(clock.now(), settings.current()), note)
            .asPayloads()
    }

    // Already a list: undo tombstones every live add in the cell, and they all
    // belong to the one transaction.
    override suspend fun undoCompletion(habitId: HabitId, logicalDate: LocalDate): CommandResult<Unit> =
        commit { Commands.undoCompletion(it, habitId, logicalDate) }

    /**
     * The UI addresses a cell by habit and date; the domain addresses it by
     * the id of the add the note hangs off. Resolving to the newest live add
     * is deterministic and, at this layer, immaterial: the displayed note is
     * whichever note write has the latest stamp of its own, and undo tombstones
     * every live add in the cell regardless of which one a note chose.
     */
    override suspend fun updateNote(habitId: HabitId, logicalDate: LocalDate, text: String): CommandResult<Unit> = commit { state ->
        when {
            // Repeated from the domain on purpose. updateCompletionNote checks
            // archived before liveness, so an archived habit reports
            // HabitIsArchived whatever the completion's state — but reaching it
            // at all needs an event id this layer can only get from a live add.
            // Resolving first without this would make the error depend on
            // completion state, which is exactly what that ordering exists to
            // prevent, and would disagree with undoCompletion right beside it.
            state.isArchived(habitId) -> CommandResult.Rejected(CommandError.HabitIsArchived)

            else -> when (val parent = state.liveAddIds(habitId, logicalDate).maxOrNull()) {
                // No live add: nothing to annotate, and no id for the domain.
                null -> CommandResult.Rejected(CommandError.CompletionNotFound)

                else -> Commands.updateCompletionNote(state, parent, text).asPayloads()
            }
        }
    }

    override fun observeToday(): Flow<TodaySnapshot> = flow {
        ensureProjectionCurrent()
        emitAll(
            readContext()
                .flatMapLatest { (today, weekStart) ->
                    sweepStreaks(today, weekStart)
                    val week = weekOf(today, weekStart)
                    val rows = readModel
                        .observeToday(today.toString(), week.first.toString(), week.second.toString())
                        .map { rows -> rows.map { it.toDomain() } }
                    combine(rows, moodContext(today)) { habits, mood ->
                        TodaySnapshot(habits, today, mood.now, mood.reminderTime, mood.dayCutoff, weekStart)
                    }
                }
                .distinctUntilChanged(),
        )
    }

    /**
     * The lean single-habit read, for a caller that wants no date and no cells.
     *
     * Sweeps the stale streak on the way through, exactly as the detail read
     * does: both are single-habit reads and neither may hand back a streak
     * computed for an older day.
     */
    override fun observeHabit(habitId: HabitId): Flow<TodayHabit?> = flow {
        ensureProjectionCurrent()
        emitAll(
            readContext()
                .flatMapLatest { (today, weekStart) ->
                    sweepStreaks(today, weekStart)
                    habitRow(habitId, today, weekStart)
                }
                .distinctUntilChanged(),
        )
    }

    /**
     * The habit and its recent cells, read against one date.
     *
     * Both halves come off the same [readContext] emission, so the strip's
     * window, the completions in it and the streak beside them are all the same
     * day's answer. Reading them as two subscriptions would let a rollover land
     * between the two and pair a fresh window with yesterday's habit.
     *
     * `combine` rather than two `flow`s for the same reason `observeToday`
     * combines its rows with the mood context: one emission per change, and no
     * intermediate state where one half has updated and the other has not. It is
     * also why [observeHabit] exists separately — `combine` waits for every
     * source, so a caller with no use for the cells would still wait for them.
     */
    override fun observeHabitDetail(habitId: HabitId): Flow<HabitDetail?> = flow {
        ensureProjectionCurrent()
        emitAll(
            readContext()
                .flatMapLatest { (today, weekStart) ->
                    sweepStreaks(today, weekStart)
                    val (from, to) = HabitDetail.stripWindow(today)
                    val recent = readModel
                        .observeCompletedDates(habitId.value, from.toString(), to.toString())
                        .map { rows -> rows.associate { LocalDate.parse(it.logicalDate) to it.note } }
                    combine(habitRow(habitId, today, weekStart), recent) { row, cells ->
                        // Null habit means null detail: there is no date worth
                        // carrying for a habit that is not there.
                        row?.let { HabitDetail(habit = it, today = today, recent = cells) }
                    }
                }
                .distinctUntilChanged(),
        )
    }

    /**
     * The row both single-habit reads are built on.
     *
     * Takes the date and week start already resolved by the caller's
     * [readContext] emission rather than resolving its own, which is what keeps
     * the streak, the week count and — for detail — the strip window all one
     * day's answer.
     */
    private fun habitRow(habitId: HabitId, today: LocalDate, weekStart: DayOfWeek): Flow<TodayHabit?> {
        val week = weekOf(today, weekStart)
        return readModel
            .observeHabit(habitId.value, today.toString(), week.first.toString(), week.second.toString())
            .map { row -> row?.toDomain() }
    }

    override fun observeAllHabits(): Flow<List<HabitState>> = flow {
        ensureProjectionCurrent()
        emitAll(
            readModel
                .observeAllHabits()
                .map { rows -> rows.map { it.toDomain() } }
                .distinctUntilChanged(),
        )
    }

    override fun observeCompletedDates(habitId: HabitId, from: LocalDate, to: LocalDate): Flow<Map<LocalDate, String?>> = flow {
        ensureProjectionCurrent()
        emitAll(
            readModel
                .observeCompletedDates(habitId.value, from.toString(), to.toString())
                .map { rows -> rows.associate { LocalDate.parse(it.logicalDate) to it.note } }
                .distinctUntilChanged(),
        )
    }

    override fun observeCompletionDatesByHabit(from: LocalDate, to: LocalDate): Flow<Map<HabitId, Set<LocalDate>>> = flow {
        ensureProjectionCurrent()
        emitAll(
            readModel
                .observeCompletionsInRange(from.toString(), to.toString())
                .map { rows ->
                    rows.groupBy(
                        keySelector = { HabitId(it.habitId) },
                        valueTransform = { LocalDate.parse(it.logicalDate) },
                    ).mapValues { (_, dates) -> dates.toSet() }
                }
                .distinctUntilChanged(),
        )
    }

    /**
     * No `ensureProjectionCurrent()`, and that is not an oversight: this reads
     * no derived row. It answers from the settings and the clock, so there is
     * nothing a stale projection could make it get wrong — and calling it would
     * mean asking the date could trigger a replay.
     */
    override fun observeReadContext(): Flow<ReadContext> = readContext()

    override fun observeTagEffort(from: LocalDate, to: LocalDate): Flow<List<TagEffort>> = flow {
        ensureProjectionCurrent()
        emitAll(
            readModel
                .observeTagEffort(from.toString(), to.toString())
                .map { rows -> rows.map { TagEffort(tag = it.tag, completions = it.completions) } }
                .distinctUntilChanged(),
        )
    }

    override suspend fun refreshStreaks() {
        val settings = settings.current()
        sweepStreaks(todayFor(clock.now(), settings), settings.weekStart)
    }

    /**
     * The sweep itself, over a date and week start the caller has already
     * resolved.
     *
     * Separate from [refreshStreaks] so the read path can sweep without reading
     * the settings again. That matters twice. The observers already hold the
     * date and week start this is for, so re-deriving them here let the sweep
     * and the query it runs beneath disagree if a boundary fell between the
     * two. And [SettingsSource.current] deliberately refuses to answer when the
     * store is unreadable, which is right for a command and would take a screen
     * down — so the read path must not call it.
     */
    private suspend fun sweepStreaks(today: LocalDate, weekStart: DayOfWeek) = mutex.withLock {
        val current = initialised()
        database.withTransaction {
            writer.refreshStreaks(current, today, weekStart)
        }
    }

    override suspend fun rebuildProjections() = mutex.withLock {
        rebuildLocked(initialised())
    }

    /**
     * Merges events that came from outside — an import today, sync later —
     * and returns how many of them the log did not already hold.
     *
     * Not on [HabitRepository]. That interface promises nothing above it knows
     * events exist, and a merge is not something a user does to a habit; it is
     * the store being handed part of another store. It also never touches
     * `Commands`: the three-day retroactive window is a *command* rule, and an
     * import carries months-old events that replay must accept unconditionally
     * (architecture §5).
     *
     * **A refold, not a rebuild.** `rebuildProjections()` replays the
     * *in-memory* state, which after an out-of-band insert is precisely the
     * thing that is wrong — `state` is non-null, so `initialised()`
     * short-circuits and the command authority stays behind the log for the
     * life of the process. Every derived table would still look right, and the
     * first undo on an imported cell would report `CompletionNotFound` against
     * a row the user is looking at.
     *
     * The refold is skipped outright when the insert added nothing and this
     * process has already folded — see [mergeLocked]. It cannot change what it
     * would produce, and re-importing one's own file is the common case rather
     * than the corner.
     */
    internal suspend fun mergeEvents(incoming: List<EncodedEvent>): Int = mutex.withLock {
        val settings = settings.current()
        mergeLocked(incoming.map { it.toEntity() }, todayFor(clock.now(), settings), settings.weekStart)
    }

    /**
     * The insert, the refold and the rebuild, as one unit in both senses.
     *
     * One *transaction*, because two would let a process death land between
     * them: the log would hold the imported events, the derived tables would
     * not, and `projection_meta` would still read the current version — so the
     * repair in `initialised()` finds no mismatch and never runs. Permanently
     * wrong tables under a matching version is the worst of the available
     * failures.
     *
     * One *non-cancellable* unit, for the reason `appendLocked` records and
     * more so here: cancellation landing between the commit and the publish
     * would leave `state` short of the entire import, with nothing to notice.
     */
    private suspend fun mergeLocked(rows: List<EventEntity>, today: LocalDate, weekStart: DayOfWeek): Int = withContext(NonCancellable) {
        // Read out here rather than inside the transaction. Not for visibility
        // — the caller holds the mutex and `state` is only ever touched under
        // it — but because the guard below is a claim about this *process*,
        // not about the rows the transaction opens over.
        val carried = state
        val (added, refolded) = database.withTransaction {
            val inserted = events.insertMerging(rows).count { it != ROW_NOT_INSERTED }
            // Nothing inserted means the log is exactly what `carried` was
            // folded from. `events` has no update and no delete, its two
            // inserts are both behind this mutex, the repository is a process
            // singleton and there is no second process, so no writer can have
            // moved it in between. The fold is a function of the log, and the
            // derived tables were written from that fold.
            //
            // `carried != null` is the load-bearing half, not a null-safety
            // formality. A null state is a process that has not been through
            // `initialised()`, so the projection version has *not* been checked
            // and the tables in front of it may have been written by a build
            // whose rules have since changed — and the long path is the only
            // one that upserts PROJECTION_VERSION. That is exactly the
            // cold-start restore path, `pm clear` then import, so it has to
            // take the long road. Do not "simplify" by calling `initialised()`
            // first either: that folds the largest log there will ever be
            // twice.
            //
            // Deliberately *not* repaired here: `today` and `weekStart`. Only
            // the streak rows depend on them, they carry the date they were
            // computed for, no query binds it, and the read path sweeps on
            // every collection and every week-start edit. An import that
            // changed nothing is not a moment at which "when" moved, and
            // making it one would turn this row into a streak-repair button
            // nobody asked for.
            val folded = if (inserted == 0 && carried != null) carried else refoldLocked(today, weekStart)
            inserted to folded
        }
        // A self-assignment on the short path, and left that way on purpose:
        // one exit, and the publish stays visibly paired with the commit.
        state = refolded
        // An import is the other way the read model moves, so it notifies too.
        // Unconditionally, including when nothing was inserted: the guard above
        // is about whether the *fold* can be reused, and a widget that is
        // already correct is cheap to redraw, where one left stale is silent.
        announceProjectionChanged()
        added
    }

    /**
     * Reads the whole log, folds it, and writes the derived tables from
     * scratch.
     *
     * Without a transaction of its own, like [writeRebuild] and for the same
     * reason: the caller has to commit the insert and this as one unit.
     */
    private suspend fun refoldLocked(today: LocalDate, weekStart: DayOfWeek): ProjectedState {
        // Read on this dispatcher and fold off it. A dao call from inside the
        // switched context would escape Room's transaction dispatcher, which
        // is a deadlock rather than a slow path.
        val stored = events.loadAll()
        val folded = withContext(Dispatchers.Default) { Projector.rebuild(stored.map { it.toDomain(codec) }) }
        writeRebuild(folded, today, weekStart)
        return folded
    }

    /**
     * Folds the log if that has not happened yet, and repairs the derived
     * tables if they were written by a different projection version.
     */
    private suspend fun initialised(): ProjectedState {
        state?.let { return it }
        // Decoding the log and folding it are both CPU work, and the fold is
        // quadratic. A suspend Room query resumes on the caller's dispatcher,
        // so without this the whole start-up cost lands wherever the collector
        // runs — which for a ViewModel collecting observeToday is the main
        // thread.
        val folded = withContext(Dispatchers.Default) {
            Projector.rebuild(events.loadAll().map { it.toDomain(codec) })
        }
        // Published only after any repair succeeds. Assigning first would mean
        // a rebuild that failed part-way (a disk error mid-transaction) left
        // the version mismatch unrepaired *and* unnoticed, because every later
        // call would short-circuit on a non-null state and never look again.
        if (meta.projectionVersion() != PROJECTION_VERSION) rebuildLocked(folded)
        state = folded
        return folded
    }

    /**
     * Initialisation for the read path.
     *
     * Reads have to trigger it too, not just commands. If only a command could,
     * someone opening the app after a projection-version bump would sit looking
     * at stale rows until they happened to tap something.
     */
    private suspend fun ensureProjectionCurrent() {
        mutex.withLock { initialised() }
    }

    /**
     * Everything a read query binds, and nothing else.
     *
     * Exactly these two, rather than the settings and the date: the cutoff
     * decides which day is "today" and when the next boundary falls, but it does
     * that inside [logicalDates] below, and no query binds it. Carrying the whole
     * of [UserSettings] out of here would mean carrying a reminder time that the
     * dedupe below is entitled to leave stale, and the mascot's `nearBoundary`
     * now does read one. That is why the mood takes its settings from
     * [moodContext] instead: this dedupe must keep swallowing a reminder-time
     * edit, or every open screen re-runs the streak sweep and cancels its query
     * over a setting no query binds.
     *
     * Both observers share this so the two cannot drift apart. Holding the
     * settings for the life of a collection would be worse than it looks: a
     * stale cutoff would keep answering with the old day indefinitely rather
     * than correcting itself overnight — while the streak rows joined into the
     * same query, recomputed by the sweep, had already moved to the new setting.
     *
     * The streak sweep deliberately does *not* live here. It belongs inside the
     * downstream `flatMapLatest`, which cancels the previous query before
     * running the new block — so by the time new streak rows are committed, no
     * query bound to the old day is still subscribed. Sweeping here instead
     * would write those rows while the previous query was still live, and Room
     * invalidates asynchronously, so that query could re-emit yesterday's
     * completion state paired with today's streak. `distinctUntilChanged` is
     * downstream of that and would not filter it.
     */
    private fun readContext(): Flow<ReadContext> = settings
        .observe()
        // A reminder-time edit changes nothing here, and must not cancel the
        // live query and re-run the streak sweep under every open screen.
        .distinctUntilChanged { old, new -> old.dayCutoff == new.dayCutoff && old.weekStart == new.weekStart }
        // The cutoff has to reach this far, even though it goes no further: a
        // new one restarts the boundary timer on the new schedule.
        .flatMapLatest { current -> logicalDates(current).map { ReadContext(it, current.weekStart) } }
        // A wake that does not actually change what a query binds — clock skew,
        // a DST shift, a cutoff edit that leaves "today" where it was — must not
        // churn the query underneath an open screen. Emitting only real changes
        // is also what stops the sweep below running on every wake.
        .distinctUntilChanged()

    /**
     * The mood inputs no query binds: the wall clock, and the two thresholds
     * `nearBoundary` reads.
     *
     * A second subscription to the settings, deliberately. [readContext]'s
     * dedupe is entitled to drop a reminder-time edit — it has to, or every open
     * screen re-runs the streak sweep over a setting no query binds — so
     * anything downstream of it carries a stale reminder time by construction.
     * This is the fresh read, and it sits *inside* the query's `flatMapLatest`
     * rather than beside it, so an edit here reaches the snapshot through
     * `combine` without re-entering the block that sweeps.
     *
     * The week start is not read here. That one is the outer context's, and a
     * second independently-deduped copy of a value the query is bound to is
     * exactly the disagreement [readContext] exists to prevent.
     */
    private fun moodContext(today: LocalDate): Flow<MoodContext> = settings
        .observe()
        .distinctUntilChanged { old, new -> old.dayCutoff == new.dayCutoff && old.reminderTime == new.reminderTime }
        .flatMapLatest { current -> reminderTicks(today, current).map { MoodContext(it, current.reminderTime, current.dayCutoff) } }

    /** The mood's half of a reading: wall clock and thresholds, none of it bound by a query. */
    private data class MoodContext(val now: LocalDateTime, val reminderTime: LocalTime, val dayCutoff: LocalTime)

    /**
     * Now, and now again the moment [today]'s reminder threshold passes.
     *
     * That threshold is the only instant strictly inside a logical day at which
     * the mood changes with no data change at all, which is why the mood needs a
     * clock of its own and a query does not. Shaped like [logicalDates] on
     * purpose, with one difference: this one *completes* once the threshold is
     * behind us, so an evening spent looking at the screen holds no timer. The
     * upper edge of `nearBoundary` is the day boundary, and [logicalDates]
     * already wakes for that — a wake that tears this whole block down and
     * builds it again for the new date.
     *
     * The clock is read here and nowhere else on this path. Sampling it in the
     * `combine` above instead would mint a fresh `now` on every Room
     * invalidation, which stops `distinctUntilChanged` deduping anything. It is
     * sound to sample only here because `nearBoundary`'s two edges are the
     * reminder instant and the day boundary, and both have a ticker: any `now`
     * inside an interval gives the same answer as any other.
     */
    private fun reminderTicks(today: LocalDate, settings: UserSettings): Flow<LocalDateTime> = flow {
        while (true) {
            val now = clock.now()
            emit(LocalDateTime.ofInstant(now, clock.zone()))
            val wait = millisUntilReminder(now, today, settings) ?: break
            delay(wait)
        }
    }

    /** Millis until [today]'s reminder threshold, or null once it is behind us. */
    private fun millisUntilReminder(now: Instant, today: LocalDate, settings: UserSettings): Long? {
        val at = reminderOn(today, settings.reminderTime, settings.dayCutoff).atZone(clock.zone()).toInstant()
        return (at.toEpochMilli() - now.toEpochMilli()).takeIf { it > 0 }
    }

    private suspend fun rebuildLocked(current: ProjectedState) {
        val settings = settings.current()
        val today = todayFor(clock.now(), settings)
        database.withTransaction { writeRebuild(current, today, settings.weekStart) }
    }

    /**
     * The drop-and-replay itself, without a transaction of its own, so a
     * caller that has to write something else in the same one can.
     */
    private suspend fun writeRebuild(current: ProjectedState, today: LocalDate, weekStart: DayOfWeek) {
        writer.rebuild(current, today, weekStart)
        meta.upsert(ProjectionMetaEntity(projectionVersion = PROJECTION_VERSION))
    }

    private suspend fun commit(decide: suspend (ProjectedState) -> CommandResult<List<EventPayload>>): CommandResult<Unit> =
        mutex.withLock {
            val before = initialised()
            when (val decision = decide(before)) {
                is CommandResult.Rejected -> decision

                is CommandResult.Accepted -> {
                    appendLocked(before, decision.payload)
                    CommandResult.Accepted(Unit)
                }
            }
        }

    /**
     * Stamps envelopes, appends them and moves the derived rows, all in one
     * transaction. The in-memory state is published only once that commits, so
     * a failed write cannot leave the command authority ahead of the log.
     */
    private suspend fun appendLocked(before: ProjectedState, payloads: List<EventPayload>) {
        val now = clock.now()
        val offsetMinutes = clock.zone().rules.getOffset(now).totalSeconds / SECONDS_PER_MINUTE
        val stamped = payloads.map { Event(ids.next(), now, offsetMinutes, it) }
        val after = stamped.fold(before, Projector::apply)
        val settings = settings.current()

        // The commit and the publish are one unit, and cancellation must not
        // land between them. `withContext` — which `withTransaction` is built
        // on — throws on resume if the caller's job was cancelled while the
        // block ran, *even when the block finished*. Without NonCancellable a
        // tap cancelled mid-transaction could therefore commit the event and
        // never advance the in-memory state. That does not self-heal: `state`
        // is non-null by then, so `initialised()` short-circuits for the life
        // of the process, leaving the command authority a whole event behind
        // the log. An undo on that cell would report CompletionNotFound
        // against a row the user can still see, and the next `applyDelta`
        // would diff from a stale baseline. The KDoc above reasons about the
        // opposite direction; this is the one that bites.
        withContext(NonCancellable) {
            database.withTransaction {
                events.insertAll(stamped.map { it.toEntity(codec) })
                writer.applyDelta(before, after, stamped, todayFor(now, settings), settings.weekStart)
            }
            state = after
            // Inside the non-cancellable region, not after it: this is the one
            // place responsible for keeping a widget current (architecture §4),
            // and a tap whose scope dies right after the commit is exactly the
            // case that leaves the home screen showing the opposite of the log.
            // The same lesson the zero-byte export taught, in the direction
            // where NonCancellable can actually help — the work has started.
            //
            // It is also inside the mutex, and what it awaits is not free: the
            // Glance implementation does a DataStore read and a WorkManager
            // enqueue, so a slow session start delays the next command. Accepted
            // rather than unnoticed. Taps are serialised through this mutex
            // anyway and the user has just tapped, whereas notifying outside the
            // lock means doing it at every committing call site and losing the
            // single-place property architecture §4 relies on.
            announceProjectionChanged()
        }
    }

    /**
     * Tells the listener the read model moved, and **cannot fail the write that
     * moved it.**
     *
     * The guard is here rather than only in the implementation, because the
     * invariant belongs to the call site: this runs after the commit, inside
     * `NonCancellable`, so a throw would propagate out of a command that had
     * already succeeded and report a written event as a failure. That is not
     * hypothetical — a `NoClassDefFoundError` out of the Glance listener did
     * exactly that, and left a habit editor looking as though Save were dead
     * (docs/ux/widget.md §5). Fixing it only inside that implementation left the
     * rule enforced by convention, so a second listener, or an edit narrowing
     * its catch, would bring the bug back.
     *
     * `runCatching` rather than a typed catch: `Error` is the class that escaped
     * last time. Nothing is logged — `:core:data` has no logger by design — so
     * an implementation that wants to report a failed push logs it itself, which
     * `GlanceProjectionListener` does.
     */
    private suspend fun announceProjectionChanged() {
        runCatching { projectionListener.onProjectionChanged() }
    }

    private fun todayFor(now: Instant, settings: UserSettings): LocalDate = logicalDate(now, settings.dayCutoff, clock.zone())

    private fun weekOf(today: LocalDate, weekStart: DayOfWeek): Pair<LocalDate, LocalDate> {
        val start = weekStartOn(today, weekStart)
        return start to start.plusWeeks(1).minusDays(1)
    }

    /**
     * The current logical date, re-emitted when the day boundary passes.
     *
     * This is what makes rollover invisible above: the date is a bind
     * parameter, so a new day is a re-query rather than anything stored going
     * stale.
     */
    private fun logicalDates(settings: UserSettings): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            emit(todayFor(now, settings))
            delay(millisUntilBoundary(now, settings))
        }
    }

    private fun millisUntilBoundary(now: Instant, settings: UserSettings): Long {
        val zoned = now.atZone(clock.zone())
        val todayBoundary = zoned.toLocalDate().atTime(settings.dayCutoff).atZone(clock.zone())
        val next = if (todayBoundary.toInstant() > now) todayBoundary else todayBoundary.plusDays(1)
        return next.toInstant().toEpochMilli() - now.toEpochMilli()
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}

/** Lifts a single accepted payload into the list [OfflineFirstHabitRepository.commit] appends. */
private fun <T : EventPayload> CommandResult<T>.asPayloads(): CommandResult<List<EventPayload>> = when (this) {
    is CommandResult.Accepted -> CommandResult.Accepted(listOf(payload))
    is CommandResult.Rejected -> this
}
