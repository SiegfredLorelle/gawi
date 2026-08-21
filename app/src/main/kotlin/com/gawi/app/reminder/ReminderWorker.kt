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
 * **Arms the rollover, not itself.** See [ReminderScheduler]'s KDoc: re-enqueuing
 * one's own unique work with `REPLACE` cancels the run doing the enqueuing. The
 * two workers arming each other has no such race, and the chain alternates.
 *
 * **Always `Result.success()`, even when the work failed.** A `failure` on unique
 * one-time work is terminal — the record is kept and nothing re-runs — and a
 * `retry` would re-run at WorkManager's backoff rather than at the reminder time,
 * which is not what a daily nudge means. Since the next wake is armed by hand
 * below, "did today's post work" is not something WorkManager's result needs to
 * carry, and reporting a failure would only add a red row to a dumpsys nobody
 * reads. What is not swallowed is the *arming*: that runs whether the post
 * succeeded or not, because a lost link is the one failure the chain cannot
 * repair by itself.
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

        // KEEP, never REPLACE: this must not cancel the other wake, only
        // ensure one exists. See ReminderScheduler's KDoc — REPLACE here
        // destroyed an overdue rollover wake that was about to re-arm this one.
        entryPoint.reminderScheduler().armRollover(ExistingWorkPolicy.KEEP)
        return Result.success()
    }
}
