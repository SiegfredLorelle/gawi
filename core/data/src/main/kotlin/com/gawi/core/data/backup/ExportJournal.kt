package com.gawi.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.time.DeviceClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * When the log was last written to a file, and whether there is a log worth
 * writing — the two things the 30-day nudge is made of (PRD §5, architecture §6).
 *
 * **Deliberately not a fourth `UserSettings` field**, which is where
 * docs/ux/settings.md §7 expected it. Two reasons, and the first is the one that
 * decided it. `OfflineFirstHabitRepository` dedupes the Today query on the
 * `(settings, logical date)` pair, so a `UserSettings` field that changes on
 * every export would make that dedupe miss and restart the streak sweep under an
 * open screen — the churn `DataStoreSettingsSource`'s `distinctUntilChanged`
 * exists to prevent. And the nudge needs a second signal `UserSettings` cannot
 * carry at all, "is there anything here to lose", so a flow of its own was
 * needed either way. The three preferences are what the user set; this is a
 * record of something the app did.
 *
 * It shares the *file* with them, which is safe in the direction that is easy to
 * get wrong: `DataStoreSettingsSource.update` assigns only its own three keys
 * inside `edit`, so this one survives a settings write even though that block
 * reads as though it rewrites everything. A test pins it in both directions.
 *
 * Not a sixth constructor parameter on [EventLogArchive], which already has
 * five — detekt's `LongParameterList` fires *at* six.
 */
internal class ExportJournal @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val events: EventDao,
    private val clock: DeviceClock,
) {

    /** Stamps now as the moment a file landed. Called after the write, never before. */
    suspend fun record() {
        dataStore.edit { preferences -> preferences[LAST_EXPORTED_AT] = clock.now().toEpochMilli() }
    }

    /**
     * The current status, and every later one.
     *
     * **A read failure degrades to [UNKNOWN] rather than propagating**, which is
     * the opposite of what the settings store does with the same file, and the
     * asymmetry is the point: a cutoff is an input to a command, so guessing one
     * admits taps it should refuse, while this is a hint on a row that nothing
     * validates against. Letting it throw would put the settings screen in
     * `Unavailable` and take the only disaster-recovery path off the screen with
     * it — over the caption above the button.
     *
     * [distinctUntilChanged] because DataStore re-emits on every write to the
     * file, settings included, and each of those would otherwise re-count the
     * log to produce an answer that had not changed.
     */
    fun observe(): Flow<ExportStatus> = dataStore.data
        .map { preferences -> statusFor(preferences.long(LAST_EXPORTED_AT)) }
        .catch { emit(UNKNOWN) }
        .distinctUntilChanged()

    private suspend fun statusFor(storedMillis: Long?): ExportStatus = ExportStatus(
        daysSinceExport = storedMillis?.let { daysSince(Instant.ofEpochMilli(it)) },
        hasEvents = events.count() > 0,
    )

    /**
     * Whole days between two wall-clock dates in the device's zone.
     *
     * Wall-clock and **not** the logical date, for the reason `exportFileName`
     * gives about the file name: the day cutoff decides which day a completion
     * belongs to (architecture §5) and has no business deciding whether a backup
     * is stale. Under an 03:00 cutoff a backup taken at 01:00 is otherwise a day
     * older than it is.
     *
     * Coerced at zero, so a device clock wound backwards reads as "today"
     * instead of counting down towards a nudge from a negative number.
     */
    private fun daysSince(exportedAt: Instant): Long {
        val zone = clock.zone()
        return ChronoUnit.DAYS
            .between(exportedAt.atZone(zone).toLocalDate(), clock.now().atZone(zone).toLocalDate())
            .coerceAtLeast(0)
    }

    /** The stored [key], or null if it is absent *or* holds something else. */
    private fun Preferences.long(key: Preferences.Key<Long>): Long? = asMap()[key] as? Long

    private companion object {
        val LAST_EXPORTED_AT = longPreferencesKey("last_exported_at_epoch_milli")

        /** What an unreadable file says: exactly what the row said before any of this existed. */
        val UNKNOWN = ExportStatus(daysSinceExport = null, hasEvents = false)
    }
}
