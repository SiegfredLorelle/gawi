package com.gawi.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The real settings store. No Robolectric: a [TemporaryFolder] and the
 * preferences factory need no `Context`, so this stays off the `sdk=35` path.
 *
 * Each store runs on `backgroundScope`, so its reader is cancelled when the test
 * ends rather than outliving it, and on a file DataStore creates itself — a
 * pre-created empty one would be testing the corruption path by accident.
 */
class DataStoreSettingsSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** A store whose reads fail, which no real DataStore lets a test arrange. */
    private fun throwing(cause: Throwable) = object : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw cause }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = error("not used")
    }

    private fun TestScope.store(name: String = "settings"): DataStore<Preferences> =
        settingsDataStore(scope = backgroundScope) { File(folder.root, "$name.preferences_pb") }

    @Test
    fun `an empty store answers the PRD defaults`() = runTest {
        val settings = DataStoreSettingsSource(store()).current()

        assertEquals(UserSettings(), settings)
        assertEquals(LocalTime.MIDNIGHT, settings.dayCutoff)
        assertEquals(DayOfWeek.MONDAY, settings.weekStart)
        assertEquals(LocalTime.of(21, 0), settings.reminderTime)
    }

    @Test
    fun `an update round-trips every field`() = runTest {
        val source = DataStoreSettingsSource(store())
        val edited = UserSettings(
            dayCutoff = LocalTime.of(3, 0),
            weekStart = DayOfWeek.SUNDAY,
            reminderTime = LocalTime.of(19, 45),
        )

        source.update { edited }

        assertEquals(edited, source.current())
    }

    @Test
    fun `an update sees the stored settings rather than the defaults`() = runTest {
        val source = DataStoreSettingsSource(store())

        source.update { it.copy(weekStart = DayOfWeek.THURSDAY) }
        source.update { it.copy(reminderTime = LocalTime.of(6, 30)) }

        assertEquals(UserSettings(weekStart = DayOfWeek.THURSDAY, reminderTime = LocalTime.of(6, 30)), source.current())
    }

    @Test
    fun `a stored value out of range falls back to the default instead of throwing`() = runTest {
        // DayOfWeek.of(9) and LocalTime.ofSecondOfDay(999999) both throw, and a
        // throw here would surface as a dead Today screen rather than a default.
        val dataStore = store()
        dataStore.edit { preferences ->
            preferences[intPreferencesKey("week_start_iso")] = 9
            preferences[intPreferencesKey("day_cutoff_second_of_day")] = 999_999
            preferences[intPreferencesKey("reminder_second_of_day")] = -1
        }

        assertEquals(UserSettings(), DataStoreSettingsSource(dataStore).current())
    }

    @Test
    fun `a value stored under one of these names with another type reads as absent`() = runTest {
        val dataStore = store()
        dataStore.edit { preferences -> preferences[stringPreferencesKey("week_start_iso")] = "monday" }

        assertEquals(UserSettings(), DataStoreSettingsSource(dataStore).current())
    }

    @Test
    fun `an unreadable file answers the defaults rather than throwing`() = runTest {
        // A read failure must not reach observeToday, which every screen
        // collects and every command reads through.
        val unreadable = throwing(IOException("unreadable"))

        assertEquals(UserSettings(), DataStoreSettingsSource(unreadable).current())
    }

    @Test
    fun `anything that is not a read failure still propagates`() = runTest {
        val broken = throwing(IllegalStateException("a bug"))

        val thrown = runCatching { DataStoreSettingsSource(broken).current() }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
    }

    @Test
    fun `a corrupt file is replaced rather than breaking the store for good`() = runTest {
        // Without the corruption handler this is terminal: reads throw, and so
        // does update, because it reads before it writes — so the user could not
        // set the settings back either.
        File(folder.root, "corrupt.preferences_pb").writeText("not a protobuf at all")
        val source = DataStoreSettingsSource(store("corrupt"))

        source.update { it.copy(weekStart = DayOfWeek.SUNDAY) }

        assertEquals(DayOfWeek.SUNDAY, source.current().weekStart)
    }

    @Test
    fun `an observer already collecting sees an update land`() = runTest {
        val source = DataStoreSettingsSource(store())

        source.observe().test {
            assertEquals(UserSettings(), awaitItem())

            source.update { it.copy(dayCutoff = LocalTime.of(3, 0)) }

            assertEquals(LocalTime.of(3, 0), awaitItem().dayCutoff)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
