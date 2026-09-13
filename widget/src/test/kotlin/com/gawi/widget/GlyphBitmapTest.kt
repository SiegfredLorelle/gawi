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
 * a mark that fills its bitmap at any size.
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

        assertTrue("the middle of an outline is inked", !mark.inkedAtUnits(15f, 15f))
        // The stroke is 3 units wide centred on the 1.5 inset, so this is its middle.
        assertTrue("the left stroke is clear", mark.inkedAtUnits(1.5f, 15f))
    }

    /** The done mark is filled where the outline was hollow. */
    @Test
    fun `the done mark is filled`() {
        val done = render(checked = true)
        val outstanding = render(checked = false)

        assertTrue("the middle of a filled mark is clear", done.inkedAtUnits(15f, 15f))
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
        assertTrue("the tick's corner is filled in", !done.inkedAtUnits(13f, 20.5f))
    }

    /**
     * The mark fills its bitmap at any size a density can ask for: its stroke
     * reaches all four edges and only the rounded corners are clear.
     *
     * This is the property that decides how big the mark is drawn, because the
     * `Image` is sized to the bitmap — a margin baked into the bitmap would
     * shrink the mark inside its own box by exactly that margin and nothing in
     * the tree would say so (docs/ux/widget.md §8).
     */
    @Test
    fun `the mark fills its bitmap at every size`() {
        for (size in listOf(18, 27, 36, 54, 72)) {
            for (checked in listOf(false, true)) {
                val mark = GlyphBitmap.render(size, DENSITY, checked)!!
                val mid = size / 2
                val edges = listOf(
                    mark.getPixel(mid, 0),
                    mark.getPixel(mid, size - 1),
                    mark.getPixel(0, mid),
                    mark.getPixel(size - 1, mid),
                )
                assertTrue("a $size px mark (checked=$checked) falls short of an edge", edges.all { Color.alpha(it) > 0 })
                assertTrue("a $size px mark (checked=$checked) has a square corner", Color.alpha(mark.getPixel(0, 0)) == 0)
            }
        }
    }

    private fun render(checked: Boolean) = GlyphBitmap.render(SIZE, DENSITY, checked)!!

    /** A point in the mark's own 30-unit space, scaled to the rendered size. */
    private fun Bitmap.inkedAtUnits(x: Float, y: Float): Boolean {
        val unit = SIZE / 30f
        return Color.alpha(getPixel((x * unit).toInt(), (y * unit).toInt())) > 0
    }
}

private const val SIZE = 72
private const val DENSITY = 320
