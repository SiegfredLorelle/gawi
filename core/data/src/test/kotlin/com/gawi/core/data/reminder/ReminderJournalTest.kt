package com.gawi.core.data.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gawi.core.data.settings.DataStoreSettingsSource
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.data.settings.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * The once-a-day guard, and the direction its failures resolve in.
 *
 * No Robolectric: a [TemporaryFolder] and the preferences factory need no
 * `Context`, so this stays off the `sdk=35` path — the same note
 * `DataStoreSettingsSourceTest` carries, and this is its sibling.
 *
 * The interesting assertions are the failure ones. This journal resolves
 * *towards silence* where `ExportJournal` resolves *towards nudging*, which is a
 * deliberate asymmetry between two classes sharing one file, and exactly the kind
 * of thing a later reader would unify. These are what would go red if someone did.
 */
class ReminderJournalTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val today = LocalDate.parse("2026-08-17")

    private fun TestScope.store(name: String = "reminder"): DataStore<Preferences> =
        settingsDataStore(scope = backgroundScope) { File(folder.root, "$name.preferences_pb") }

    /** A store whose reads fail, which no real DataStore lets a test arrange. */
    private fun throwing(cause: Throwable) = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw cause }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("not used")
    }

    @Test
    fun `a fresh install has not been reminded`() = runTest {
        assertFalse(ReminderJournal(store()).alreadyReminded(today))
    }

    @Test
    fun `a recorded day reads back as reminded`() = runTest {
        val journal = ReminderJournal(store())

        journal.record(today)

        assertTrue(journal.alreadyReminded(today))
    }

    @Test
    fun `a different day is not suppressed by yesterday's stamp`() = runTest {
        val journal = ReminderJournal(store())

        journal.record(today.minusDays(1))

        assertFalse(journal.alreadyReminded(today))
    }

    /**
     * The asymmetry, stated as a test.
     *
     * `ExportJournal` reads an unreadable file as "never exported" so the nudge
     * still fires; this reads one as "already reminded" so the notification does
     * not. Both are the safe direction for their own feature, and swapping either
     * would look like a tidy-up.
     */
    @Test
    fun `an unreadable file suppresses rather than reminds`() = runTest {
        val journal = ReminderJournal(throwing(IOException("preferences unreadable")))

        assertTrue(journal.alreadyReminded(today))
    }

    /** Not a blanket catch: anything that is not a read failure is a bug and stays loud. */
    @Test(expected = IllegalStateException::class)
    fun `a non-IO failure is not absorbed`() = runTest {
        ReminderJournal(throwing(IllegalStateException("bug"))).alreadyReminded(today)
    }

    /**
     * A stamp one day ahead still suppresses — a clock nudge across local midnight
     * must not re-arm a reminder that has just been posted.
     */
    @Test
    fun `a stamp a day ahead is treated as jitter and suppresses`() = runTest {
        val journal = ReminderJournal(store())

        journal.record(today.plusDays(1))

        assertTrue(journal.alreadyReminded(today))
    }

    /**
     * A stamp further ahead reads as no stamp, which is the difference between
     * self-healing and permanent silence.
     *
     * A device whose clock was a month ahead when a reminder was posted, and
     * correct afterwards, would otherwise be silenced for a month — invisibly,
     * which is the one failure worse than a duplicate. `ExportJournal.daysSince`
     * made this same call, for the same reason, and had it corrected by a reviewer.
     */
    @Test
    fun `a stamp well in the future reads as no stamp at all`() = runTest {
        val journal = ReminderJournal(store())

        journal.record(today.plusDays(30))

        assertFalse(journal.alreadyReminded(today))
    }

    @Test
    fun `a value of the wrong type reads as absent`() = runTest {
        val dataStore = store()
        dataStore.edit { it[stringPreferencesKey("last_reminded_logical_date_epoch_day")] = "yesterday" }

        assertFalse(ReminderJournal(dataStore).alreadyReminded(today))
    }

    /**
     * A nonsensical value cannot throw, and errs towards a reminder rather than
     * towards permanent silence.
     *
     * `LocalDate.ofEpochDay(Long.MIN_VALUE)` throws `DateTimeException`, so
     * comparing epoch days rather than dates is what makes this safe by
     * construction instead of by a `try`. And it lands on "not reminded", so the
     * bad stamp is overwritten by the next real one.
     */
    @Test
    fun `an out-of-range value neither throws nor silences forever`() = runTest {
        val dataStore = store()
        dataStore.edit { it[longPreferencesKey("last_reminded_logical_date_epoch_day")] = Long.MIN_VALUE }

        assertFalse(ReminderJournal(dataStore).alreadyReminded(today))
    }

    /**
     * A write failure is absorbed, because the notification has already been shown.
     *
     * `updateData` is what `edit` calls, so a store that refuses it is a store whose
     * writes fail. Nothing is thrown and nothing is recorded — the residual the
     * KDoc admits to.
     */
    @Test
    fun `a failed write is absorbed rather than thrown`() = runTest {
        val dataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("unreadable") }

            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("unwritable")
        }

        ReminderJournal(dataStore).record(today)
    }

    /**
     * The two journals and the three settings share one file, and neither write
     * erases the other.
     *
     * `DataStoreSettingsSource.update` reads as though it rewrites everything —
     * it assigns inside `edit` — so this is the direction that is easy to get wrong,
     * and `ExportJournal` has the same test for the same reason.
     */
    @Test
    fun `a settings write does not erase the stamp, and the stamp does not erase the settings`() = runTest {
        val dataStore = store()
        val journal = ReminderJournal(dataStore)
        val settings = DataStoreSettingsSource(dataStore)
        val edited = UserSettings(dayCutoff = LocalTime.of(3, 0), weekStart = DayOfWeek.SUNDAY, reminderTime = LocalTime.of(19, 45))

        journal.record(today)
        settings.update { edited }

        assertTrue("a settings write erased the stamp", journal.alreadyReminded(today))

        journal.record(today.plusDays(1))

        assertEquals("a stamp erased the settings", edited, settings.current())
    }
}
