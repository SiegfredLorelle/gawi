package com.gawi.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gawi.app.testsupport.WidgetHostBinding
import org.junit.After
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
            "Glance produced no text for a bound widget within ${TIMEOUT_SECONDS}s — " +
                "provideGlance, its session worker, or WorkManager did not run",
            rendered.isNotEmpty(),
        )
        // Printed so a run says what it drew, not merely that it drew something.
        println("GAWI_WIDGET rendered=$rendered")
    }

    private companion object {
        const val TIMEOUT_SECONDS = 20L
    }
}
