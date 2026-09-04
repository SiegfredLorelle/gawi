package com.gawi.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.gawi.core.testing.WCAG_TEXT_FLOOR
import com.gawi.core.testing.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The colour rules, asserted without a device.
 *
 * A plain JVM test with no Robolectric, which is the whole reason
 * [parseHabitColor] is hand-rolled rather than delegating to
 * `android.graphics.Color`.
 */
class HabitColorTest {

    // The surfaces the app actually draws on, not literals. These used to be
    // Material's default surfaces, which stopped being what the app draws the
    // moment GawiTheme got a scheme of its own — a test measuring the wrong
    // background is the failure mode this whole file exists to catch.
    private val lightBackground = GawiLightColors.surface
    private val darkBackground = GawiDarkColors.surface

    @Test
    fun `a habit colour is parsed in both lengths and survives anything else`() {
        // Unvalidated off the event log, so a caller degrades rather than crashes.
        assertEquals(Color(0xFFAABBCC), parseHabitColor("#aabbcc"))
        assertEquals(Color(0x80AABBCC), parseHabitColor("#80aabbcc"))
        assertNull(parseHabitColor("aabbcc"))
        assertNull(parseHabitColor("#abc"))
        assertNull(parseHabitColor("#gggggg"))
        assertNull(parseHabitColor(""))
    }

    @Test
    fun `a signed hex string is not a colour`() {
        // toLongOrNull accepts a leading sign, so this is six digits that parse
        // to a negative number and mask into an arbitrary opaque colour.
        assertNull(parseHabitColor("#-abcde"))
        assertNull(parseHabitColor("#-abcdefa"))
        assertNull(parseHabitColor("#+abcde"))
    }

    @Test
    fun `the glyph contrasts with an opaque habit colour`() {
        // The case that broke: a theme content role would be invisible on both.
        assertEquals(Color.White, glyphColorOn(Color(0xFF000000), lightBackground))
        assertEquals(Color.Black, glyphColorOn(Color(0xFFFFFFFF), lightBackground))
    }

    @Test
    fun `the glyph contrasts with what a translucent colour renders as`() {
        // luminance() reads RGB and ignores alpha, so a transparent white is
        // "bright" on paper while what shows through is the background. Judged
        // on the tint alone, both of these would pick black.
        assertEquals(Color.White, glyphColorOn(Color(0x00FFFFFF), darkBackground))
        assertEquals(Color.Black, glyphColorOn(Color(0x00FFFFFF), lightBackground))
    }

    @Test
    fun `a mostly-transparent colour is judged on the blend, not the tint`() {
        // White at 12% over the dark background renders nearly as dark as the
        // background, so it wants a light glyph. Judged on the tint alone it is
        // white and would pick a dark one — which is the whole point of
        // compositing first.
        assertEquals(Color.White, glyphColorOn(Color(0x20FFFFFF), darkBackground))
        assertEquals(Color.Black, glyphColorOn(Color(0xFFFFFFFF), darkBackground))
    }

    @Test
    fun `every colour the editor offers clears the contrast floor as drawn`() {
        // The defect this pins: the pivot used to be 0.5f, the midpoint of the
        // range rather than the crossover, and six of these eight drew below
        // 4.5:1 — in every case picking the worse of the two available glyphs.
        // Asserted as a ratio rather than as an expected colour, so the
        // property survives a future retune of the hues.
        listOf(lightBackground, darkBackground).forEach { background ->
            HabitPalette.Colors.forEach { hex ->
                val tint = parseHabitColor(hex)!!
                val ratio = contrastRatio(glyphColorOn(tint, background), tint.compositeOver(background))
                assertTrue("$hex on $background drew at $ratio", ratio >= WCAG_TEXT_FLOOR)
            }
        }
    }

    @Test
    fun `the pivot picks the better of the two glyphs at every luminance`() {
        // A sweep rather than a spot check, because the old value was wrong for
        // a whole band and not for one colour. Grey is enough: the decision
        // reads luminance only.
        (0..100).forEach { step ->
            val grey = Color(step / 100f, step / 100f, step / 100f)
            val picked = glyphColorOn(grey, lightBackground)
            val rejected = if (picked == Color.Black) Color.White else Color.Black
            val chosen = contrastRatio(picked, grey)
            assertTrue("grey $step picked the worse glyph", chosen >= contrastRatio(rejected, grey))
            assertTrue("grey $step drew at $chosen", chosen >= WCAG_TEXT_FLOOR)
        }
    }

    @Test
    fun `every colour the editor offers is one the parser accepts`() {
        // The palette is what makes a stored colour valid by construction. If
        // these two ever disagree the form would offer a swatch that draws as a
        // fallback the moment it is saved.
        HabitPalette.Colors.forEach { hex -> assertTrue(hex, parseHabitColor(hex) != null) }
        assertTrue(HabitPalette.DefaultColor in HabitPalette.Colors)
        assertTrue(HabitPalette.DefaultIcon in HabitPalette.Icons)
    }

    @Test
    fun `the palette offers no duplicates`() {
        assertEquals(HabitPalette.Colors.size, HabitPalette.Colors.toSet().size)
        assertEquals(HabitPalette.Icons.size, HabitPalette.Icons.toSet().size)
    }
}
