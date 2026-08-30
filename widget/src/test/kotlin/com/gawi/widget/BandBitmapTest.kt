package com.gawi.widget

import android.graphics.Color
import com.gawi.widget.testsupport.inkedPixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The band's geometry, read off the pixels: one segment per flag, the mask for
 * a state inked exactly where that state's habits are, gaps between, nothing
 * drawn for a state no habit is in. `GraphicsMode.NATIVE`, or every count is
 * zero — `BitmapTextTest` records why.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BandBitmapTest {

    @Test
    fun `each mask inks its own segments and none of the other's`() {
        val flags = listOf(true, false, true, false)
        val woven = BandBitmap.render(flags, GEOMETRY, woven = true, mirrored = false)!!
        val outstanding = BandBitmap.render(flags, GEOMETRY, woven = false, mirrored = false)!!

        assertEquals(listOf(true, false, true, false), woven.centres(4))
        assertEquals(listOf(false, true, false, true), outstanding.centres(4))
    }

    @Test
    fun `the gaps between segments are clear on both masks`() {
        val flags = listOf(true, true, true)
        val mask = BandBitmap.render(flags, GEOMETRY, woven = true, mirrored = false)!!
        val segment = (WIDTH - GAP * 2) / 3f
        // The middle of the first gap.
        val x = (segment + GAP / 2f).toInt()
        assertTrue("the gap at x=$x is inked", !mask.inkedAt(x))
    }

    @Test
    fun `a state no habit is in draws no mask at all`() {
        assertNull(BandBitmap.render(listOf(true, true), GEOMETRY, woven = false, mirrored = false))
        assertNull(BandBitmap.render(emptyList(), GEOMETRY, woven = true, mirrored = false))
        assertNull(BandBitmap.render(listOf(true), GEOMETRY.copy(widthPx = 0), woven = true, mirrored = false))
    }

    /** Thirty habits — well past Glance's ten-child cap — still draw thirty segments. */
    @Test
    fun `a long day still inks every habit`() {
        val mask = BandBitmap.render(List(30) { true }, GEOMETRY, woven = true, mirrored = false)!!
        assertEquals(List(30) { true }, mask.centres(30))
        assertTrue(mask.inkedPixels() > 0)
    }

    /**
     * Narrower than the habits: 60px for 30 segments is a 2px pitch with a 1px
     * gap and a 1px segment. Every habit is still inside — the thirtieth starts
     * at pixel 58 — where the first cut's fixed 6px gap marched the tail off the
     * bitmap after the tenth.
     */
    @Test
    fun `a day wider than its room keeps every habit on the bitmap`() {
        val mask = BandBitmap.render(List(30) { true }, GEOMETRY.copy(widthPx = 60), woven = true, mirrored = false)!!
        assertEquals(List(30) { true }, mask.centres(30))
        // The last segment alone, at its own place: it starts inside the bitmap
        // and nothing is drawn where the others would be.
        val only = BandBitmap.render(List(29) { false } + true, GEOMETRY.copy(widthPx = 60), woven = true, mirrored = false)!!
        assertEquals(List(29) { false } + true, only.centres(30))
    }

    /**
     * The 2026-08-30 launcher run, as a unit test. Under a Hebrew system locale
     * the rows mirrored and the band did not: the first habit's woven segment
     * stayed at x 49-117, the band's left end, while its own glyph had moved to
     * x 437-501, flush right. Mirrored, the two read the same way again — and
     * the whole picture is the LTR one reflected, column for column, which is
     * the claim the `- segment` form makes and the strongest way to state it.
     */
    @Test
    fun `the band mirrors under an rtl host`() {
        val flags = listOf(true, false, false, false)
        val ltr = BandBitmap.render(flags, GEOMETRY, woven = true, mirrored = false)!!
        val rtl = BandBitmap.render(flags, GEOMETRY, woven = true, mirrored = true)!!

        // The woven habit leads, each band read in its own direction.
        assertEquals(listOf(true, false, false, false), ltr.centres(4))
        assertEquals(listOf(true, false, false, false), rtl.centres(4, mirrored = true))
        (0 until WIDTH).forEach { x ->
            assertEquals("column $x is not the reflection of ${WIDTH - 1 - x}", ltr.inkedAt(WIDTH - 1 - x), rtl.inkedAt(x))
        }
    }

    /**
     * What tells `widthPx - index * pitch - segment` from the tempting
     * `widthPx - (index + 1) * pitch`, which is off by a whole gap: it ends the
     * first segment at `widthPx - gap` rather than flush, and puts the last one
     * flush against the near edge. Both forms reverse the reading order, so
     * [centres] cannot separate them; the two edge columns can. The narrow case
     * is here for the same reason it is above — at a 2px pitch there is no room
     * for an off-by-a-gap to hide in.
     */
    @Test
    fun `a mirrored band strands its gap at the near edge, not the far one`() {
        val first = BandBitmap.render(listOf(true, false, false, false), GEOMETRY, woven = true, mirrored = true)!!
        assertTrue("the first habit is not flush against the far edge", first.inkedAt(WIDTH - 1))
        val last = BandBitmap.render(listOf(false, false, false, true), GEOMETRY, woven = true, mirrored = true)!!
        assertTrue("the gap fell at the far edge instead of the near one", !last.inkedAt(0))

        val narrow = BandBitmap.render(List(30) { true }, GEOMETRY.copy(widthPx = 60), woven = true, mirrored = true)!!
        assertEquals(List(30) { true }, narrow.centres(30, mirrored = true))
        assertTrue("the thirtieth habit ran off the near edge", !narrow.inkedAt(0))
    }

    @Test
    fun `the mask is tagged with the density it was drawn at`() {
        assertEquals(DPI, BandBitmap.render(listOf(true), GEOMETRY, woven = true, mirrored = false)!!.density)
    }

    /**
     * Whether each of [n] segments has ink at its leading pixel — the pixel a
     * reader meets first, which is `i · width / n` left-to-right and the column
     * one in from `width - i · width / n` when [mirrored]. Leading rather than
     * central because the centre would do for a wide band and lands in the gap
     * when a segment is a pixel wide.
     */
    private fun android.graphics.Bitmap.centres(n: Int, mirrored: Boolean = false): List<Boolean> = (0 until n).map { i ->
        val edge = i * width / n.toFloat()
        inkedAt((if (mirrored) width - edge - 1f else edge).toInt().coerceIn(0, width - 1))
    }

    /** Whether the column at [x] carries ink at any height. */
    private fun android.graphics.Bitmap.inkedAt(x: Int): Boolean = (0 until height).any { y -> Color.alpha(getPixel(x, y)) > 0 }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 10
        const val GAP = 6f
        const val DPI = 440
        val GEOMETRY = BandBitmap.Geometry(WIDTH, HEIGHT, GAP, DPI)
    }
}
