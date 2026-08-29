package com.gawi.app

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gawi.app.testsupport.WidgetHostBinding
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Momo widget bound to a real host, rendered by real Glance.
 *
 * The third provider, and the cheapest to prove: its tree has no collection and
 * no size gate, so the one thing a JVM test cannot see — that the provider binds
 * and the session composes — is the whole of what this checks, plus the
 * `res/xml-v31` attributes only an `AppWidgetProviderInfo` can read back
 * (`StreakWidgetHostTest` has why). No seeding: with or without habits the
 * widget draws a face and a word, and both branches are strings this test
 * accepts, so ambient data cannot turn it into a coin toss.
 */
@RunWith(AndroidJUnit4::class)
class MomoWidgetHostTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var widget: WidgetHostBinding? = null

    @Before
    fun bind() {
        widget = WidgetHostBinding.bind(context, WidgetHostBinding.MOMO_RECEIVER)
    }

    @After
    fun release() {
        widget?.release()
    }

    @Test
    fun glanceRendersTheMomoWidgetForARealHost() {
        val bound = widget
        assertNotNull("MomoWidgetReceiver is missing from the merged manifest, or `appwidget grantbind` was refused", bound)

        val rendered = bound!!.awaitText(TIMEOUT_SECONDS) { text -> text.any(::isGlanceContent) }

        assertTrue(
            "Glance produced none of this widget's own strings within ${TIMEOUT_SECONDS}s. Rendered: $rendered",
            rendered.any(::isGlanceContent),
        )
        assertFalse("Glance rendered the read-failure state: $rendered", stringByName("widget_unavailable") in rendered)
        println("GAWI_MOMO rendered=$rendered")
    }

    /** Two by two from `res/xml-v31` on API 31+; below it the fields do not exist (`StreakWidgetHostTest` has the measurement). */
    @Test
    fun theApi31AttributesResolveFromTheV31Variant() {
        val info = widget!!.info
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertEquals("targetCellWidth did not resolve from res/xml-v31", 2, info.targetCellWidth)
            assertEquals("targetCellHeight did not resolve from res/xml-v31", 2, info.targetCellHeight)
            assertNotEquals("description did not resolve from res/xml-v31", 0, info.descriptionRes)
            assertNotEquals("previewLayout did not resolve from res/xml-v31", 0, info.previewLayout)
            assertTrue("the description resource is empty", context.getString(info.descriptionRes).isNotBlank())
        } else {
            assertTrue("the base provider xml declared no minWidth", info.minWidth > 0)
            assertTrue("the base provider xml declared no minHeight", info.minHeight > 0)
        }
        println("GAWI_MOMO sdk=${Build.VERSION.SDK_INT} min=${info.minWidth}x${info.minHeight}")
    }

    /**
     * What this widget's composition can emit and the host's own label cannot:
     * a mood sentence (the face's description), the no-habits copy, or the
     * failure copy. The label "Momo" is present from `createView` on, so waiting
     * on non-empty would return before Glance ran — the false pass
     * `StreakWidgetHostTest` already had.
     */
    private fun isGlanceContent(line: String): Boolean = line in MOOD_STRINGS.map(::stringByName) ||
        line == stringByName("widget_no_habits") ||
        line == stringByName("widget_unavailable")

    private fun stringByName(name: String): String = context.getString(context.resources.getIdentifier(name, "string", context.packageName))

    private companion object {
        const val TIMEOUT_SECONDS = 20L
        val MOOD_STRINGS = listOf("widget_mood_thriving", "widget_mood_content", "widget_mood_worried", "widget_mood_regenerating")
    }
}
