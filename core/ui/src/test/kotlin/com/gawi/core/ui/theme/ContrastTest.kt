package com.gawi.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The formula every contrast assertion in the repo runs through, checked at
 * the values WCAG fixes: black on white is 21:1, a colour against itself is
 * 1:1, and the two floors sit where the standard puts them.
 */
class ContrastTest {

    @Test
    fun `black on white is the maximum and a colour on itself the minimum`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.White, Color.White), 1e-6f)
        assertEquals(1f, contrastRatio(Color.Black, Color.Black), 1e-6f)
    }

    @Test
    fun `the ratio does not depend on which colour is named first`() {
        val a = Color(0xFF1F6F78)
        val b = Color(0xFFF2F7F7)
        assertEquals(contrastRatio(a, b), contrastRatio(b, a), 1e-6f)
    }

    @Test
    fun `the floors sit either side of mid grey on white`() {
        // #777777 on white is 4.48:1 — the canonical near-miss for text, and
        // comfortably a pass for a graphic.
        val ratio = contrastRatio(Color(0xFF777777), Color.White)
        assertTrue("$ratio", ratio < WCAG_TEXT_FLOOR)
        assertTrue("$ratio", ratio > WCAG_NON_TEXT_FLOOR)
    }
}
