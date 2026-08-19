package com.gawi.core.data.testsupport

import androidx.room.Room
import com.gawi.core.data.db.GawiDatabase
import com.gawi.core.data.db.entity.CompletionEntity
import com.gawi.core.data.db.entity.HabitEntity
import com.gawi.core.data.db.entity.HabitStreakEntity
import com.gawi.core.data.projection.ProjectionWriter
import com.gawi.core.data.repository.OfflineFirstHabitRepository
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.data.time.DeviceClock
import com.gawi.core.domain.id.UuidV7Generator
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.time.logicalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.RuntimeEnvironment
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random

/** A clock the test moves by hand, so day rollover is an assignment. */
class FakeDeviceClock(var instant: Instant = Instant.parse("2026-08-17T09:00:00Z"), private var zone: ZoneId = ZoneOffset.UTC) :
    DeviceClock {

    override fun now(): Instant = instant

    override fun zone(): ZoneId = zone

    fun moveTo(date: LocalDate, hour: Int = MORNING) {
        instant = date.atTime(hour, 0).atZone(zone).toInstant()
    }

    fun advanceDays(days: Long) {
        instant = instant.atZone(zone).plusDays(days).toInstant()
    }

    fun moveToZone(newZone: ZoneId) {
        zone = newZone
    }

    private companion object {
        const val MORNING = 9
    }
}

/**
 * Settings a test can edit mid-collection. Backed by a [MutableStateFlow] so an
 * assignment reaches an already-running observer, which is the only way to
 * catch a reader that captured the settings instead of following them.
 */
class FakeSettingsSource(initial: UserSettings = UserSettings()) : SettingsSource {

    private val state = MutableStateFlow(initial)

    /**
     * Makes [current] fail the way the real store does when the preferences file
     * cannot be read, leaving [observe] working. Set it to assert that a reader
     * does not reach for the command path's read.
     */
    var currentFails: Boolean = false

    var settings: UserSettings
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun observe(): Flow<UserSettings> = state

    override suspend fun current(): UserSettings = if (currentFails) throw IOException("settings unreadable") else super.current()

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        state.value = transform(state.value)
    }
}

/**
 * An in-memory database wired to a real repository — the same object graph
 * `DataModule` builds, minus Hilt.
 *
 * The id generator is seeded so a failing run is reproducible: ids are the one
 * thing in this store that would otherwise differ between two identical runs,
 * and they order the log.
 */
internal class TestStore private constructor(
    val database: GawiDatabase,
    val repository: OfflineFirstHabitRepository,
    val clock: FakeDeviceClock,
    val settings: FakeSettingsSource,
) {

    suspend fun today(): LocalDate = logicalDate(clock.now(), settings.settings.dayCutoff, clock.zone())

    suspend fun snapshot(): TableSnapshot = TableSnapshot(
        habits = database.habitProjectionDao().all(),
        completions = database.completionProjectionDao().all(),
        streaks = database.habitStreakDao().all(),
    )

    fun close() = database.close()

    companion object {
        fun create(
            clock: FakeDeviceClock = FakeDeviceClock(),
            settings: FakeSettingsSource = FakeSettingsSource(),
            idSeed: Long = 1,
        ): TestStore = createOver(
            database = Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GawiDatabase::class.java)
                .build(),
            clock = clock,
            settings = settings,
            idSeed = idSeed,
        )

        /**
         * A second repository over a database that already exists — a restart,
         * as far as the repository is concerned, since it folds the log again
         * and re-checks the projection version.
         */
        fun createOver(
            database: GawiDatabase,
            clock: FakeDeviceClock = FakeDeviceClock(),
            settings: FakeSettingsSource = FakeSettingsSource(),
            idSeed: Long = 1,
        ): TestStore {
            val writer = ProjectionWriter(
                habits = database.habitProjectionDao(),
                completions = database.completionProjectionDao(),
                streaks = database.habitStreakDao(),
            )
            val repository = OfflineFirstHabitRepository(
                database = database,
                events = database.eventDao(),
                readModel = database.readModelDao(),
                meta = database.projectionMetaDao(),
                writer = writer,
                codec = EventCodec(),
                ids = UuidV7Generator(nowMillis = { clock.now().toEpochMilli() }, random = Random(idSeed)),
                clock = clock,
                settings = settings,
            )
            return TestStore(database, repository, clock, settings)
        }
    }
}

/**
 * Every derived table, in a deterministic order. Comparing two of these is how
 * the rebuild oracle states its assertion.
 */
internal data class TableSnapshot(
    val habits: List<HabitEntity>,
    val completions: List<CompletionEntity>,
    val streaks: List<HabitStreakEntity>,
)
