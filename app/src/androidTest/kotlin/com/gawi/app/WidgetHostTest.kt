package com.gawi.app

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The widget bound to a real host, rendered by real Glance.
 *
 * **Why this test exists and what only it can prove.** Everything else about the
 * widget is either a pure function (`:widget`'s JVM tests) or a fake
 * (`ProjectionListenerTest`). Nothing else constructs the real `GlanceAppWidget`
 * or runs its session — and that session is a `CoroutineWorker` on WorkManager,
 * which is the dependency that forced a permission decision
 * (docs/ux/widget.md §5). It is also how a `NoClassDefFoundError` reached a
 * committed write once already.
 *
 * **It is not a substitute for placing the widget on a launcher.** Pinning one
 * needs the user, so `docs/running.md`'s widget checklist stays manual. What
 * this replaces is the part of that checklist a machine *can* do: that the
 * provider binds at all, and that Glance produces `RemoteViews` for it without
 * crashing or being refused a permission.
 *
 * Binding normally requires `BIND_APPWIDGET`, which is signature-level and
 * cannot be granted — so the test grants itself the launcher's privilege through
 * `appwidget grantbind`, the same shell command a developer would use by hand.
 * The component is named as a string because `TodayWidgetReceiver` is `internal`
 * to `:widget` and `:app` cannot reference it.
 */
@RunWith(AndroidJUnit4::class)
class WidgetHostTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var host: AppWidgetHost
    private var widgetId = 0

    @Before
    fun grantBindPermission() {
        // Drain the stream rather than closing it: executeShellCommand returns
        // as soon as the pipe exists, so closing immediately can race the
        // command to completion and the grant silently does not happen.
        shell("appwidget grantbind --package ${context.packageName} --user 0")
        host = AppWidgetHost(context, HOST_ID)
        widgetId = host.allocateAppWidgetId()
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
    ).use { it.readBytes().decodeToString() }

    @After
    fun releaseWidget() {
        host.deleteAppWidgetId(widgetId)
        host.deleteHost()
    }

    @Test
    fun glanceRendersTheWidgetForARealHost() {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context.packageName, RECEIVER)

        assertTrue(
            "the provider is not in the merged manifest",
            manager.installedProviders.any { it.provider == provider },
        )

        val bound = manager.bindAppWidgetIdIfAllowed(widgetId, provider)
        assertTrue(
            "bindAppWidgetIdIfAllowed refused. grant state:\n" + shell("dumpsys appwidget").lines()
                .filter { it.contains("Grant") || it.contains(context.packageName) }
                .take(10).joinToString("\n"),
            bound,
        )

        // Not "the host view got a child" — createView inflates Glance's
        // initialLayout immediately, so that fires before Glance has run and is
        // a pass that proves nothing. What is waited for is *text*, which the
        // loading layout has none of and every state of this widget has some of.
        lateinit var view: AppWidgetHostView
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            host.startListening()
            view = host.createView(context, widgetId, manager.getAppWidgetInfo(widgetId))
        }

        val rendered = awaitRenderedText(view)

        assertTrue(
            "Glance produced no text for a bound widget within ${TIMEOUT_SECONDS}s — " +
                "provideGlance, its session worker, or WorkManager did not run",
            rendered.isNotEmpty(),
        )
        // Printed so a run says what it actually drew rather than only that it
        // drew something; a widget rendering the loading layout forever would
        // otherwise be indistinguishable from one rendering habits.
        println("GAWI_WIDGET rendered=" + rendered)
    }

    /** Every non-blank string in the host view's tree, polled until there is one. */
    private fun awaitRenderedText(view: AppWidgetHostView): List<String> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        var found = emptyList<String>()
        while (System.nanoTime() < deadline && found.isEmpty()) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { found = textIn(view) }
            if (found.isEmpty()) Thread.sleep(POLL_MILLIS)
        }
        return found
    }

    private fun textIn(view: View): List<String> = when (view) {
        is TextView -> listOfNotNull(view.text?.toString()?.takeIf { it.isNotBlank() })
        is ViewGroup -> (0 until view.childCount).flatMap { textIn(view.getChildAt(it)) }
        else -> emptyList()
    }

    private companion object {
        const val HOST_ID = 0x6761
        const val RECEIVER = "com.gawi.widget.TodayWidgetReceiver"
        const val TIMEOUT_SECONDS = 20L
        const val POLL_MILLIS = 250L
    }
}
