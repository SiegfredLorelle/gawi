package com.gawi.core.data.testsupport

import androidx.room.Room
import com.gawi.core.data.backup.AppVersion
import com.gawi.core.data.backup.EventLogArchive
import com.gawi.core.data.backup.ImportResult
import com.gawi.core.data.db.GawiDatabase
import com.gawi.core.data.db.dao.CompletionProjectionDao
import com.gawi.core.data.db.entity.CompletionEntity
import com.gawi.core.data.db.entity.EventEntity
import com.gawi.core.data.db.entity.HabitEntity
import com.gawi.core.data.db.entity.HabitStreakEntity
import com.gawi.core.data.projection.ProjectionListener
import com.gawi.core.data.projection.ProjectionWriter
import com.gawi.core.data.repository.OfflineFirstHabitRepository
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.data.time.DeviceClock
import com.gawi.core.domain.id.UuidV7Generator
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.serialization.export.EventLogCodec
import com.gawi.core.domain.time.logicalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
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
 * Counts the pushes the widget would have received.
 *
 * The seam exists because Glance cannot observe Room (architecture §4), and the
 * failure it guards against is silent: a widget that never hears about a commit
 * looks exactly like one nobody has placed. So the notification is asserted
 * here rather than left to a device observation.
 */
class RecordingProjectionListener : ProjectionListener {

    var calls = 0
        private set

    override suspend fun onProjectionChanged() {
        calls++
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
 * A completion DAO that runs [onUpsert] before each write.
 *
 * The only seam a test has *inside* the projection transaction. `appendLocked`
 * commits the event and publishes the in-memory state as one unit, and the
 * window between them is reachable from nowhere else: cancelling before the
 * transaction aborts it, and cancelling after it is too late. Delegates
 * everything else, so the real writer still does the real work.
 */
internal class HookedCompletionDao(private val delegate: CompletionProjectionDao, private val onUpsert: () -> Unit) :
    CompletionProjectionDao {

    override suspend fun upsert(completion: CompletionEntity) {
        onUpsert()
        delegate.upsert(completion)
    }

    override suspend fun find(habitId: String, logicalDate: String): CompletionEntity? = delegate.find(habitId, logicalDate)

    override suspend fun delete(habitId: String, logicalDate: String) = delegate.delete(habitId, logicalDate)

    override suspend fun deleteAll() = delegate.deleteAll()

    override suspend fun all(): List<CompletionEntity> = delegate.all()
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
    val archive: EventLogArchive,
    val clock: FakeDeviceClock,
    val settings: FakeSettingsSource,
    val listener: RecordingProjectionListener,
) {

    suspend fun today(): LocalDate = logicalDate(clock.now(), settings.settings.dayCutoff, clock.zone())

    suspend fun snapshot(): TableSnapshot = TableSnapshot(
        habits = database.habitProjectionDao().all(),
        completions = database.completionProjectionDao().all(),
        streaks = database.habitStreakDao().all(),
    )

    fun close() = database.close()

    /** The whole log as an export file. */
    suspend fun exportText(): String = archive.encode().decodeToString()

    /** Imports [text] as if it had come off a document the user picked. */
    suspend fun import(text: String): ImportResult = archive.import(ByteArrayInputStream(text.toByteArray()))

    /** Every event row, for the assertions that are about the log itself. */
    suspend fun log(): List<EventEntity> = database.eventDao().loadAll()

    companion object {
        /** Fixed, so an exported envelope is comparable between two runs. */
        const val APP_VERSION = "0.0.0-test"

        fun create(
            clock: FakeDeviceClock = FakeDeviceClock(),
            settings: FakeSettingsSource = FakeSettingsSource(),
            idSeed: Long = 1,
            onCompletionWrite: (() -> Unit)? = null,
            listener: RecordingProjectionListener = RecordingProjectionListener(),
        ): TestStore = createOver(
            database = Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GawiDatabase::class.java)
                .build(),
            clock = clock,
            settings = settings,
            idSeed = idSeed,
            onCompletionWrite = onCompletionWrite,
            listener = listener,
        )

        /**
         * A second repository over a database that already exists — a restart,
         * as far as the repository is concerned, since it folds the log again
         * and re-checks the projection version.
         *
         * `LongParameterList` is suppressed at the declaration, following the
         * precedent the feature modules' `Fixtures.kt` builders set: a fixture
         * builder's parameters are its whole point, every one is defaulted, and
         * a test names only the seam it is about. detekt's threshold fires *at*
         * six, and the sixth is the projection listener — folding two of these
         * into a holder to get under the line would hide what a test store is
         * made of.
         */
        @Suppress("LongParameterList")
        fun createOver(
            database: GawiDatabase,
            clock: FakeDeviceClock = FakeDeviceClock(),
            settings: FakeSettingsSource = FakeSettingsSource(),
            idSeed: Long = 1,
            onCompletionWrite: (() -> Unit)? = null,
            listener: RecordingProjectionListener = RecordingProjectionListener(),
        ): TestStore {
            val completions = database.completionProjectionDao()
            val writer = ProjectionWriter(
                habits = database.habitProjectionDao(),
                completions = onCompletionWrite?.let { HookedCompletionDao(completions, it) } ?: completions,
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
                projectionListener = listener,
            )
            val archive = EventLogArchive(
                events = database.eventDao(),
                store = repository,
                codec = EventLogCodec(EventCodec()),
                clock = clock,
                appVersion = AppVersion(APP_VERSION),
            )
            return TestStore(database, repository, archive, clock, settings, listener)
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
