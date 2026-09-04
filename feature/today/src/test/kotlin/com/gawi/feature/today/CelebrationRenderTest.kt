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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The celebration draws what docs/ux/momo.md §6 says, in pixels — the
 * `MomoRenderTest` rig: `GraphicsMode.NATIVE`, a `CanvasDrawScope` on a tank-sized
 * bitmap, no composition.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CelebrationRenderTest {

    @Test
    fun `nothing is drawn before it starts or after it ends`() {
        assertEquals(0, painted(render(0f)))
        assertEquals(0, painted(render(1f)))
    }

    /**
     * The glow is a wash over the *whole* tank, so it is measured at the edge
     * where the burst never reaches, and by weight rather than by area: it
     * peaks early and thins towards the end, so an area count would read the
     * same at both.
     *
     * Asserting on the painted count over the whole canvas would prove nothing:
     * lane 0 of the burst is airborne early, so bubble ink alone satisfies
     * "something is painted" and deleting `drawCelebrationGlow` leaves such a
     * case green. Measured.
     */
    @Test
    fun `the glow washes the edges early and thins towards the end`() {
        val edge = IntRect(0, 0, WIDTH / 6, HEIGHT)
        val peak = weightIn(render(0.30f), edge)
        assertTrue(peak > 0)
        assertTrue(weightIn(render(0.9f), edge) < peak)
    }

    @Test
    fun `the bubbles climb the middle, not the edges`() {
        // Half way, bubbles are in the air over the middle third and none at the edges.
        val mid = render(0.5f)
        assertTrue(inkAbove(mid, threshold = 0.3f, left = WIDTH / 3, right = 2 * WIDTH / 3) > 0)
        assertEquals(0, inkAbove(mid, threshold = 0.3f, left = 0, right = WIDTH / 6))
        assertEquals(0, inkAbove(mid, threshold = 0.3f, left = WIDTH - WIDTH / 6, right = WIDTH))
    }

    @Test
    fun `the same progress is the same picture`() {
        assertEquals(render(0.42f).toList(), render(0.42f).toList())
    }

    private fun render(progress: Float): IntArray {
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawCelebration(CelebrationFrame.at(progress), Color.White)
        }
        val map = bitmap.toPixelMap()
        return IntArray(WIDTH * HEIGHT) { i -> (map[i % WIDTH, i / WIDTH].alpha * 255).toInt() }
    }

    private fun painted(alphas: IntArray) = alphas.count { it > 0 }

    /** Total alpha inside [region], which is how heavily it is washed rather than how much of it is touched. */
    private fun weightIn(alphas: IntArray, region: IntRect): Int {
        var total = 0
        for (y in region.top until region.bottom) for (x in region.left until region.right) total += alphas[y * WIDTH + x]
        return total
    }

    /** Pixels above [threshold] alpha in the columns [left, right) — the bubbles, once the glow has faded below it. */
    private fun inkAbove(alphas: IntArray, threshold: Float, left: Int, right: Int): Int {
        var n = 0
        for (y in 0 until HEIGHT) for (x in left until right) if (alphas[y * WIDTH + x] > threshold * 255) n++
        return n
    }

    private companion object {
        const val WIDTH = 362
        const val HEIGHT = 250
    }
}
