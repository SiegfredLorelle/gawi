package com.gawi.widget

import android.content.Context
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
 * Toggling rather than completing mirrors `TodayViewModel.onToggle` exactly, so
 * the widget has no semantics of its own to document, and a mis-tap on a home
 * screen is recoverable without opening the app (docs/ux/widget.md §3).
 *
 * `internal` is a Kotlin visibility statement only; Glance resolves this class
 * by name from the `PendingIntent`, so it must keep a no-arg constructor — the
 * same note [TodayWidget] carries.
 */
internal class ToggleHabitAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        toggleHabit(repositoryFrom(context), parameters[HABIT_ID])
        // Re-render whatever the log now says, including after a failure: the
        // widget is the only surface here and correcting it is the only report
        // it can make.
        TodayWidget().update(context, glanceId)
    }
}

/**
 * The whole of the tap's decision, as a function taking the repository so it is
 * tested without Glance or a device.
 *
 * **It re-reads the snapshot; it does not trust what was drawn.** The rendered
 * row carried only an id, and the logical date and the completion state both
 * come from this fresh read. That is a deliberate difference from
 * `TodayScreen`, where the snapshot is live through its `Flow` and the date
 * handed to a tap is current by construction — the rule
 * `HabitRepository.observeToday`'s KDoc states. A widget's render is not live,
 * so a rendered `logicalDate` would be exactly the value most likely to be
 * stale, and writing to it would put a completion on yesterday: something the
 * 3-day retroactive window (architecture §5) *accepts* rather than rejects, and
 * so a silent wrong answer rather than a refusal. Re-reading means a stale
 * render can mislead the eye and never the log.
 *
 * Matching the id as a string is what makes a bad parameter harmless: an id that
 * is absent, malformed, or belongs to a habit archived since the render all
 * resolve to the same no-op, and no `HabitId` is ever constructed here, so its
 * `require` cannot throw inside a broadcast.
 *
 * Failures are absorbed for the reason [TodayWidget]'s read absorbs them, plus
 * one specific to writes: an exception out of a command reaching the default
 * handler is process death, which is the defect PR review found in three
 * ViewModels — a widget callback needs the same guard, and rejections are
 * values here rather than exceptions, so what is left to catch is real failure.
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
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
    }
}
