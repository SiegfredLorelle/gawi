package com.gawi.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.time.DeviceClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
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
 * **Every failure here resolves towards nudging rather than towards silence**,
 * and that is the rule the whole class is arranged around. This value only ever
 * decides whether to warn someone that they may have no backup, so a wrong
 * warning costs an export nobody needed and a wrong silence costs the warning
 * the PRD asked for. Four failures are handled that way below, individually: the
 * preferences read, the log count, the write, and a nonsensical stored value.
 *
 * The count was the one that got away. It used to run inside the `map` over the
 * preferences flow, under a `catch` that tests `IOException` — and Room throws
 * `SQLiteException`, which is a `RuntimeException`. So a corrupt, locked or full
 * database went straight past this class's guard and was answered two layers up
 * with "nothing to lose", silencing the nudge on a device whose database was
 * failing. A PR reviewer found it. The two reads have separate guards now,
 * because they fail for separate reasons.
 *
 * Not a sixth constructor parameter on [EventLogArchive], which already has
 * five — detekt's `LongParameterList` fires *at* six.
 */
internal class ExportJournal @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val events: EventDao,
    private val clock: DeviceClock,
) {

    /**
     * Stamps now as the moment a file landed. Called after the write, never before.
     *
     * **A write failure is absorbed, because the export already succeeded.**
     * `dataStore.edit` reads before it writes, so an unwritable preferences file
     * throws here — and letting that out would fail an `exportTo` whose document
     * is complete and correct, which the caller reports as
     * `settings_error_export`: copy that tells the user to delete a good backup
     * rather than trust it, on the only recovery path there is. The residual is
     * benign and self-healing: the stamp stays as it was, so the row keeps
     * nudging, and the next export the user makes because of that nudge tries
     * again.
     *
     * `IOException` only. Anything else is a bug rather than a bad disk, and a
     * bug should be loud even here.
     */
    // Suppressed rather than logged: there is nowhere to report this that would
    // not misdescribe an export that worked, and no logger anywhere in this
    // module to write it to. The KDoc above is the record.
    @Suppress("SwallowedException")
    suspend fun record() {
        try {
            dataStore.edit { preferences -> preferences[LAST_EXPORTED_AT] = clock.now().toEpochMilli() }
        } catch (cause: IOException) {
            Unit
        }
    }

    /**
     * The current status, and every later one.
     *
     * **An unreadable file reads as "never exported" rather than propagating**,
     * exactly as [DataStoreSettingsSource][com.gawi.core.data.settings.DataStoreSettingsSource]'s
     * read path degrades to the defaults, and for a sharper reason: this is the
     * one value on the screen whose absence *hides* a warning. Note what the
     * fallback deliberately does not do — it substitutes empty preferences and
     * carries on, so the log is still counted, and a device with events on it
     * therefore still gets nudged. Answering with a fixed "nothing to lose"
     * would have silenced the nudge over a preferences read, which is the one
     * failure mode this feature exists to prevent.
     *
     * Not a blanket catch, for the reason the settings store gives: anything
     * that is not a read failure is a bug and swallowing it would hide it behind
     * a plausible answer. The caller guards that case separately, because a
     * caption must not be able to take the screen down either way.
     *
     * [distinctUntilChanged] suppresses the *emission*, not the work: DataStore
     * re-emits on every write to the file, settings included, so `COUNT(*)` does
     * run again on each of those — what this stops is an identical answer
     * reaching `combine` and recomposing the screen behind it.
     */
    fun observe(): Flow<ExportStatus> = combine(storedStamp(), hasEvents()) { storedMillis, hasEvents ->
        ExportStatus(
            daysSinceExport = storedMillis?.let { daysSince(Instant.ofEpochMilli(it)) },
            hasEvents = hasEvents,
        )
    }.distinctUntilChanged()

    /**
     * The stored stamp, or null if there is none to read.
     *
     * **An unreadable file reads as "never exported" rather than propagating**,
     * exactly as `DataStoreSettingsSource`'s read path degrades to the defaults,
     * and for a sharper reason: this is the one value on the screen whose absence
     * *hides* a warning. Note what the fallback deliberately does not do — it
     * substitutes empty preferences rather than a finished answer, so the log is
     * still counted alongside it and a device with events on it still gets
     * nudged.
     *
     * Not a blanket catch, for the reason the settings store gives: anything that
     * is not a read failure is a bug and swallowing it would hide it behind a
     * plausible answer. The caller guards that case separately, because a caption
     * must not be able to take the screen down either way.
     */
    private fun storedStamp(): Flow<Long?> = dataStore.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { preferences -> preferences.long(LAST_EXPORTED_AT) }

    /**
     * Whether the log holds anything, which is what keeps a fresh install from
     * being nudged about losing nothing.
     *
     * **A failed count reads as "there is something here", because that is the
     * reading that nudges.** Room throws `SQLiteException` — a `RuntimeException`,
     * unrelated to `IOException` — so a corrupt, locked or full database lands
     * here and nowhere else. Answering false would hide the warning on precisely
     * the device that most needs it; answering true costs an export nobody
     * needed, and on a broken database that export fails loudly, which tells the
     * user more than silence would.
     *
     * Mapped to a `Boolean` before the catch, so the fallback can be written as
     * the decision it is rather than as a sentinel count.
     */
    private fun hasEvents(): Flow<Boolean> = events.observeCount()
        .map { it > 0 }
        .catch { emit(true) }

    /**
     * Whole days between two wall-clock dates in the device's zone, or null if
     * the stamp is in the future.
     *
     * Wall-clock and **not** the logical date, for the reason `exportFileName`
     * gives about the file name: the day cutoff decides which day a completion
     * belongs to (architecture §5) and has no business deciding whether a backup
     * is stale. Under an 03:00 cutoff a backup taken at 01:00 is otherwise a day
     * older than it is.
     *
     * **A stamp dated after today reads as no stamp at all, and that is not the
     * same as clamping it to nought.** A device whose clock was ahead when the
     * export happened, and correct afterwards, leaves a stamp that can never
     * count upwards — so clamping would pin the row to "Last exported today" and
     * kill the nudge for the life of the install, silently, which is precisely
     * the failure this feature exists to prevent. Reading it as unknown nudges
     * instead, and the export that follows replaces the bad stamp. Note this
     * compares *dates*, so it takes a whole day of skew to trigger and clock
     * jitter around a fresh export cannot.
     *
     * **A count too large for the UI's `Int` is refused the same way**, and that
     * is not hypothetical arithmetic: a stored `Long.MIN_VALUE` dates the stamp
     * to the year -292275055, which is 106,752,011,854 days and narrows to
     * -622,170,546 — a negative age, which reads as not-overdue and silences the
     * nudge. Measured, not reasoned about. Bounding it here rather than clamping
     * at the call site is what makes `recencyOf`'s `Long`-to-`Int` narrowing safe
     * by construction instead of by luck.
     */
    private fun daysSince(exportedAt: Instant): Long? {
        val zone = clock.zone()
        val days = ChronoUnit.DAYS
            .between(exportedAt.atZone(zone).toLocalDate(), clock.now().atZone(zone).toLocalDate())
        return days.takeIf { it in 0..Int.MAX_VALUE.toLong() }
    }

    /** The stored [key], or null if it is absent *or* holds something else. */
    private fun Preferences.long(key: Preferences.Key<Long>): Long? = asMap()[key] as? Long

    private companion object {
        val LAST_EXPORTED_AT = longPreferencesKey("last_exported_at_epoch_milli")
    }
}
