package com.gawi.feature.today

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.ui.component.MomoFrame
import com.gawi.core.ui.component.MomoMotion
import com.gawi.core.ui.component.drawMomo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Momo draws what docs/ux/momo.md §3 says, measured in pixels.
 *
 * Here and not in `:core:ui`, which is deliberately Robolectric-free (its
 * `GawiIconsTest` reads XML off disk for the same reason). `GraphicsMode.NATIVE`
 * on this class only, for the reason `BitmapTextTest` gives: Robolectric's
 * default `Canvas` accepts every call and paints nothing, so a pixel assertion
 * there passes on a blank bitmap.
 *
 * No composition: `drawMomo` is a `DrawScope` function of a mood and a
 * [MomoFrame], so it is driven through a `CanvasDrawScope` on an `ImageBitmap`.
 * That is what makes "the same mood at the same second is the same picture"
 * an assertion rather than a hope, and it is the route the widget's still
 * frame will take.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MomoRenderTest {

    @Test
    fun `every mood renders ink at rest`() {
        Mood.entries.forEach { mood ->
            val inked = render(mood).count { it != 0 }
            assertTrue("$mood drew nothing", inked > MIN_INK)
        }
    }

    @Test
    fun `the four resting faces differ pairwise`() {
        val frames = Mood.entries.associateWith { render(it) }
        Mood.entries.forEach { a ->
            Mood.entries.filter { it > a }.forEach { b ->
                assertNotEquals("$a and $b drew the same picture", frames[a]!!.toList(), frames[b]!!.toList())
            }
        }
    }

    @Test
    fun `the resting frame is deterministic`() {
        assertArrayEquals(render(Mood.CONTENT), render(Mood.CONTENT))
        assertEquals(MomoFrame.rest(Mood.WORRIED), MomoFrame.at(Mood.WORRIED, 0f))
    }

    @Test
    fun `regenerating draws a shorter right upper gill`() {
        // The gill reaches from (176,78) toward (199.6,59.5) at full length and
        // stops at (191.6,65.8) while regrowing. Count pink in the region only
        // the full gill's outer beads reach.
        fun outerBeadInk(mood: Mood) = pinkIn(render(mood), left = 196, top = 44, right = 216, bottom = 66)
        assertTrue(outerBeadInk(Mood.CONTENT) > 0)
        assertTrue(outerBeadInk(Mood.REGENERATING) < outerBeadInk(Mood.CONTENT) / 4)
    }

    @Test
    fun `regenerating drains the colour`() {
        assertTrue(MomoMotion.REGENERATING.saturation < 1f)
        val body = { mood: Mood -> pixel(render(mood), 130, 100) }
        val full = body(Mood.CONTENT)
        val drained = body(Mood.REGENERATING)
        // Less spread between channels is less colour.
        assertTrue(spread(drained) < spread(full))
    }

    @Test
    fun `the clock moves the picture and the loop returns to rest`() {
        val rest = render(Mood.CONTENT, 0f)
        assertNotEquals(rest.toList(), render(Mood.CONTENT, 1.3f).toList())
        // A float period (4.2), a breathe (3.4) and a gill sway (2.9) have no
        // common cycle inside a minute, so this is the one instant the whole
        // frame is known: the frame maths, not the pixels, is what is pinned.
        assertEquals(MomoFrame.rest(Mood.CONTENT), MomoFrame.at(Mood.CONTENT, 0f))
    }

    @Test
    fun `worried fidgets in place and content floats`() {
        val worried = MomoFrame.at(Mood.WORRIED, 0.425f)
        val content = MomoFrame.at(Mood.CONTENT, 2.1f)
        assertTrue(worried.dx != 0f)
        assertEquals(0f, content.dx)
        assertTrue(content.dy < 0f)
        assertEquals(MomoMotion.WORRIED.gillDrop, worried.gillDrop)
        assertEquals(0f, content.gillDrop)
    }

    @Test
    fun `only content-like moods blink`() {
        val mid = 0.97f * MomoMotion.CONTENT.blinkPeriod!!
        assertTrue(MomoFrame.at(Mood.CONTENT, mid).eyeOpen < 1f)
        assertEquals(1f, MomoFrame.at(Mood.THRIVING, mid).eyeOpen)
    }

    private fun render(mood: Mood, seconds: Float = 0f): IntArray {
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawMomo(mood, MomoFrame.at(mood, seconds))
        }
        val map = bitmap.toPixelMap()
        return IntArray(WIDTH * HEIGHT) { i -> argb(map[i % WIDTH, i / WIDTH].value) }
    }

    private fun argb(color: ULong): Int {
        val c = androidx.compose.ui.graphics.Color(color)
        return ((c.alpha * 255).toInt() shl 24) or ((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or
            (c.blue * 255).toInt()
    }

    private fun pixel(pixels: IntArray, x: Int, y: Int) = pixels[y * WIDTH + x]

    private fun spread(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return maxOf(r, g, b) - minOf(r, g, b)
    }

    private fun pinkIn(pixels: IntArray, left: Int, top: Int, right: Int, bottom: Int): Int {
        var n = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val p = pixel(pixels, x, y)
                val r = (p shr 16) and 0xFF
                val b = p and 0xFF
                if ((p ushr 24) > 0 && r > 200 && b > 150) n++
            }
        }
        return n
    }

    private companion object {
        /** The design space, so coordinates in the assertions are the SVG's. */
        const val WIDTH = 260
        const val HEIGHT = 200
        const val MIN_INK = 5_000
    }
}
