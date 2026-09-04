package com.gawi.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import com.gawi.core.data.projection.ProjectionListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Pushes the committed read model at Glance, which cannot observe Room for
 * itself (architecture §4).
 *
 * **Switches dispatcher here, not at the call site.** The interface promises the
 * repository a main-safe callback, and the caller's dispatcher is whatever
 * tapped — `viewModelScope` is `Main.immediate`. This class owns the switch
 * because it is the one that touches the platform, so the promise is kept in a
 * single place; the alternative is every call site remembering to make it.
 *
 * **A failure is absorbed, and it catches `Throwable` rather than `Exception`.**
 * The write it follows has already committed, so throwing here would report a
 * completed tap as failed and, inside the command mutex and a `NonCancellable`
 * region, would propagate out of a command that succeeded. The same call the
 * export nudge makes about a failed stamp after a successful export. What is
 * lost when it happens is a redraw, which the provider's own update period and
 * the next write both recover.
 *
 * `Throwable` is not overreach here, it is the measured requirement: a
 * `NoClassDefFoundError` — an `Error`, not an `Exception` — escapes a narrower
 * catch, propagates out of `appendLocked` and kills a habit creation already
 * written to the log, leaving the editor looking as though Save were dead. Only
 * a device test constructs the real Glance object at all, so `WriteJourneyTest`
 * is what sees it. Cancellation is still rethrown, so the widened catch costs
 * nothing else.
 *
 * `updateAll` resolves the placed widgets, so this is close to free when there
 * are none — which is the common case, and the reason no "is one placed?" guard
 * is worth keeping in front of it.
 *
 * **Every provider in this module has to be named here.** A provider left out
 * still renders when its session starts for other reasons, so it does not look
 * broken — it just stops following writes made in the app, for the life of that
 * session, which is indistinguishable from a widget nobody placed — a cost
 * docs/ux/visual-identity.md §7.4's pricing of a second widget does not carry.
 * `ProjectionRefreshTest` keeps the list honest as more are added.
 */
private const val TAG = "GlanceProjection"

internal class GlanceProjectionListener @Inject constructor(@ApplicationContext private val context: Context) : ProjectionListener {

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun onProjectionChanged() {
        withContext(Dispatchers.IO) {
            // Two layers of guard, and they catch different things. This one is
            // around *building* the list: a GlanceAppWidget reaches Glance's
            // session machinery from its own constructor, which is where the
            // measured NoClassDefFoundError on androidx/work/CoroutineWorker
            // came from (widget/build.gradle.kts). That throw happens before any
            // per-widget catch can exist, so this cannot be folded into
            // refreshEach.
            try {
                refreshEach(refreshedWidgets().map { widget -> suspend { widget.updateAll(context) } }) { e ->
                    // Logged rather than silently dropped. A permanently broken
                    // push — WorkManager failing to start, a corrupt Glance
                    // store — is otherwise indistinguishable from nobody having
                    // placed a widget, which is the failure shape
                    // ProjectionListenerTest exists to rule out.
                    Log.w(TAG, "the widget refresh failed after a committed write", e)
                }
            } catch (e: Throwable) {
                // Rethrows cancellation and nothing else. Deliberately wider
                // than Exception; the KDoc above names the Error that escapes.
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "the widget list could not be built", e)
            }
        }
    }
}

/**
 * Runs every [updates] entry, reporting failures instead of letting one stop the
 * rest.
 *
 * **Why this is a function rather than a loop in place.** A single `try` around
 * the loop means the first provider's failure silently suppresses every provider
 * after it — the freeze [GlanceProjectionListener]'s KDoc calls
 * indistinguishable from a widget nobody placed, arriving by the very mechanism
 * meant to prevent it. `ProjectionRefreshTest` pins the *list* and not the
 * isolation, so the loop is a seam a JVM test can drive with an update that
 * throws — `RefreshIsolationTest`.
 *
 * Cancellation is rethrown through the same `ensureActive()` idiom the rest of
 * this module uses ([toggleHabit]); a failure of one update is not a reason to
 * abandon the others, but a cancelled scope is.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend fun refreshEach(updates: List<suspend () -> Unit>, onFailure: (Throwable) -> Unit) {
    for (update in updates) {
        try {
            update()
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            onFailure(e)
        }
    }
}

/**
 * Every `GlanceAppWidget` this module publishes, and therefore everything a
 * committed write has to redraw.
 *
 * A function rather than a `val`, and fresh instances rather than shared ones,
 * because a `GlanceAppWidget` reaches Glance's session machinery from its own
 * constructor — the same reason the tap path constructs one for `update` rather
 * than holding it ([ToggleHabitAction]). Exposed to tests so the list cannot
 * silently fall behind the manifest.
 */
internal fun refreshedWidgets(): List<GlanceAppWidget> = listOf(TodayWidget(), StreakWidget(), MomoWidget())
