package com.gawi.core.ui.theme

import androidx.compose.ui.graphics.Color
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

    private val lightBackground = Color(0xFFFFFBFE)
    private val darkBackground = Color(0xFF141218)

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
    fun `a half-transparent colour is judged on the blend, not the tint`() {
        // White at 50% over black renders mid-grey, which sits below the pivot
        // and so still wants a light glyph.
        assertEquals(Color.White, glyphColorOn(Color(0x80FFFFFF), darkBackground))
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
