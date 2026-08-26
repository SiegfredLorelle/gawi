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
 * Momo draws what docs/ux/momo.md §3 says, measured in pixels. The frame
 * maths — what moves when — is `MomoFrameTest` in `:core:ui`, plain JVM; this
 * class is only what needs a bitmap.
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
    fun `regenerating drains the colour and keeps its lightness`() {
        val body = { mood: Mood -> pixel(render(mood), 130, 100) }
        val full = body(Mood.CONTENT)
        val drained = body(Mood.REGENERATING)
        // Less spread between channels is less colour.
        assertTrue(spread(drained) < spread(full))
        // Drained, not darkened: the canvas's saturate() keeps the encoded
        // lightness, and a grey taken from linear luminance would sit about a
        // fifth lower. Rec. 709 weights on the encoded channels, as saturated()
        // uses, so the check is the definition and not a looser proxy.
        val delta = kotlin.math.abs(lightness(drained) - lightness(full))
        assertTrue("regenerating changed lightness by $delta", delta < 3f)
    }

    @Test
    fun `the clock moves the picture`() {
        assertNotEquals(render(Mood.CONTENT, 0f).toList(), render(Mood.CONTENT, 1.3f).toList())
    }

    /**
     * A mood change is one Momo: at either end the two-mood draw is pixel for
     * pixel the single-mood one, and half way it is neither — one body with
     * both faces on it, not two bodies (docs/ux/momo.md §3).
     */
    @Test
    fun `a mood change meets both ends exactly`() {
        assertArrayEquals(render(Mood.CONTENT), renderBetween(Mood.CONTENT, Mood.WORRIED, 0f))
        assertArrayEquals(render(Mood.WORRIED), renderBetween(Mood.CONTENT, Mood.WORRIED, 1f))
        assertArrayEquals(render(Mood.REGENERATING), renderBetween(Mood.THRIVING, Mood.REGENERATING, 1f))
    }

    @Test
    fun `half way through a mood change the body is drawn once and both faces show`() {
        val mid = renderBetween(Mood.CONTENT, Mood.WORRIED, 0.5f)
        // One body: the belly pixel is the same opaque colour as in a settled
        // frame. Two translucent bodies over each other would read darker or
        // lighter, and the first cut's crossfade drew exactly that.
        assertEquals(pixel(render(Mood.CONTENT), 130, 100), pixel(mid, 130, 100))
        // Both faces: the worried eye's round ink is present but fainter than
        // when settled, and the picture is neither end's.
        val worriedEye = { p: IntArray -> pixel(p, 104, 96) }
        assertNotEquals(render(Mood.CONTENT).toList(), mid.toList())
        assertNotEquals(render(Mood.WORRIED).toList(), mid.toList())
        val settled = inkDistance(worriedEye(render(Mood.WORRIED)))
        val bare = inkDistance(worriedEye(render(Mood.CONTENT)))
        val half = inkDistance(worriedEye(mid))
        assertTrue("settled=$settled half=$half bare=$bare", half in (settled + 1) until bare)
    }

    @Test
    fun `the regrowing gill blends in rather than cutting`() {
        fun outerBeadInk(p: IntArray) = pinkIn(p, left = 196, top = 44, right = 216, bottom = 66)
        val full = outerBeadInk(render(Mood.CONTENT))
        val mid = outerBeadInk(renderBetween(Mood.CONTENT, Mood.REGENERATING, 0.5f))
        // Half faded, the full gill's outer beads are paler and so fewer clear
        // the pink threshold than at rest, but they have not vanished.
        assertTrue("mid=$mid full=$full", mid in 1 until full)
    }

    private fun renderBetween(from: Mood, to: Mood, t: Float, seconds: Float = 0f): IntArray {
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawMomo(from, to, t, MomoFrame.between(MomoFrame.at(from, seconds), MomoFrame.at(to, seconds), t))
        }
        val map = bitmap.toPixelMap()
        return IntArray(WIDTH * HEIGHT) { i -> argb(map[i % WIDTH, i / WIDTH].value) }
    }

    /** How far a pixel is from the eye ink, summed over channels; smaller is more ink. */
    private fun inkDistance(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return kotlin.math.abs(r - 0x3A) + kotlin.math.abs(g - 0x25) + kotlin.math.abs(b - 0x30)
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

    /** Rec. 709 weights on the encoded channels, 0..255 — the grey `saturated()` fades toward. */
    private fun lightness(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

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
