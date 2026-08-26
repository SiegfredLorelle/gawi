package com.gawi.feature.today

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import com.gawi.core.domain.mascot.Mood
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The tank life draws what docs/ux/momo.md §4 says, measured in pixels — the
 * same rig as `MomoRenderTest`, for the same reasons: `GraphicsMode.NATIVE`
 * because Robolectric's default Canvas paints nothing, and no composition
 * because `drawHabitat` is a `DrawScope` function of a frame.
 *
 * The bitmap is a tank at 1 dp = 1 px, 362 by 250 as the canvas drew it, so
 * the regions below are the canvas's own coordinates.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HabitatRenderTest {

    @Test
    fun `weeds grow from the floor at both edges and nowhere in the middle`() {
        val pixels = render(Mood.CONTENT)
        assertTrue(inkIn(pixels, IntRect(0, HEIGHT - 60, 90, HEIGHT)) > MIN_INK)
        assertTrue(inkIn(pixels, IntRect(WIDTH - 90, HEIGHT - 60, WIDTH, HEIGHT)) > MIN_INK)
        assertEquals(0, inkIn(pixels, IntRect(120, HEIGHT - 60, WIDTH - 120, HEIGHT)))
    }

    @Test
    fun `bubbles rise above the weeds and stop while regenerating`() {
        // Part way through the loop every lane has a bubble in the air.
        val content = render(Mood.CONTENT, seconds = 3f)
        assertTrue(inkIn(content, IntRect(0, 40, WIDTH, HEIGHT - 70), BUBBLE) > 0)
        val regenerating = render(Mood.REGENERATING, seconds = 3f)
        assertEquals(0, inkIn(regenerating, IntRect(0, 0, WIDTH, HEIGHT), BUBBLE))
    }

    @Test
    fun `regenerating greys the weeds and leans them outward`() {
        val full = render(Mood.CONTENT)
        val drained = render(Mood.REGENERATING)
        assertEquals(0, inkIn(drained, IntRect(0, 0, WIDTH, HEIGHT), WEED))
        assertTrue(inkIn(drained, IntRect(0, 0, WIDTH, HEIGHT), WEED_DRAINED) > MIN_INK)
        // Leaning left, the left weeds reach further left than upright ones and
        // no longer reach as high.
        fun top(p: IntArray) = (0 until HEIGHT).first { y -> (0 until 90).any { x -> pixel(p, x, y) != 0 } }
        assertTrue(top(drained) > top(full))
    }

    @Test
    fun `the resting frame is deterministic and the clock moves it`() {
        assertArrayEquals(render(Mood.CONTENT), render(Mood.CONTENT))
        assertNotEquals(render(Mood.CONTENT, 0f).toList(), render(Mood.CONTENT, 1.3f).toList())
    }

    @Test
    fun `half way through a change the weeds are between both leans`() {
        val from = HabitatFrame.at(Mood.CONTENT, 0f)
        val to = HabitatFrame.at(Mood.REGENERATING, 0f)
        val mid = render(HabitatFrame.between(from, to, 0.5f))
        assertNotEquals(render(Mood.CONTENT).toList(), mid.toList())
        assertNotEquals(render(Mood.REGENERATING).toList(), mid.toList())
        // The colour is between the two roles as well: neither pure weed nor pure drained ink appears.
        assertEquals(0, inkIn(mid, IntRect(0, 0, WIDTH, HEIGHT), WEED))
        assertEquals(0, inkIn(mid, IntRect(0, 0, WIDTH, HEIGHT), WEED_DRAINED))
    }

    private fun render(mood: Mood, seconds: Float = 0f) = render(HabitatFrame.at(mood, seconds))

    private fun render(frame: HabitatFrame): IntArray {
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawHabitat(frame, HabitatColours(weed = WEED, weedDrained = WEED_DRAINED, bubble = BUBBLE))
        }
        val map = bitmap.toPixelMap()
        return IntArray(WIDTH * HEIGHT) { i -> argb(map[i % WIDTH, i / WIDTH].value) }
    }

    private fun argb(color: ULong): Int {
        val c = Color(color)
        return ((c.alpha * 255).toInt() shl 24) or ((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or
            (c.blue * 255).toInt()
    }

    private fun pixel(pixels: IntArray, x: Int, y: Int) = pixels[y * WIDTH + x]

    /**
     * Painted pixels in a region — any, or only those whose hue is [colour]'s.
     * Alpha is ignored because everything here is translucent; the channels are
     * compared premultiplied-free by ratio, which survives the alpha blend.
     */
    private fun inkIn(pixels: IntArray, region: IntRect, colour: Color? = null): Int {
        var n = 0
        for (y in region.top until region.bottom) {
            for (x in region.left until region.right) {
                val p = pixel(pixels, x, y)
                if ((p ushr 24) == 0) continue
                if (colour == null || sameHue(p, colour)) n++
            }
        }
        return n
    }

    private fun sameHue(argb: Int, colour: Color): Boolean {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return kotlin.math.abs(r - colour.red) < 0.02f && kotlin.math.abs(g - colour.green) < 0.02f &&
            kotlin.math.abs(b - colour.blue) < 0.02f
    }

    private companion object {
        const val WIDTH = 362
        const val HEIGHT = 250
        const val MIN_INK = 150
        val WEED = Color(0xFF1F6F78)
        val WEED_DRAINED = Color(0xFF5B6D70)
        val BUBBLE = Color(0xFFFFFFFF)
    }
}
