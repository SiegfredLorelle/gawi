package com.gawi.core.data.reminder

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

/**
 * Which logical date the end-of-day reminder was last posted for — the whole of
 * PRD §6.1's *"one reminder max per day"*.
 *
 * The **logical** date and not the calendar one, because that is the day the
 * user is being reminded about (architecture §5): under an 03:00 cutoff, 01:30
 * belongs to the evening before and must not be reminded about twice.
 *
 * Shaped after [ExportJournal][com.gawi.core.data.backup.ExportJournal] and for
 * the same reason — this is a record of something the app *did*, not a
 * preference the user set, so it is not a fourth
 * [UserSettings][com.gawi.core.data.settings.UserSettings] field. It shares the
 * preferences *file* with them, which is safe in the direction that is easy to
 * get wrong: `DataStoreSettingsSource.update` assigns only the preference keys
 * inside `edit`, so this key survives a settings write.
 *
 * **Its failures resolve the opposite way round from `ExportJournal`'s, and that
 * is the only interesting thing about this class.** That one is arranged so
 * every failure resolves *towards nudging*: a wrong warning costs an export
 * nobody needed, a wrong silence costs the warning the PRD asked for. Here the
 * costs are reversed. An extra notification is the failure a user actually
 * notices, and "one reminder max per day" is a headline criterion, whereas a
 * reminder missed once is a nudge that arrives tomorrow. So an unreadable file
 * **suppresses** the reminder, and a failed write is absorbed.
 *
 * Do not "simplify" the two journals into one policy. They are deliberately
 * opposite, and what differs is which way a wrong answer hurts.
 */
internal class ReminderJournal @Inject constructor(private val dataStore: DataStore<Preferences>) {

    /**
     * Whether a reminder for [today] has already been dealt with and must not be
     * posted again.
     *
     * **The decision, not the data, and deliberately so.** The alternative — a
     * nullable date plus a separate "could it be read?" — is two reads that can
     * disagree, and it hands the caller a null that means *fire* sitting next to
     * a failure that means *stay silent*. Collapsing those two by accident is
     * exactly how the once-a-day guarantee would be lost to a bad disk, so the
     * policy lives here, next to the KDoc that argues for it, and the caller
     * gets one answer from one read.
     *
     * True for a stamp on [today] **or one day ahead of it**, and false for one
     * further ahead than that. Both halves of that are `ExportJournal.daysSince`'s
     * decision, re-made here because it is the same bug in the same shape.
     *
     * A day of tolerance, because this compares dates and a clock nudge across
     * local midnight — reminded at 00:03, NTP pulls back to 23:57 — would
     * otherwise re-arm a reminder that had just been posted.
     *
     * A stamp *further* ahead reads as no stamp at all, and that is not the same
     * as clamping it. A device whose clock was a month ahead when a reminder was
     * posted, and correct afterwards, leaves a stamp this class would otherwise
     * honour for a month — silently, which is the one failure mode worse than a
     * duplicate. Reading it as absent posts one reminder and **overwrites the bad
     * stamp**, which is self-healing; honouring it is not.
     *
     * Comparing epoch days rather than [LocalDate]s is also what makes a
     * nonsensical stored value safe by construction: `Long.MIN_VALUE` is simply
     * far in the past, and `Long.MAX_VALUE` far in the future, so neither reaches
     * `LocalDate.ofEpochDay` to throw out of a function whose whole job is to be
     * un-throwable.
     *
     * `IOException` only. Anything else is a bug rather than a bad disk, and a
     * bug should be loud even here.
     */
    @Suppress("SwallowedException")
    suspend fun alreadyReminded(today: LocalDate): Boolean = try {
        val stored = dataStore.data.first().asMap()[LAST_REMINDED_EPOCH_DAY] as? Long
        stored != null && stored in today.toEpochDay()..(today.toEpochDay() + SKEW_TOLERANCE_DAYS)
    } catch (cause: IOException) {
        // Suppressing rather than firing. See the class KDoc: this is the one
        // decision in the app whose safe direction is silence.
        true
    }

    /**
     * Stamps [date] as reminded.
     *
     * **A write failure is absorbed, because the notification has already been
     * posted** — the same call `ExportJournal.record` makes about a stamp after a
     * successful export. There is nothing left for a caller to do about it and
     * nothing it could report. The residual is one duplicate reminder, and only
     * if the worker runs a second time that same day.
     */
    // Suppressed rather than logged: there is no logger anywhere in this module,
    // and the KDoc above is the record.
    @Suppress("SwallowedException")
    suspend fun record(date: LocalDate) {
        try {
            dataStore.edit { preferences -> preferences[LAST_REMINDED_EPOCH_DAY] = date.toEpochDay() }
        } catch (cause: IOException) {
            Unit
        }
    }

    private companion object {
        val LAST_REMINDED_EPOCH_DAY = longPreferencesKey("last_reminded_logical_date_epoch_day")

        /**
         * How far ahead of [today] a stamp may be dated and still suppress.
         * `ExportJournal` uses the same one for the same reason: a whole day
         * ahead is jitter, two days is a wrong clock.
         */
        const val SKEW_TOLERANCE_DAYS = 1L
    }
}
