package com.gawi.widget

import android.content.Context
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
 * tapped — `viewModelScope` is `Main.immediate`. Putting the switch in the class
 * that touches the platform is the fix `/code-review` arrived at when nothing on
 * the export path left the caller's dispatcher; the alternative is every call
 * site remembering.
 *
 * **A failure is absorbed, and it catches `Throwable` rather than `Exception`.**
 * The write it follows has already committed, so throwing here would report a
 * completed tap as failed and, inside the command mutex and a `NonCancellable`
 * region, would propagate out of a command that succeeded. The same call the
 * export nudge makes about a failed stamp after a successful export. What is
 * lost when it happens is a redraw, which the provider's own update period and
 * the next write both recover.
 *
 * `Throwable` is not overreach here, it is the measured requirement. An earlier
 * version of this file caught `Exception` and a `NoClassDefFoundError` — an
 * `Error` — escaped it, propagated out of `appendLocked` and killed a habit
 * creation that had already been written to the log; the editor sat there
 * looking as though Save were dead. Found by `WriteJourneyTest` on a device,
 * which is the only kind of test that constructs the real Glance object at all.
 * Cancellation is still rethrown, so the widened catch costs nothing else.
 *
 * `updateAll` resolves the placed widgets, so this is close to free when there
 * are none — which is the common case, and the reason no "is one placed?" guard
 * is worth keeping in front of it.
 */
internal class GlanceProjectionListener @Inject constructor(@ApplicationContext private val context: Context) : ProjectionListener {

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun onProjectionChanged() {
        withContext(Dispatchers.Default) {
            try {
                TodayWidget().updateAll(context)
            } catch (e: Throwable) {
                // Rethrows cancellation and nothing else. Deliberately wider
                // than Exception; the KDoc above says which Error did escape.
                currentCoroutineContext().ensureActive()
            }
        }
    }
}
