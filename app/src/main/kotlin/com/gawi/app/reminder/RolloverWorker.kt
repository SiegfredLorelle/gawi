package com.gawi.app.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
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
 * **Calling the listener directly makes this its third caller**, and the one
 * that follows the *absence* of a commit rather than a commit — the case its
 * KDoc names when it says a consumer needing to follow a rollover must observe
 * or be woken for itself. `:app` resolves the interface rather than a `:widget` type, so the
 * module rule (`widget → core`) is untouched — the Glance implementation is
 * reached through the binding `:widget` already provides.
 *
 * **Arms the reminder, not itself, and with `REPLACE`** — [ReminderScheduler]'s
 * KDoc has both halves. This is the direction that guarantees forward progress, so
 * it is the one that may cancel; `ReminderWorker` is the one that may not.
 */
internal class RolloverWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)

        try {
            entryPoint.habitRepository().refreshStreaks()
            entryPoint.projectionListener().onProjectionChanged()
        } catch (cause: Throwable) {
            // As ReminderWorker: cancellation is rethrown and an Error is not
            // allowed to escape. Rethrowing means the arming below is skipped when
            // this worker is *stopped*, which is safe — a stop leaves the work
            // enqueued for a retry. It still runs after an ordinary failure.
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "the day-rollover refresh failed", cause)
        }

        // REPLACE, and this is the one direction that must replace. KEEP here
        // no-ops against an overdue or running reminder — KEEP treats RUNNING as
        // pending — which left the reminder's unique name empty after both wakes
        // came due at once. ReminderScheduler's KDoc has both orderings.
        entryPoint.reminderScheduler().armReminder(ExistingWorkPolicy.REPLACE)
        return Result.success()
    }
}
