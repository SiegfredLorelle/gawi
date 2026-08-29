package com.gawi.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gawi.app.testsupport.WidgetHostBinding
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widget bound to a real host, rendered by real Glance.
 *
 * **Why this exists and what only it can prove.** Everything else about the
 * widget is a pure function (`:widget`'s JVM tests) or a fake
 * (`ProjectionListenerTest`). Nothing else constructs the real
 * `GlanceAppWidget` or runs its session — and that session is a
 * `CoroutineWorker` on WorkManager, the dependency that forced a permission
 * decision (docs/ux/widget.md §5). It is also how a `NoClassDefFoundError`
 * once reached a committed write.
 *
 * **Not a substitute for placing the widget on a launcher.** Pinning needs the
 * user, so `docs/running.md`'s checklist stays manual. What this replaces is
 * the part a machine can do: that the provider binds, and that Glance produces
 * content for it without crashing or being refused a permission.
 */
@RunWith(AndroidJUnit4::class)
class WidgetHostTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var widget: WidgetHostBinding? = null

    @Before
    fun bindWidget() {
        widget = WidgetHostBinding.bind(context)
    }

    @After
    fun releaseWidget() {
        widget?.release()
    }

    @Test
    fun glanceRendersTheWidgetForARealHost() {
        val bound = widget
        assertNotNull(
            "the provider is missing from the merged manifest, or `appwidget grantbind` was refused",
            bound,
        )

        val rendered = bound!!.awaitText(TIMEOUT_SECONDS) { it.isNotEmpty() }

        assertTrue(
            "Glance produced no text for a bound widget within ${TIMEOUT_SECONDS}s",
            rendered.isNotEmpty(),
        )

        // "It drew something" is not enough: the read-failure copy is text too,
        // so a throwing observeToday() would turn this green while the message
        // above blamed the session worker. That is the confusion strings.xml is
        // split to prevent (docs/ux/widget.md §4), and the one automated check
        // that Glance renders should not be blind to it.
        //
        // :app cannot name :widget's R class — non-transitive R classes are the
        // default — but the resource is resolvable by name at runtime.
        val unavailable = context.getString(
            context.resources.getIdentifier("widget_unavailable", "string", context.packageName),
        )
        assertFalse("Glance rendered the read-failure state: $rendered", unavailable in rendered)

        // Printed so a run says what it drew, not merely that it drew something.
        println("GAWI_WIDGET rendered=$rendered")
    }

    /**
     * The Today provider recomposes at the size a host reports, and past the
     * height gate draws a mood (docs/ux/widget.md §7). Told it is 250×200dp —
     * the four-by-three placement the canvas drew — the widget must emit a
     * mood sentence, or the empty copy on an unseeded device.
     *
     * **What this does not prove, stated because the first version claimed
     * it.** The mood sentence is the large body's drawn line *and* the
     * face-above-rows body's content description, and this harness reads
     * both, so it cannot tell the two tall bodies apart — only that the size
     * was taken and the height gate passed. The width gate is pinned by
     * `WidgetBodyTest` and `HeaderCopyTest`, and the header itself is a
     * launcher check in docs/running.md §4.
     */
    @Test
    fun theProviderRecomposesAtTheReportedSizeAndDrawsAMood() {
        val bound = widget!!
        bound.resize(LARGE_WIDTH_DP, LARGE_HEIGHT_DP)
        val moods = WidgetHostBinding.MOOD_STRINGS.map { WidgetHostBinding.stringByName(context, it) }
        val empty = WidgetHostBinding.stringByName(context, "widget_no_habits")

        val rendered = bound.awaitText(TIMEOUT_SECONDS) { text -> text.any { it in moods } || empty in text }

        assertTrue(
            "neither a mood nor the empty copy rendered at ${LARGE_WIDTH_DP}x$LARGE_HEIGHT_DP: $rendered",
            rendered.any { it in moods } || empty in rendered,
        )
        println("GAWI_WIDGET large=$rendered")
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
        const val LARGE_WIDTH_DP = 250
        const val LARGE_HEIGHT_DP = 200
    }
}
