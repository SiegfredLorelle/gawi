package com.gawi.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.luminance
import com.gawi.core.ui.theme.GawiRole
import com.gawi.core.ui.theme.gawiRole
import com.gawi.widget.testsupport.MIN_CONTRAST
import com.gawi.widget.testsupport.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * [WidgetPalette] is legible in both schemes, and is genuinely two schemes.
 *
 * **Why this exists next to `WidgetTextColourTest`.** That test measures what the
 * widget draws, and since 2026-08-28 it reaches the checkbox glyph too. This one
 * measures the palette the widget draws *from*, independent of any tree — so a
 * colour is covered here even in a state no render test happens to compose, and
 * the polarity check below has nothing to do with drawing at all.
 *
 * **Why most assertions resolve against a `Context` instead of reading a hex.**
 * Pinning these eight literals against this file's own source would only restate
 * it, and the literals are not what was wrong before 2026-08-28 — it was that the
 * widget's three colours took different translation paths, so one followed a
 * night-mode toggle and two did not. The property that fixes that is *in-process
 * resolution against the running configuration*, so that is what is asserted:
 * resolve each provider in a day and a night `Context` and check the answers
 * behave like a scheme pair.
 *
 * **What changed on 2026-08-29, and what it did to this file.** [WidgetPalette]
 * no longer holds hexes: every value is derived from `:core:ui`'s `gawiRole`, so
 * the old "is `surface` still the app's window background?" test became a
 * restatement of one function calling another and is gone. What replaced it is
 * the question deriving actually leaves open — *which role does each widget
 * colour draw?* — because that mapping is a decision (`primary` for a completed
 * glyph, `outline` for an outstanding one, semantic per visual-identity §4.1)
 * and swapping two of them would still compile, still be two schemes, and still
 * have the right polarity.
 *
 * The contrast test below also changed meaning without changing a line, and this
 * is the tripwire §7.4 asked for: a retune in `core/ui/theme/Color.kt` now
 * reaches this module, so a role that drops under 4.5:1 on the widget's ground
 * fails here. Before, a retune could not fail anything in `:widget` at all.
 *
 * **What this cannot see, stated rather than implied.** A JVM test cannot tell
 * which of Glance's translation paths a provider will take on a real host — that
 * is a property of `RemoteViews`, the launcher's process and the API level.
 * docs/running.md §4 keeps the by-hand toggle on API 29 or 30 for exactly that,
 * and it is the check that found the defect in the first place.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetPaletteTest {

    /** Every colour the widget draws, with the `:core:ui` role it is derived from. */
    private val roles = listOf(
        Triple("surface", WidgetPalette.surface, GawiRole.Surface),
        Triple("onSurface", WidgetPalette.onSurface, GawiRole.OnSurface),
        Triple("glyphChecked", WidgetPalette.glyphChecked, GawiRole.Primary),
        Triple("glyphUnchecked", WidgetPalette.glyphUnchecked, GawiRole.Outline),
    )

    /** The three that are drawn *on* [WidgetPalette.surface] rather than being it. */
    private val ink = roles.filterNot { it.third == GawiRole.Surface }.map { it.first to it.second }

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

    /**
     * Each widget colour draws the role it says it draws.
     *
     * The mapping is the part deriving does not settle. A completed glyph is
     * `primary` and an outstanding one is `outline` because visual-identity §4.1
     * makes both semantic; swap them and the widget still resolves two schemes
     * with the right polarity and every contrast still clears the floor, so
     * nothing else in this file would notice. This is also what keeps a literal
     * from creeping back in: a hand-typed hex equal to today's value would pass
     * every other test here and fail this one the moment `:core:ui` moved.
     */
    @Test
    fun `each colour is the core-ui role it claims, both schemes`() {
        for ((name, provider, role) in roles) {
            for (night in listOf(false, true)) {
                assertEquals(
                    "$name is not gawiRole($role) with night=$night",
                    gawiRole(role, darkTheme = night),
                    provider.getColor(context(night)),
                )
            }
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
