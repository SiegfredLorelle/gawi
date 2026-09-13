package com.gawi.widget

import android.graphics.Bitmap
import android.graphics.Color
import com.gawi.widget.testsupport.inkedPixels
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The completion mark's geometry, read off the pixels: an outline when a habit
 * is outstanding, a filled mark with its tick cut out when it is done, and
 * nothing touching the edges at any size.
 *
 * Asserted as the shapes the canvas board names rather than as pixel counts —
 * "filled", "an outline", "a tick", "inside its own bounds" — so a redraw that
 * keeps the design keeps the test. `GraphicsMode.NATIVE`, or every count is
 * zero: `BitmapTextTest` records why.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlyphBitmapTest {

    @Test
    fun `no room draws nothing`() {
        assertNull(GlyphBitmap.render(sizePx = 0, densityDpi = DENSITY, checked = false))
        assertNull(GlyphBitmap.render(sizePx = -1, densityDpi = DENSITY, checked = true))
    }

    /** The outstanding mark is a ring: its edge is inked and its middle is not. */
    @Test
    fun `the outstanding mark is an outline`() {
        val mark = render(checked = false)

        assertTrue("the middle of an outline is inked", !mark.inkedAtUnits(18f, 18f))
        // The stroke is 3 units wide centred on the 4.5 inset, so this is its middle.
        assertTrue("the left stroke is clear", mark.inkedAtUnits(4.5f, 18f))
    }

    /** The done mark is filled where the outline was hollow. */
    @Test
    fun `the done mark is filled`() {
        val done = render(checked = true)
        val outstanding = render(checked = false)

        assertTrue("the middle of a filled mark is clear", done.inkedAtUnits(18f, 18f))
        assertTrue("a fill inks no more than an outline", done.inkedPixels() > outstanding.inkedPixels())
    }

    /**
     * The tick is a hole rather than a second colour, so the widget's own ground
     * shows through it — one bitmap carries one tint, and a painted tick would
     * mean a second mask stacked on this one.
     */
    @Test
    fun `the done mark keeps its tick cut out of it`() {
        val done = render(checked = true)

        // The tick's corner, where the two strokes meet: inside the fill, and clear.
        assertTrue("the tick's corner is filled in", !done.inkedAtUnits(16f, 23.5f))
    }

    /** Nothing is drawn against an edge, at any size a density can ask for. */
    @Test
    fun `the mark stays inside its own bounds at every size`() {
        for (size in listOf(18, 27, 36, 54, 72)) {
            for (checked in listOf(false, true)) {
                val mark = GlyphBitmap.render(size, DENSITY, checked)!!
                val edges = (0 until size).flatMap { i ->
                    listOf(mark.getPixel(i, 0), mark.getPixel(i, size - 1), mark.getPixel(0, i), mark.getPixel(size - 1, i))
                }
                assertTrue("a $size px mark (checked=$checked) touches its edge", edges.all { Color.alpha(it) == 0 })
            }
        }
    }

    private fun render(checked: Boolean) = GlyphBitmap.render(SIZE, DENSITY, checked)!!

    /** A point in the board's own 36-unit space, scaled to the rendered size. */
    private fun Bitmap.inkedAtUnits(x: Float, y: Float): Boolean {
        val unit = SIZE / 36f
        return Color.alpha(getPixel((x * unit).toInt(), (y * unit).toInt())) > 0
    }
}

private const val SIZE = 72
private const val DENSITY = 320
