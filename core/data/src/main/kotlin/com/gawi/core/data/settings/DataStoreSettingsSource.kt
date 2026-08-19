package com.gawi.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** The preferences file settings live in, out of the event log (architecture §3). */
internal const val SETTINGS_NAME = "settings"

/**
 * The settings store, configured in one place so a test gets the same one the
 * app does.
 *
 * The corruption handler is not optional. Without it, a preferences file left
 * unreadable by an interrupted write makes both reads and writes throw for
 * good: [DataStoreSettingsSource] would answer defaults forever via its own
 * `catch`, but `update` reads before it writes, so the user could never even
 * set the settings back. Discarding preferences that cannot be parsed costs
 * three values they can set again.
 *
 * [scope] defaults to what DataStore would have used, and exists so a test can
 * bind the store's lifetime to the test's.
 */
internal fun settingsDataStore(
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    scope = scope,
    produceFile = produceFile,
)

/**
 * [UserSettings] in DataStore, which is where architecture §3 always put them.
 *
 * Times are stored as a second-of-day and the week start as its ISO number,
 * because both survive a locale change and neither needs a parser.
 *
 * Reading is forgiving about the *contents*, at both levels they can fail. A
 * value stored under one of these names with another type reads as absent rather
 * than raising `ClassCastException`; a value out of range reads as absent rather
 * than letting `LocalTime.ofSecondOfDay` or `DayOfWeek.of` throw. Either would
 * otherwise propagate through the repository's read path into `observeToday()`
 * and take the Today screen down over a stray byte.
 *
 * A file that cannot be *read* is where the two paths part, and the asymmetry is
 * deliberate rather than an oversight. [observe] degrades to the defaults,
 * because a query bound to a guessed cutoff shows the wrong day's rows while a
 * dead flow shows nothing, and neither writes anything that outlives the
 * process. [current] refuses, because commands validate against its answer —
 * `Commands.addCompletion` checks the retro window and the future-date rule
 * against a `today` derived from the cutoff, so guessing there can admit or
 * reject a tap it should not, and what the log then keeps is never re-bucketed
 * on replay. Refusing a write is recoverable; a wrongly admitted one is not.
 *
 * Corruption is handled a level up, by the handler in `DataModule`, so a
 * replaced file reads clean and both paths see the defaults — genuine loss of
 * the stored values rather than a guess about them.
 *
 * The [distinctUntilChanged] is load-bearing. DataStore re-emits on every write,
 * including one that changed a value back to what it already was, and the
 * repository's own dedupe is on the `(settings, logical date)` pair, so an
 * unchanged re-emission would otherwise churn the query under an open screen.
 *
 * `current()` is inherited: DataStore reads the file once and then serves from
 * memory, so of the four command-path calls — one of them inside the append
 * transaction, under the repository's mutex — only the first in a process
 * touches disk.
 */
@Singleton
class DataStoreSettingsSource @Inject constructor(private val dataStore: DataStore<Preferences>) : SettingsSource {

    /**
     * Deliberately not the inherited default, which reads through [observe] and
     * would inherit its fallback. Cold, so the read is retried on the next
     * command rather than needing retry machinery here.
     */
    override suspend fun current(): UserSettings = decode(dataStore.data.first())

    override fun observe(): Flow<UserSettings> = dataStore.data
        // Not a blanket catch: anything that is not a read failure is a bug
        // here, and swallowing it would hide it behind plausible defaults.
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map(::decode)
        .distinctUntilChanged()

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        dataStore.edit { preferences ->
            val updated = transform(decode(preferences))
            preferences[DAY_CUTOFF] = updated.dayCutoff.toSecondOfDay()
            preferences[WEEK_START] = updated.weekStart.value
            preferences[REMINDER_TIME] = updated.reminderTime.toSecondOfDay()
        }
    }

    private fun decode(preferences: Preferences): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            dayCutoff = preferences.int(DAY_CUTOFF).asLocalTime(defaults.dayCutoff),
            weekStart = preferences.int(WEEK_START).asDayOfWeek(defaults.weekStart),
            reminderTime = preferences.int(REMINDER_TIME).asLocalTime(defaults.reminderTime),
        )
    }

    /** The stored [key], or null if it is absent *or* holds something else. */
    private fun Preferences.int(key: Preferences.Key<Int>): Int? = asMap()[key] as? Int

    private fun Int?.asLocalTime(default: LocalTime): LocalTime =
        if (this != null && this in 0 until SECONDS_PER_DAY) LocalTime.ofSecondOfDay(toLong()) else default

    private fun Int?.asDayOfWeek(default: DayOfWeek): DayOfWeek =
        if (this != null && this in DayOfWeek.MONDAY.value..DayOfWeek.SUNDAY.value) DayOfWeek.of(this) else default

    private companion object {
        val DAY_CUTOFF = intPreferencesKey("day_cutoff_second_of_day")
        val WEEK_START = intPreferencesKey("week_start_iso")
        val REMINDER_TIME = intPreferencesKey("reminder_second_of_day")

        const val SECONDS_PER_DAY = 24 * 60 * 60
    }
}
