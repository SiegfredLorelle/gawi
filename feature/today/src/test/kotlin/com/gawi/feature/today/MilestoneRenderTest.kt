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
 * The milestone draws what docs/ux/momo.md §6 says, in pixels — the
 * `CelebrationRenderTest` rig. What tells it apart from the day-complete
 * sequence is the ring: by the second half there is ink far from the middle
 * third, where the day's burst never reaches.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MilestoneRenderTest {

    @Test
    fun `nothing is drawn before it starts or after it ends`() {
        assertEquals(0, painted(render(0f)))
        assertEquals(0, painted(render(1f)))
    }

    /**
     * The glow is a wash over the *whole* tank, measured at the edge sixth and
     * by weight rather than by area — the same shape as
     * `CelebrationRenderTest`, and for the same reason: it peaks early and
     * thins rather than stopping, so an area count reads alike at both ends.
     *
     * The edge sixth holds nothing else at any progress. The burst's lanes span
     * x 81 to 281 of 362 (`MilestoneLanes`), and the ring reaches at most 110dp
     * from a centre at x 181, so neither enters it — which is why the case
     * below can use the same region to say "no ring here yet".
     *
     * Measured: without this, deleting `drawMilestoneGlow` left the whole file
     * green. Every other case here reads at `threshold = 0.3f`, and the glow
     * peaks at alpha 76 of 255, so it sits under the bar everywhere they look.
     */
    @Test
    fun `the glow washes the edges early and thins towards the end`() {
        val edge = IntRect(0, 0, WIDTH / 6, HEIGHT)
        val peak = weightIn(render(0.16f), edge)
        assertTrue(peak > 0)
        assertTrue(weightIn(render(0.9f), edge) < peak)
    }

    @Test
    fun `the ring reaches past the burst's middle third once it has opened`() {
        // Before the ring opens, only the burst: nothing near the side edges,
        // as with the day's celebration.
        val early = render(0.28f)
        assertEquals(0, inkAbove(early, threshold = 0.3f, left = 0, right = WIDTH / 6))
        assertEquals(0, inkAbove(early, threshold = 0.3f, left = WIDTH - WIDTH / 6, right = WIDTH))
        // Once open and wide, the stars sit around the middle at ~90 dp out —
        // ink well outside the burst's lanes, to both sides.
        val open = render(0.72f)
        assertTrue(inkAbove(open, threshold = 0.3f, left = 0, right = WIDTH / 4) > 0)
        assertTrue(inkAbove(open, threshold = 0.3f, left = 3 * WIDTH / 4, right = WIDTH) > 0)
    }

    @Test
    fun `the same progress is the same picture`() {
        assertEquals(render(0.42f).toList(), render(0.42f).toList())
    }

    private fun render(progress: Float): IntArray {
        val bitmap = ImageBitmap(WIDTH, HEIGHT)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(WIDTH.toFloat(), HEIGHT.toFloat())) {
            drawMilestone(MilestoneFrame.at(progress), Color.White)
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
