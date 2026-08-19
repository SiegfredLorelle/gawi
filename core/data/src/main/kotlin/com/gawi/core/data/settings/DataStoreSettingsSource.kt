package com.gawi.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** The preferences file settings live in, out of the event log (architecture §3). */
internal const val SETTINGS_NAME = "settings"

/**
 * [UserSettings] in DataStore, which is where architecture §3 always put them.
 *
 * Times are stored as a second-of-day and the week start as its ISO number,
 * because both survive a locale change and neither needs a parser. Decoding is
 * deliberately forgiving: `LocalTime.ofSecondOfDay` and `DayOfWeek.of` both
 * throw on a value out of range, and a throw inside `dataStore.data` propagates
 * all the way through the repository's read path into `observeToday()` — so a
 * preferences file corrupted by a bad write or a hand-edit would take the Today
 * screen down rather than degrade to a default. Anything unreadable is treated
 * as absent.
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

    override fun observe(): Flow<UserSettings> = dataStore.data.map(::decode).distinctUntilChanged()

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
            dayCutoff = preferences[DAY_CUTOFF].asLocalTime(defaults.dayCutoff),
            weekStart = preferences[WEEK_START].asDayOfWeek(defaults.weekStart),
            reminderTime = preferences[REMINDER_TIME].asLocalTime(defaults.reminderTime),
        )
    }

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
