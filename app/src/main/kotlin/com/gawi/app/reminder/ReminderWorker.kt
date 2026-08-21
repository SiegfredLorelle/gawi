package com.gawi.app.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import com.gawi.core.data.reminder.ReminderDecision
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val TAG = "ReminderWorker"

/**
 * The end-of-day reminder, woken at the configured reminder time (PRD §4, §6.1.5).
 *
 * Holds no rule of its own. What to say is [ReminderDecision]'s answer, computed
 * in `:core:data` where the clock, the cutoff and the outstanding rule already
 * live; this posts it and arms the next wake.
 *
 * **Arms the rollover, not itself, and with `KEEP`.** See [ReminderScheduler]'s
 * KDoc: re-enqueuing one's own unique work with `REPLACE` cancels the run doing
 * the enqueuing, and replacing the *other* name from here is what lost a day's
 * reminder. This is the direction that may not cancel; [RolloverWorker] is the one
 * that must.
 *
 * **Always `Result.success()`, even when the work failed.** A `failure` on unique
 * one-time work is terminal — the record is kept and nothing re-runs — and a
 * `retry` would re-run at WorkManager's backoff rather than at the reminder time,
 * which is not what a daily nudge means. Since the next wake is armed by hand
 * below, "did today's post work" is not something WorkManager's result needs to
 * carry, and reporting a failure would only add a red row to a dumpsys nobody
 * reads. What is not swallowed is the *arming*: that runs whether the post
 * succeeded or failed, because a lost link is the one failure the chain cannot
 * repair by itself.
 *
 * **With one exception, which this KDoc used to gloss over.** The guard rethrows
 * cancellation, so a worker WorkManager *stops* — a `REPLACE` from a settings
 * edit, a quota, a constraint — never reaches the arming call at all. That is
 * safe rather than a hole, for two separate reasons: a stop leaves the work
 * enqueued for a retry, so this runs again; and the `REPLACE` that caused the
 * stop has itself just armed that name. Not "unconditional", which is what the
 * previous wording claimed. Found by `/code-review`.
 */
internal class ReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)

        try {
            when (val decision = entryPoint.reminderCheck().evaluate()) {
                is ReminderDecision.Silent -> Unit

                is ReminderDecision.Remind ->
                    if (!entryPoint.reminderNotifier().post(decision)) {
                        // Notifications are off, so the reminder was recorded as
                        // posted and then went nowhere. Logged rather than
                        // un-recorded: retrying it would produce another
                        // invisible attempt, and the settings screen is where
                        // this state is meant to be discovered.
                        Log.i(TAG, "reminder suppressed by the platform; notifications are off")
                    }
            }
        } catch (cause: Throwable) {
            // Rethrows cancellation and nothing else. Deliberately wider than
            // Exception, for the reason GlanceProjectionListener records: an
            // Error escaping a guard here once killed a committed write.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "the end-of-day reminder failed", cause)
        }

        // KEEP, and this is the one direction that must not cancel. REPLACE here
        // destroyed an overdue rollover wake that was about to re-arm this one,
        // losing a day's reminder. RolloverWorker replaces instead, which is what
        // keeps the chain moving — ReminderScheduler's KDoc has the pair.
        //
        // Not reached when this worker is stopped: the guard above rethrows
        // cancellation. See the KDoc for why that is safe.
        entryPoint.reminderScheduler().armRollover(ExistingWorkPolicy.KEEP)
        return Result.success()
    }
}
