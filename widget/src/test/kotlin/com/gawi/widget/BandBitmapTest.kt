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
        val woven = BandBitmap.render(flags, GEOMETRY, woven = true)!!
        val outstanding = BandBitmap.render(flags, GEOMETRY, woven = false)!!

        assertEquals(listOf(true, false, true, false), woven.centres(4))
        assertEquals(listOf(false, true, false, true), outstanding.centres(4))
    }

    @Test
    fun `the gaps between segments are clear on both masks`() {
        val flags = listOf(true, true, true)
        val mask = BandBitmap.render(flags, GEOMETRY, woven = true)!!
        val segment = (WIDTH - GAP * 2) / 3f
        // The middle of the first gap.
        val x = (segment + GAP / 2f).toInt()
        assertTrue("the gap at x=$x is inked", (0 until HEIGHT).none { y -> Color.alpha(mask.getPixel(x, y)) > 0 })
    }

    @Test
    fun `a state no habit is in draws no mask at all`() {
        assertNull(BandBitmap.render(listOf(true, true), GEOMETRY, woven = false))
        assertNull(BandBitmap.render(emptyList(), GEOMETRY, woven = true))
        assertNull(BandBitmap.render(listOf(true), GEOMETRY.copy(widthPx = 0), woven = true))
    }

    /** Thirty habits — well past Glance's ten-child cap — still draw thirty segments. */
    @Test
    fun `a long day still inks every habit`() {
        val mask = BandBitmap.render(List(30) { true }, GEOMETRY, woven = true)!!
        assertEquals(List(30) { true }, mask.centres(30))
        assertTrue(mask.inkedPixels() > 0)
    }

    @Test
    fun `the mask is tagged with the density it was drawn at`() {
        assertEquals(DPI, BandBitmap.render(listOf(true), GEOMETRY, woven = true)!!.density)
    }

    private fun android.graphics.Bitmap.centres(n: Int): List<Boolean> = (0 until n).map { i ->
        val x = ((i + 0.5f) * width / n).toInt()
        (0 until height).any { y -> Color.alpha(getPixel(x, y)) > 0 }
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 10
        const val GAP = 6f
        const val DPI = 440
        val GEOMETRY = BandBitmap.Geometry(WIDTH, HEIGHT, GAP, DPI)
    }
}
