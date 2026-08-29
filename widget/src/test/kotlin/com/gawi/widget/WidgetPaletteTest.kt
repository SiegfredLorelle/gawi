package com.gawi.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.luminance
import androidx.glance.unit.ColorProvider
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
 * Grown with the streak widget on 2026-08-29: `caption`, `streakWeeks` and the
 * two names that share a role with the checkbox glyphs. `streakWeeks` matters
 * most — `tertiary` is the one role no surface in this module drew before, so
 * nothing had ever measured it against the widget's own ground.
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

    /**
     * Every colour the widget draws: the `:core:ui` role it is derived from, and
     * the ground it is drawn on — `null` for the grounds and fills themselves.
     * One table, so each loop below derives its set from it rather than naming
     * roles: the next ink drawn on a ground other than the surface is measured
     * against that ground by adding one row here, and cannot be measured against
     * the wrong one or fall out of the polarity check. Review found the first
     * cut special-casing the Momo widget's caption by name in three places.
     */
    private class Entry(val name: String, val provider: ColorProvider, val role: GawiRole, val ground: ColorProvider?)

    private val entries = listOf(
        Entry("surface", WidgetPalette.surface, GawiRole.Surface, ground = null),
        Entry("onSurface", WidgetPalette.onSurface, GawiRole.OnSurface, WidgetPalette.surface),
        Entry("glyphChecked", WidgetPalette.glyphChecked, GawiRole.Primary, WidgetPalette.surface),
        Entry("glyphUnchecked", WidgetPalette.glyphUnchecked, GawiRole.Outline, WidgetPalette.surface),
        Entry("caption", WidgetPalette.caption, GawiRole.OnSurfaceVariant, WidgetPalette.surface),
        Entry("streakDays", WidgetPalette.streakDays, GawiRole.Primary, WidgetPalette.surface),
        Entry("streakWeeks", WidgetPalette.streakWeeks, GawiRole.Tertiary, WidgetPalette.surface),
        Entry("streakBroken", WidgetPalette.streakBroken, GawiRole.Outline, WidgetPalette.surface),
        // Grown 2026-08-29 with the Momo widget and the large Today body: a second
        // ground, the one ink drawn on it, and the band's two fills.
        Entry("momoGround", WidgetPalette.momoGround, GawiRole.PrimaryContainer, ground = null),
        Entry("momoCaption", WidgetPalette.momoCaption, GawiRole.OnPrimaryContainer, WidgetPalette.momoGround),
        Entry("bandWoven", WidgetPalette.bandWoven, GawiRole.Primary, ground = null),
        Entry("bandOutstanding", WidgetPalette.bandOutstanding, GawiRole.OutlineVariant, ground = null),
    )

    /** Everything drawn *on* a ground. */
    private val inks = entries.filter { it.ground != null }

    /** The grounds and fills — nothing is drawn in them, so they owe no text contrast and darken at night. */
    private val grounds = entries.filter { it.ground == null }

    @Test
    fun `every ink is legible on the ground it is drawn on, in both schemes`() {
        for (night in listOf(false, true)) {
            val context = context(night)
            for (ink in inks) {
                val ratio = contrastRatio(ink.provider.getColor(context), ink.ground!!.getColor(context))
                assertTrue("${ink.name} is $ratio:1 on its ground with night=$night, below $MIN_CONTRAST", ratio >= MIN_CONTRAST)
            }
        }
    }

    /**
     * The band's two fills tell woven from outstanding with nothing but their
     * ground, so the pair is held to WCAG 1.4.11's 3:1 the way the history grid's
     * two cell fills are in `GawiColorSchemeTest`. Swapping the two providers
     * passes this and fails the polarity test below, which is the pair of checks
     * a mutation of either needs.
     */
    @Test
    fun `a woven segment and an outstanding one differ by the non-text floor`() {
        for (night in listOf(false, true)) {
            val context = context(night)
            val ratio = contrastRatio(WidgetPalette.bandWoven.getColor(context), WidgetPalette.bandOutstanding.getColor(context))
            assertTrue("the band's two fills are $ratio:1 apart with night=$night, below $NON_TEXT_CONTRAST", ratio >= NON_TEXT_CONTRAST)
        }
    }

    /**
     * The fix itself: a colour that answers the same in both configurations is a
     * pinned literal, and a pinned literal is what left the ink behind while the
     * background followed the host.
     */
    @Test
    fun `every colour answers differently in the two schemes`() {
        for (entry in entries) {
            assertTrue(
                "${entry.name} resolves the same in both schemes, so it is not a scheme at all",
                entry.provider.getColor(context(night = false)) != entry.provider.getColor(context(night = true)),
            )
        }
    }

    /**
     * Polarity, which the two tests above would both pass with the day and night
     * values swapped: contrast survives a swap and so does answering differently,
     * but a widget that turns *light* when the phone turns dark does not. Grounds
     * darken; inks lighten. `bandWoven` is a fill that is also `primary`, so it
     * lightens like the glyph it shares a role with — listed as a ground for the
     * contrast loop, excepted here by role rather than by name.
     */
    @Test
    fun `the night scheme darkens the grounds and lightens the inks`() {
        for (entry in grounds.filter { it.role != GawiRole.Primary }) {
            assertTrue(
                "${entry.name} does not darken for the night scheme, so the pair is swapped",
                entry.provider.getColor(context(night = true)).luminance() < entry.provider.getColor(context(night = false)).luminance(),
            )
        }
        for (entry in inks + grounds.filter { it.role == GawiRole.Primary }) {
            assertTrue(
                "${entry.name} does not lighten for the night scheme, so the pair is swapped",
                entry.provider.getColor(context(night = true)).luminance() > entry.provider.getColor(context(night = false)).luminance(),
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
        for (entry in entries) {
            for (night in listOf(false, true)) {
                assertEquals(
                    "${entry.name} is not gawiRole(${entry.role}) with night=$night",
                    gawiRole(entry.role, darkTheme = night),
                    entry.provider.getColor(context(night)),
                )
            }
        }
    }

    private companion object {
        /** WCAG 1.4.11, for two fills whose difference is the information. */
        const val NON_TEXT_CONTRAST = 3f
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
