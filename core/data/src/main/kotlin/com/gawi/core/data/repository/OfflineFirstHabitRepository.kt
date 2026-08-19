package com.gawi.core.data.repository

import androidx.room.withTransaction
import com.gawi.core.data.PROJECTION_VERSION
import com.gawi.core.data.db.GawiDatabase
import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.db.dao.ProjectionMetaDao
import com.gawi.core.data.db.dao.ReadModelDao
import com.gawi.core.data.db.entity.ProjectionMetaEntity
import com.gawi.core.data.db.mapper.toDomain
import com.gawi.core.data.db.mapper.toEntity
import com.gawi.core.data.model.TodayHabit
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
import com.gawi.core.domain.projection.ProjectedState
import com.gawi.core.domain.projection.Projector
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.time.logicalDate
import com.gawi.core.domain.time.weekStartOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
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

    override fun observeToday(): Flow<List<TodayHabit>> = flow {
        ensureProjectionCurrent()
        emitAll(
            readContext()
                .flatMapLatest { (settings, today) ->
                    sweepStreaks(today, settings.weekStart)
                    val week = weekOf(today, settings)
                    readModel
                        .observeToday(today.toString(), week.first.toString(), week.second.toString())
                        .map { rows -> rows.map { it.toDomain() } }
                }
                .distinctUntilChanged(),
        )
    }

    override fun observeHabit(habitId: HabitId): Flow<TodayHabit?> = flow {
        ensureProjectionCurrent()
        emitAll(
            readContext()
                .flatMapLatest { (settings, today) ->
                    sweepStreaks(today, settings.weekStart)
                    val week = weekOf(today, settings)
                    readModel
                        .observeHabit(habitId.value, today.toString(), week.first.toString(), week.second.toString())
                        .map { row -> row?.toDomain() }
                }
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
     * The settings and logical date every read query binds, re-emitted whenever
     * either moves — a settings edit, or the day boundary passing.
     *
     * Both observers share this so the two cannot drift apart. Holding the
     * settings for the life of a collection would be worse than it looks: the
     * captured cutoff decides not just how a week is bucketed but which day is
     * "today" and when the next boundary falls, so a stale one would keep
     * answering with the old day indefinitely rather than correcting itself
     * overnight — while the streak rows joined into the same query, recomputed
     * by [refreshStreaks], had already moved to the new setting.
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
    private fun readContext(): Flow<Pair<UserSettings, LocalDate>> = settings
        .observe()
        // Only the two settings the queries actually bind. The reminder time
        // rides along in [UserSettings] for the mascot's benefit and no query
        // reads it, so an edit to it must not cancel the live query and re-run
        // the streak sweep under every open screen. The cost is that the
        // settings reaching the queries below can carry a stale reminder time,
        // which is sound precisely because nothing below reads it.
        .distinctUntilChanged { old, new -> old.dayCutoff == new.dayCutoff && old.weekStart == new.weekStart }
        .flatMapLatest { current -> logicalDates(current).map { current to it } }
        // A boundary wake that does not actually change the date — clock skew,
        // a DST shift, a settings write that changed nothing — must not churn
        // the query underneath an open screen. Emitting only real changes is
        // also what stops the sweep below running on every wake.
        .distinctUntilChanged()

    private suspend fun rebuildLocked(current: ProjectedState) {
        val settings = settings.current()
        val today = todayFor(clock.now(), settings)
        database.withTransaction {
            writer.rebuild(current, today, settings.weekStart)
            meta.upsert(ProjectionMetaEntity(projectionVersion = PROJECTION_VERSION))
        }
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
        }

        // The widget refresh belongs here — architecture §4 makes this the one
        // place responsible for keeping Glance current, because widgets do not
        // observe Room. There is no :widget module yet, and :core:data must not
        // depend on one when there is, so it will arrive as a callback the app
        // implements rather than a direct call.
    }

    private fun todayFor(now: Instant, settings: UserSettings): LocalDate = logicalDate(now, settings.dayCutoff, clock.zone())

    private fun weekOf(today: LocalDate, settings: UserSettings): Pair<LocalDate, LocalDate> {
        val start = weekStartOn(today, settings.weekStart)
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
