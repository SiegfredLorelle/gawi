package com.gawi.app.testsupport

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.TimeUnit

/**
 * Binds the Gawi widget to a real `AppWidgetHost` and reads what Glance drew.
 *
 * Shared by the two instrumented tests that need a live widget, so the awkward
 * parts are stated once. Binding normally requires `BIND_APPWIDGET`, which is
 * signature-level and cannot be granted — so the test takes the launcher's
 * privilege through `appwidget grantbind`, the same shell command a developer
 * would use by hand. The receiver is named as a string because it is `internal`
 * to `:widget` and `:app` cannot reference it.
 */
class WidgetHostBinding private constructor(
    private val host: AppWidgetHost,
    private val widgetId: Int,
    private val view: AppWidgetHostView,
) {

    /**
     * Every non-blank string in the widget's view tree.
     *
     * Text, deliberately, and not "does it have a child": `createView` inflates
     * Glance's `initialLayout` immediately, so a child exists before Glance has
     * run and waiting on one is a pass that proves nothing. The loading layout
     * has no text; every state this widget can draw has some.
     */
    fun renderedText(): List<String> {
        var found = emptyList<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { found = textIn(view) }
        return found
    }

    /** Waits until the rendered text satisfies [predicate], then returns it. */
    fun awaitText(timeoutSeconds: Long, predicate: (List<String>) -> Boolean): List<String> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        var text = renderedText()
        while (System.nanoTime() < deadline && !predicate(text)) {
            Thread.sleep(POLL_MILLIS)
            text = renderedText()
        }
        return text
    }

    fun release() {
        // Paired with bind()'s startListening, and on the same thread. deleteHost
        // does tear the host down, so this is not a leak past the process — but
        // two tests bind and release in one instrumentation process, and this is
        // the documented way out.
        InstrumentationRegistry.getInstrumentation().runOnMainSync { host.stopListening() }
        host.deleteAppWidgetId(widgetId)
        host.deleteHost()
    }

    private fun textIn(view: View): List<String> = when (view) {
        is TextView -> listOfNotNull(view.text?.toString()?.takeIf { it.isNotBlank() })
        is ViewGroup -> (0 until view.childCount).flatMap { textIn(view.getChildAt(it)) }
        else -> emptyList()
    }

    companion object {
        const val RECEIVER = "com.gawi.widget.TodayWidgetReceiver"
        private const val HOST_ID = 0x6761
        private const val POLL_MILLIS = 250L

        /** Null when the provider is absent or the bind grant was refused. */
        fun bind(context: Context): WidgetHostBinding? {
            // Drained rather than closed: executeShellCommand returns as soon as
            // the pipe exists, so closing it early races the grant to completion
            // and the grant silently does not happen.
            shell("appwidget grantbind --package ${context.packageName} --user 0")

            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context.packageName, RECEIVER)
            val (host, widgetId) = allocate(context, manager, provider) ?: return null

            lateinit var view: AppWidgetHostView
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                host.startListening()
                view = host.createView(context, widgetId, manager.getAppWidgetInfo(widgetId))
            }
            return WidgetHostBinding(host, widgetId, view)
        }

        /**
         * Folded into one `when` rather than written as three guarded returns,
         * because detekt's `ReturnCount` caps a function at two and this is the
         * shape the codebase already uses for that.
         */
        private fun allocate(context: Context, manager: AppWidgetManager, provider: ComponentName): Pair<AppWidgetHost, Int>? = when {
            manager.installedProviders.none { it.provider == provider } -> null

            else -> {
                val host = AppWidgetHost(context, HOST_ID)
                val widgetId = host.allocateAppWidgetId()
                if (manager.bindAppWidgetIdIfAllowed(widgetId, provider)) {
                    host to widgetId
                } else {
                    host.deleteAppWidgetId(widgetId)
                    host.deleteHost()
                    null
                }
            }
        }

        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).use { it.readBytes().decodeToString() }
    }
}
