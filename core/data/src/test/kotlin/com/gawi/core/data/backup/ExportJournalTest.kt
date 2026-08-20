package com.gawi.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.gawi.core.data.settings.DataStoreSettingsSource
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.data.settings.settingsDataStore
import com.gawi.core.data.testsupport.FakeDeviceClock
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.data.testsupport.metadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /** A store whose reads fail, which no real DataStore lets a test arrange. */
    private fun throwing(cause: Throwable) = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw cause }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("not used")
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

    @Test
    fun `a clock wound backwards reads as today rather than as a negative count`() = runTest {
        val journal = journalOver(preferences())
        journal.record()

        clock.instant = Instant.parse("2026-08-10T09:00:00Z")

        assertEquals(0L, journal.observe().first().daysSinceExport)
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

    @Test
    fun `an unreadable file answers rather than throwing`() = runTest {
        // The opposite of what the settings store does with the same file, and
        // deliberately so: a cutoff is an input to a command, this is a caption.
        // Throwing here puts the settings screen in Unavailable, which takes the
        // only recovery path off the screen over the text above the button.
        val status = journalOver(throwing(IOException("unreadable"))).observe().first()

        assertNull(status.daysSinceExport)
        assertEquals(false, status.hasEvents)
    }

    @Test
    fun `a bug in the read is answered too, rather than reaching the screen`() = runTest {
        val status = journalOver(throwing(IllegalStateException("a bug"))).observe().first()

        assertNull(status.daysSinceExport)
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
