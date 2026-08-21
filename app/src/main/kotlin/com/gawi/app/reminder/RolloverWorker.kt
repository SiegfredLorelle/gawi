package com.gawi.app.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val TAG = "RolloverWorker"

/**
 * The day-boundary refresh, woken at the day cutoff.
 *
 * **The one thing in this app that happens because nothing happened.** Every
 * other redraw follows a commit: a write moves the derived tables, and
 * `ProjectionListener` pushes that at Glance. A day rollover commits nothing —
 * it is a wall-clock instant, not an event — so no push can fire for it, and a
 * widget with no live session goes on showing yesterday's ticks (architecture §4,
 * docs/ux/widget.md §4). An open screen follows the rollover by itself, because
 * `observeToday()` re-emits on it; the widget is the caller that cannot.
 *
 * Two calls, in this order, and the order is the point:
 *
 * 1. [HabitRepository.refreshStreaks][com.gawi.core.data.repository.HabitRepository.refreshStreaks],
 *    which its own KDoc names *"the only way a streak reaches zero without a new
 *    event, so a day-rollover worker will want this"*. This is that worker.
 * 2. `ProjectionListener.onProjectionChanged()`, so the widget redraws what step
 *    one just changed. Pushing first would redraw the old streaks and then leave
 *    the new ones unpushed until something else committed.
 *
 * **Calling the listener directly makes it a third caller**, which its KDoc
 * previously said it had none of: it is called from `appendLocked` and
 * `mergeLocked`, *"not from `rebuildProjections`… not from `sweepStreaks`"*. That
 * paragraph also says what a consumer needing to follow a rollover must do, and
 * this is it. `:app` resolves the interface rather than a `:widget` type, so the
 * module rule (`widget → core`) is untouched — the Glance implementation is
 * reached through the binding `:widget` already provides.
 *
 * **Arms the reminder, not itself** — [ReminderScheduler]'s KDoc has the reason.
 */
internal class RolloverWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)

        try {
            entryPoint.habitRepository().refreshStreaks()
            entryPoint.projectionListener().onProjectionChanged()
        } catch (cause: Throwable) {
            // As ReminderWorker: cancellation is rethrown, an Error is not
            // allowed to escape, and the arming below still runs.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "the day-rollover refresh failed", cause)
        }

        entryPoint.reminderScheduler().armReminder()
        return Result.success()
    }
}
