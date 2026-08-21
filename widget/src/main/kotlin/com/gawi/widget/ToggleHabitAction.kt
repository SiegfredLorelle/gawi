package com.gawi.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.gawi.core.data.repository.HabitRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/** The habit a tapped row belongs to. A plain string; see [WidgetRow]. */
internal val HABIT_ID = ActionParameters.Key<String>("habitId")

/**
 * One tap on a widget row: complete the habit, or undo it if it is already done.
 *
 * Toggling rather than only completing mirrors `TodayViewModel.onToggle`, so the
 * widget has no semantics of its own to document and a mis-tap on a home screen
 * is recoverable where it happened (docs/ux/widget.md §3).
 *
 * `internal` is a Kotlin visibility statement only; Glance resolves this class
 * by name from the `PendingIntent`, so it must keep a no-arg constructor — the
 * same note [TodayWidget] carries.
 */
internal class ToggleHabitAction : ActionCallback {

    /**
     * **The whole body is guarded, including the two Glance calls.** `onAction`
     * is invoked from Glance's `ActionCallbackBroadcastReceiver` inside a
     * `goAsync { }`, so there is no `CoroutineExceptionHandler` between a throw
     * here and the thread's default handler — a throw is process death, on a tap.
     * `TodayWidget()` in particular constructs the object whose constructor has
     * already been measured throwing `NoClassDefFoundError` on this project
     * (docs/ux/widget.md §5), and an `Error` walks past `catch (e: Exception)`.
     *
     * [toggleHabit] carries its own guard as well, and the redundancy is
     * deliberate: that one is the unit-tested seam and its callers should not
     * have to know, while this one covers the two calls it cannot reach.
     *
     * Not unit-testable — it needs a real Glance object, which only
     * `WidgetHostTest` constructs.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        try {
            toggleHabit(repositoryFrom(context), parameters[HABIT_ID])
            // Re-render whatever the log now says, including after a failure:
            // the widget is the only surface here, so correcting itself is the
            // only report it can make.
            TodayWidget().update(context, glanceId)
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "a widget tap failed", e)
        }
    }
}

private const val TAG = "ToggleHabitAction"

/**
 * The whole of the tap's decision, as a function taking the repository so it is
 * tested without Glance or a device.
 *
 * **It re-reads the log; it does not trust what was drawn.** The rendered row
 * carried only an id, and both the logical date and the completion state come
 * from this fresh read. A widget's content is live only while a Glance session
 * is, and sessions are short, so the drawn `logicalDate` is the value most
 * likely to be stale — and writing to a stale one would put a completion on
 * *yesterday*, which architecture §5's 3-day retroactive window **accepts**
 * rather than refuses. That would be a silent wrong answer rather than a
 * refusal. `TodayScreen` needs none of this care because its snapshot really is
 * live (`HabitRepository.observeToday`'s KDoc states the rule).
 *
 * Matching the id as a string is what makes a bad parameter harmless: absent,
 * malformed, or belonging to a habit archived since the render all resolve to
 * the same no-op, and no `HabitId` is constructed here, so its `require` cannot
 * throw inside a broadcast.
 *
 * Failures are absorbed rather than propagated, for the reason
 * [ToggleHabitAction.onAction]'s own guard records.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend fun toggleHabit(habits: HabitRepository, habitId: String?) {
    try {
        val snapshot = habits.observeToday().first()
        val row = snapshot.habits.firstOrNull { it.habit.id.value == habitId } ?: return
        if (row.completedToday) {
            habits.undoCompletion(row.habit.id, snapshot.today)
        } else {
            habits.addCompletion(row.habit.id, snapshot.today)
        }
    } catch (e: Throwable) {
        currentCoroutineContext().ensureActive()
        // Logged, not dropped. There is no snackbar on a home screen, so without
        // this a persistently failing tap is indistinguishable from a widget
        // nobody is tapping.
        Log.w(TAG, "the toggle behind a widget tap failed", e)
    }
}
