package com.gawi.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.luminance
import com.gawi.widget.testsupport.MIN_CONTRAST
import com.gawi.widget.testsupport.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * [WidgetPalette] is legible in both schemes, and is genuinely two schemes.
 *
 * **Why this exists next to `WidgetTextColourTest`.** That test measures what the
 * widget draws; this one measures the palette it draws *from*, including the two
 * colours no render test can reach — the checkbox glyph's, which `CheckBoxColors`
 * hides behind an `internal` accessor returning a memberless interface.
 *
 * **Why every assertion resolves against a `Context` instead of reading a hex.**
 * Pinning the eight literals would only restate the source file, and it is not
 * the literals that were wrong before 2026-08-28 — it was that the widget's three
 * colours took different translation paths, so one followed a night-mode toggle
 * and two did not. The property that fixes that is *in-process resolution against
 * the running configuration*, so that is what is asserted: resolve each provider
 * in a day and a night `Context` and check the answers behave like a scheme pair.
 *
 * **What this cannot see, stated rather than implied.** A JVM test cannot tell
 * which of Glance's translation paths a provider will take on a real host — that
 * is a property of `RemoteViews`, the launcher's process and the API level.
 * docs/running.md §4 keeps the by-hand toggle on API 29 or 30 for exactly that,
 * and it is the check that found the defect in the first place.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetPaletteTest {

    private val ink = listOf(
        "onSurface" to WidgetPalette.onSurface,
        "glyphChecked" to WidgetPalette.glyphChecked,
        "glyphUnchecked" to WidgetPalette.glyphUnchecked,
    )

    @Test
    fun `every ink is legible on the surface it is drawn on, in both schemes`() {
        for (night in listOf(false, true)) {
            val context = context(night)
            val ground = WidgetPalette.surface.getColor(context)
            for ((name, provider) in ink) {
                val ratio = contrastRatio(provider.getColor(context), ground)
                assertTrue(
                    "$name is $ratio:1 on the surface with night=$night, below $MIN_CONTRAST",
                    ratio >= MIN_CONTRAST,
                )
            }
        }
    }

    /**
     * The fix itself: a colour that answers the same in both configurations is a
     * pinned literal, and a pinned literal is what left the ink behind while the
     * background followed the host.
     */
    @Test
    fun `every colour answers differently in the two schemes`() {
        for ((name, provider) in ink + ("surface" to WidgetPalette.surface)) {
            assertTrue(
                "$name resolves the same in both schemes, so it is not a scheme at all",
                provider.getColor(context(night = false)) != provider.getColor(context(night = true)),
            )
        }
    }

    /**
     * Polarity, which the two tests above would both pass with the day and night
     * values swapped: contrast survives a swap and so does answering differently,
     * but a widget that turns *light* when the phone turns dark does not.
     */
    @Test
    fun `the night scheme darkens the ground and lightens the ink`() {
        val day = WidgetPalette.surface.getColor(context(night = false))
        val night = WidgetPalette.surface.getColor(context(night = true))
        assertTrue("the night surface ($night) is not darker than the day one ($day)", night.luminance() < day.luminance())

        for ((name, provider) in ink) {
            val dayInk = provider.getColor(context(night = false))
            val nightInk = provider.getColor(context(night = true))
            assertTrue(
                "$name does not lighten for the night scheme, so the pair is swapped",
                nightInk.luminance() > dayInk.luminance(),
            )
        }
    }

    /** The same application context, in a configuration with night mode forced either way. */
    private fun context(night: Boolean): Context {
        val application = RuntimeEnvironment.getApplication()
        val configuration = Configuration(application.resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return application.createConfigurationContext(configuration)
    }
}
