package com.gawi.app.testsupport

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
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
 *
 * Parameterised over the receiver on 2026-08-29, when a second provider arrived.
 * The provider's resolved [AppWidgetProviderInfo] is exposed with it, because
 * that is the only place the API 31 attributes in a `res/xml-v31` variant can be
 * *read back* — `dumpsys appwidget` does not print them, so a `-v31` file that
 * failed to resolve would look identical to one that worked.
 */
class WidgetHostBinding private constructor(
    private val host: AppWidgetHost,
    private val widgetId: Int,
    private val view: AppWidgetHostView,
    /** The provider info the framework parsed, including any `res/xml-v31` half. */
    val info: AppWidgetProviderInfo,
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

    /**
     * Every view class in the rendered tree, innermost last.
     *
     * For asserting *structure* where text is not reachable. A `LazyColumn`
     * becomes a `RemoteViews` collection, and a host view that is never attached
     * to a window never lays out, so its adapter is never asked for item views —
     * the rows exist in the translated tree and have no `View` to traverse. This
     * is what lets a test tell "the collection did not translate" apart from
     * "the collection translated and the harness cannot see into it".
     */
    fun viewClasses(): List<String> {
        var found = emptyList<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { found = classesIn(view) }
        return found
    }

    private fun classesIn(v: View): List<String> = buildList {
        add(v.javaClass.simpleName)
        if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) addAll(classesIn(v.getChildAt(i)))
        }
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

    /**
     * Tell the provider what size the host is drawing it at, in dp, the way a
     * launcher does on placement and resize. `SizeMode.Exact` composes once per
     * reported size, so this is how a test reaches a body gated on size — the
     * large Today body needs 220×170 and a bound view reports nothing until told.
     */
    fun resize(widthDp: Int, heightDp: Int) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
        }
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

    /**
     * What the widget says, whichever view says it. Since 2026-08-25 every string
     * is an Outfit bitmap in an `ImageView`, the copy carrying its text as
     * `contentDescription` and each row's name sitting on its checkbox's — and no
     * `TextView` holds text any more, so a `TextView`-only walk would poll for
     * 20s and fail on a widget Glance drew perfectly. The `TextView` branch reads
     * the description as well as the text for the same reason: a `CheckBox` *is*
     * a `TextView`, so it lands here with `text == ""`, and the first version of
     * this walk dropped every habit row — caught in review before it ran.
     */
    private fun textIn(view: View): List<String> = when (view) {
        is TextView -> textOf(view) + listOfNotNull(view.text?.toString()?.takeIf { it.isNotBlank() })
        is ViewGroup -> textOf(view) + (0 until view.childCount).flatMap { textIn(view.getChildAt(it)) }
        else -> textOf(view)
    }

    private fun textOf(view: View): List<String> = listOfNotNull(view.contentDescription?.toString()?.takeIf { it.isNotBlank() })

    companion object {
        const val RECEIVER = "com.gawi.widget.TodayWidgetReceiver"
        const val STREAK_RECEIVER = "com.gawi.widget.StreakWidgetReceiver"
        const val MOMO_RECEIVER = "com.gawi.widget.MomoWidgetReceiver"
        private const val HOST_ID = 0x6761
        private const val POLL_MILLIS = 250L

        /** Null when the provider is absent or the bind grant was refused. */
        fun bind(context: Context, receiver: String = RECEIVER): WidgetHostBinding? {
            // Drained rather than closed: executeShellCommand returns as soon as
            // the pipe exists, so closing it early races the grant to completion
            // and the grant silently does not happen.
            shell("appwidget grantbind --package ${context.packageName} --user 0")

            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context.packageName, receiver)
            val (host, widgetId, info) = allocate(context, manager, provider) ?: return null

            lateinit var view: AppWidgetHostView
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                host.startListening()
                view = host.createView(context, widgetId, info)
            }
            return WidgetHostBinding(host, widgetId, view, info)
        }

        /**
         * Allocates, binds, and resolves the provider info — or cleans up and
         * returns null, so every failure path shares one teardown.
         *
         * The info is resolved here rather than by the caller because
         * `AppWidgetHostView` dereferences it for its `initialLayout`: a null
         * (the bind raced, or the provider is disabled) would otherwise surface
         * as an NPE inside the framework on the main thread instead of the null
         * this function's callers are documented to expect.
         *
         * Folded into one `when` rather than written as guarded returns, because
         * detekt's `ReturnCount` caps a function at two and this is the shape the
         * codebase already uses for that.
         */
        private fun allocate(
            context: Context,
            manager: AppWidgetManager,
            provider: ComponentName,
        ): Triple<AppWidgetHost, Int, AppWidgetProviderInfo>? = when {
            manager.installedProviders.none { it.provider == provider } -> null

            else -> {
                val host = AppWidgetHost(context, HOST_ID)
                val widgetId = host.allocateAppWidgetId()
                val info = if (manager.bindAppWidgetIdIfAllowed(widgetId, provider)) manager.getAppWidgetInfo(widgetId) else null
                if (info != null) {
                    Triple(host, widgetId, info)
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
