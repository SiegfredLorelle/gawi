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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
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
 * The fold is off the main thread, and `rebuildProjections` is not something
 * to reach for casually.
 */
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
        when (val parent = state.liveAddIds(habitId, logicalDate).maxOrNull()) {
            // No live add means there is no completion to annotate, and there
            // is no event id to hand the domain command either.
            null -> CommandResult.Rejected(CommandError.CompletionNotFound)

            else -> Commands.updateCompletionNote(state, parent, text).asPayloads()
        }
    }

    override fun observeToday(): Flow<List<TodayHabit>> = flow {
        val current = ensureProjectionCurrent()
        emitAll(
            logicalDates(current)
                .onEach { refreshStreaks() }
                .flatMapLatest { today ->
                    val week = weekOf(today, current)
                    readModel
                        .observeToday(today.toString(), week.first.toString(), week.second.toString())
                        .map { rows -> rows.map { it.toDomain() } }
                }
                .distinctUntilChanged(),
        )
    }

    override fun observeHabit(habitId: HabitId): Flow<TodayHabit?> = flow {
        val current = ensureProjectionCurrent()
        emitAll(
            logicalDates(current)
                .flatMapLatest { today ->
                    val week = weekOf(today, current)
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

    override suspend fun refreshStreaks() = mutex.withLock {
        val current = initialised()
        val settings = settings.current()
        database.withTransaction {
            writer.refreshStreaks(current, todayFor(clock.now(), settings), settings.weekStart)
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
        val log = events.loadAll().map { it.toDomain(codec) }
        val folded = Projector.rebuild(log)
        state = folded
        if (meta.projectionVersion() != PROJECTION_VERSION) rebuildLocked(folded)
        return folded
    }

    /**
     * Initialisation for the read path.
     *
     * Reads have to trigger it too, not just commands. If only a command could,
     * someone opening the app after a projection-version bump would sit looking
     * at stale rows until they happened to tap something.
     */
    private suspend fun ensureProjectionCurrent(): UserSettings = mutex.withLock {
        initialised()
        settings.current()
    }

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

        database.withTransaction {
            events.insertAll(stamped.map { it.toEntity(codec) })
            writer.applyDelta(before, after, stamped, todayFor(now, settings), settings.weekStart)
        }
        state = after

        // The widget refresh belongs here — architecture §4 makes this the one
        // place responsible for keeping Glance current, because widgets do not
        // observe Room. There is no :widget module yet, and :core:data must not
        // depend on one when there is, so it will arrive as a callback the app
        // implements rather than a direct call.
    }

    private fun todayFor(now: Instant, settings: UserSettings): LocalDate = logicalDate(now, settings.dayCutoff, clock.zone())

    private fun weekOf(today: LocalDate, settings: UserSettings): Pair<LocalDate, LocalDate> {
        val start = today.with(TemporalAdjusters.previousOrSame(settings.weekStart))
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
