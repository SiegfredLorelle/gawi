package com.gawi.core.data.backup

import android.database.sqlite.SQLiteException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.db.entity.EventEntity
import com.gawi.core.data.settings.DataStoreSettingsSource
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.data.settings.settingsDataStore
import com.gawi.core.data.testsupport.FakeDeviceClock
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.domain.testing.metadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * What the app remembers about exports.
 *
 * Robolectric, unlike `DataStoreSettingsSourceTest` next door, because the
 * has-anything-to-lose half of the answer is a real `COUNT(*)` over a real Room
 * database and that needs a `Context`. The preferences half still runs on a
 * [TemporaryFolder], and the two share one file on purpose — which is what the
 * pair of clobber tests below are about.
 */
@RunWith(RobolectricTestRunner::class)
class ExportJournalTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val clock = FakeDeviceClock()
    private val store = TestStore.create(clock = clock)

    @After
    fun tearDown() = store.close()

    /** A store whose reads and writes both fail, which no real DataStore lets a test arrange. */
    private fun throwing(cause: Throwable) = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw cause }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw cause
    }

    private fun TestScope.preferences(name: String = "settings"): DataStore<Preferences> =
        settingsDataStore(scope = backgroundScope) { File(folder.root, "$name.preferences_pb") }

    private fun journalOver(dataStore: DataStore<Preferences>) =
        ExportJournal(dataStore = dataStore, events = store.database.eventDao(), clock = clock)

    @Test
    fun `a store with nothing in it has never exported and has nothing to lose`() = runTest {
        val status = journalOver(preferences()).observe().first()

        assertNull(status.daysSinceExport)
        assertEquals(false, status.hasEvents)
    }

    @Test
    fun `an export just recorded reads as nought days ago`() = runTest {
        val journal = journalOver(preferences())

        journal.record()

        assertEquals(0L, journal.observe().first().daysSinceExport)
    }

    @Test
    fun `hasEvents follows the log rather than the preferences`() = runTest {
        val journal = journalOver(preferences())

        store.repository.createHabit(metadata(name = "read"))

        assertEquals(true, journal.observe().first().hasEvents)
    }

    @Test
    fun `days are whole wall-clock days, not multiples of twenty-four hours`() = runTest {
        // Ninety minutes apart and a day apart at the same time. Counting the
        // gap rather than the dates reads this as nought, which would hold the
        // nudge back by a day for anyone who exports in the evening.
        clock.instant = Instant.parse("2026-08-17T23:00:00Z")
        val journal = journalOver(preferences())
        journal.record()

        clock.instant = Instant.parse("2026-08-18T00:30:00Z")

        assertEquals(1L, journal.observe().first().daysSinceExport)
    }

    @Test
    fun `a month without an export is counted`() = runTest {
        val journal = journalOver(preferences())
        journal.record()

        clock.advanceDays(31)

        assertEquals(31L, journal.observe().first().daysSinceExport)
    }

    /**
     * A stamp dated after today is not trustworthy, and must not be clamped.
     *
     * This is the case where clamping to nought is actively dangerous rather
     * than merely wrong: a device whose clock was ahead when the export happened
     * leaves a stamp that can never count upwards, so "Last exported today"
     * would stick for the life of the install and the nudge would never fire
     * again. Reading it as unknown nudges instead, and the next export replaces
     * the bad stamp.
     */
    @Test
    fun `a stamp dated in the future reads as no stamp at all`() = runTest {
        val journal = journalOver(preferences())
        store.repository.createHabit(metadata(name = "read"))
        journal.record()

        clock.instant = Instant.parse("2026-08-10T09:00:00Z")

        val status = journal.observe().first()
        assertNull(status.daysSinceExport)
        assertEquals(true, status.hasEvents)
    }

    /**
     * **Six minutes of backwards clock correction, across local midnight.**
     *
     * The zone is set deliberately and the test is worthless without it. This
     * used to run at `FakeDeviceClock`'s default UTC, where 20:00Z and 09:00Z
     * share a local date, so it passed while demonstrating nothing about the size
     * of the skew — at `+08:00` the very same instants straddle midnight and the
     * old assertion failed. A reviewer found that; the fix is the one-day
     * tolerance, and this is what pins it.
     */
    @Test
    fun `a backwards clock correction across midnight still reads as today`() = runTest {
        clock.moveToZone(ZoneId.of("+08:00"))
        clock.instant = Instant.parse("2026-08-20T16:03:00Z") // 2026-08-21 00:03 +08:00
        val journal = journalOver(preferences())
        journal.record()

        clock.instant = Instant.parse("2026-08-20T15:57:00Z") // 2026-08-20 23:57 +08:00

        assertEquals(0L, journal.observe().first().daysSinceExport)
    }

    /** The far edge of that tolerance: one day ahead is jitter, two is a wrong clock. */
    @Test
    fun `a stamp two days ahead is refused rather than tolerated`() = runTest {
        val journal = journalOver(preferences())
        store.repository.createHabit(metadata(name = "read"))
        journal.record()

        clock.advanceDays(-2)

        val status = journal.observe().first()
        assertNull(status.daysSinceExport)
        assertEquals(true, status.hasEvents)
    }

    /**
     * **A stamp too old for the UI's `Int` reads as no stamp, not as a wrapped
     * one.**
     *
     * `recencyOf` narrows the day count to an `Int` for the quantity resource.
     * `Long.MIN_VALUE` millis dates the stamp to the year -292275055, which is
     * 106,752,011,854 days and narrows to -622,170,546 — a negative age, which
     * reads as not-overdue and silences the nudge. Only reachable by a garbage
     * value in the app's own preferences file, but it is the same wrong-silence
     * as the other two, and bounding it here is what makes the narrowing safe by
     * construction rather than by luck.
     */
    @Test
    fun `a stamp too old for the day count reads as no stamp at all`() = runTest {
        val dataStore = preferences()
        store.repository.createHabit(metadata(name = "read"))
        dataStore.edit { it[longPreferencesKey("last_exported_at_epoch_milli")] = Long.MIN_VALUE }

        val status = journalOver(dataStore).observe().first()

        assertNull(status.daysSinceExport)
        assertEquals(true, status.hasEvents)
    }

    @Test
    fun `a settings write leaves the export stamp alone`() = runTest {
        // update() assigns all three of its keys unconditionally, so it reads as
        // though it rewrites the file. It does not, and the nudge depends on it.
        val dataStore = preferences()
        journalOver(dataStore).record()

        DataStoreSettingsSource(dataStore).update { it.copy(weekStart = DayOfWeek.SUNDAY) }

        assertEquals(0L, journalOver(dataStore).observe().first().daysSinceExport)
    }

    @Test
    fun `recording an export leaves the settings alone`() = runTest {
        val dataStore = preferences()
        val source = DataStoreSettingsSource(dataStore)
        val edited = UserSettings(dayCutoff = LocalTime.of(3, 0), weekStart = DayOfWeek.SUNDAY)
        source.update { edited }

        journalOver(dataStore).record()

        assertEquals(edited, source.current())
    }

    @Test
    fun `a value stored under that name with another type reads as never exported`() = runTest {
        val dataStore = preferences()
        dataStore.edit { it[stringPreferencesKey("last_exported_at_epoch_milli")] = "yesterday" }

        assertNull(journalOver(dataStore).observe().first().daysSinceExport)
    }

    /**
     * **An unreadable file still nudges a device that has something to lose.**
     *
     * The point of the fallback is what it does *not* do. Answering with a
     * finished "nothing to lose" would have silenced the nudge over a preferences
     * read — the one failure this feature exists to prevent — so the fallback
     * substitutes empty *preferences* and lets the count carry on in the other
     * half of the `combine`. Mutation-checked: make the catch hand back a whole
     * status and this reddens while the empty-log case beside it stays green.
     */
    @Test
    fun `an unreadable file still counts the log, so a device with events is nudged`() = runTest {
        store.repository.createHabit(metadata(name = "read"))

        val status = journalOver(throwing(IOException("unreadable"))).observe().first()

        assertNull(status.daysSinceExport)
        assertEquals(true, status.hasEvents)
    }

    @Test
    fun `an unreadable file over an empty log still says nothing`() = runTest {
        val status = journalOver(throwing(IOException("unreadable"))).observe().first()

        assertNull(status.daysSinceExport)
        assertEquals(false, status.hasEvents)
    }

    /**
     * A bug is not a bad disk. It propagates here and is caught one layer up, so
     * that it cannot hide behind a plausible answer — the same split the settings
     * store makes with the same file.
     */
    @Test
    fun `anything that is not a read failure still propagates`() = runTest {
        val thrown = runCatching {
            journalOver(throwing(IllegalStateException("a bug"))).observe().first()
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }

    /**
     * **An unwritable file must not fail an export that worked.**
     *
     * `edit` reads before it writes, so this throws on the same file the read
     * path degrades over — and letting it out fails an `exportTo` whose document
     * is complete, which the caller reports with copy telling the user to delete
     * a good backup rather than trust it.
     */
    @Test
    fun `a write that fails does not fail the export it was recording`() = runTest {
        val thrown = runCatching { journalOver(throwing(IOException("read-only"))).record() }.exceptionOrNull()

        assertNull(thrown)
    }

    /** Again: a bug stays loud, even on the path that swallows a bad disk. */
    @Test
    fun `a bug in the write is not swallowed`() = runTest {
        val thrown = runCatching { journalOver(throwing(IllegalStateException("a bug"))).record() }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `a settings write does not re-emit a status that has not changed`() = runTest {
        // DataStore re-emits on every write to the file, this one included, and
        // each of those would otherwise re-count the log to say the same thing.
        val dataStore = preferences()
        val source = DataStoreSettingsSource(dataStore)

        journalOver(dataStore).observe().test {
            assertEquals(ExportStatus(daysSinceExport = null, hasEvents = false), awaitItem())

            source.update { it.copy(weekStart = DayOfWeek.SUNDAY) }
            source.update { it.copy(reminderTime = LocalTime.of(6, 30)) }

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an observer already collecting sees an export land`() = runTest {
        val dataStore = preferences()
        val journal = journalOver(dataStore)

        journal.observe().test {
            assertNull(awaitItem().daysSinceExport)

            journal.record()

            assertEquals(0L, awaitItem().daysSinceExport)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- the log half of the answer -------------------------------------

    /**
     * **The reported two-tap path, on first run.**
     *
     * Fresh install, open Settings, import a backup from another phone. The log
     * now holds events and there is still no stamp, so the row must say "Never
     * exported" — and it did not, because the count used to be recomputed only
     * when the *preferences* file emitted, and an import writes no preference.
     * `WhileSubscribed(5_000)` made it recover eventually, which is what hid it.
     *
     * Note what this test does not do: it never touches DataStore after
     * subscribing. Room's invalidation on `events` is the only thing that can
     * make the second item arrive, which is the same property
     * `AllHabitsQueryTest` relies on.
     */
    @Test
    fun `an import into an empty log flips hasEvents with no preferences write`() = runTest {
        val donor = TestStore.create(idSeed = 2)
        donor.repository.createHabit(metadata(name = "from another phone"))
        val backup = donor.exportText()
        donor.close()
        val journal = journalOver(preferences())

        journal.observe().test {
            assertEquals(false, awaitItem().hasEvents)

            store.import(backup)

            assertEquals(true, awaitItem().hasEvents)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The other path the same fix closes: a habit created on another screen. */
    @Test
    fun `a habit created while the row is open flips hasEvents`() = runTest {
        val journal = journalOver(preferences())

        journal.observe().test {
            assertEquals(false, awaitItem().hasEvents)

            store.repository.createHabit(metadata(name = "read"))

            assertEquals(true, awaitItem().hasEvents)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * **A log that cannot be counted reads as having something to lose.**
     *
     * Room throws `SQLiteException`, which is a `RuntimeException` and unrelated
     * to `IOException` — verified from the platform jar — so a corrupt, locked or
     * full database slipped past the preferences guard entirely and was answered
     * two layers up with "nothing to lose". That silenced the nudge on precisely
     * the device that most needs it. A PR reviewer found it.
     */
    @Test
    fun `a log that cannot be counted reads as having something to lose`() = runTest {
        val journal = ExportJournal(
            dataStore = preferences(),
            events = UncountableEventDao(store.database.eventDao(), SQLiteException("database disk image is malformed")),
            clock = clock,
        )

        assertEquals(true, journal.observe().first().hasEvents)
    }

    @Test
    fun `the stamp is stored as epoch millis under a name a later reader can find`() = runTest {
        // Pinned rather than left to the implementation, because this is the one
        // value in the file nothing else can reconstruct: the log does not
        // record its own exports, so a key renamed by accident silently resets
        // every install to "never exported".
        val dataStore = preferences()

        journalOver(dataStore).record()

        assertEquals(clock.now().toEpochMilli(), dataStore.data.first()[longPreferencesKey("last_exported_at_epoch_milli")])
    }

    @Test
    fun `a journal built later reads the stored stamp rather than a cached one`() = runTest {
        val dataStore = preferences()
        journalOver(dataStore).record()

        clock.advanceDays(3)

        assertEquals(3L, journalOver(dataStore).observe().first().daysSinceExport)
    }
}

/**
 * An [EventDao] whose count fails the way a corrupt or locked database makes it
 * fail, delegating everything else so the real DAO still does the real work.
 *
 * The shape `HookedCompletionDao` uses in `TestStore.kt`: this project hand-writes
 * every fake and has no mocking library.
 */
private class UncountableEventDao(private val delegate: EventDao, private val cause: Throwable) : EventDao {

    override suspend fun insertAll(events: List<EventEntity>) = delegate.insertAll(events)

    override suspend fun insertMerging(events: List<EventEntity>): List<Long> = delegate.insertMerging(events)

    override suspend fun loadAll(): List<EventEntity> = delegate.loadAll()

    override suspend fun count(): Int = throw cause

    override fun observeCount(): Flow<Int> = flow { throw cause }
}
