package com.gawi.feature.today

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
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

    @Test
    fun `the glow washes the whole tank at its peak`() {
        assertEquals(WIDTH * HEIGHT, painted(render(0.16f)))
    }

    @Test
    fun `the ring reaches past the burst's middle third once it has opened`() {
        // Before the ring opens, only the burst: nothing near the side edges,
        // as with the day's celebration.
        val early = render(0.28f)
        assertEquals(0, inkAbove(early, threshold = 0.3f, left = 0, right = 40))
        // Once open and wide, the stars sit around the middle at ~90 dp out —
        // ink well outside the burst's lanes, to both sides.
        val open = render(0.72f)
        assertTrue(inkAbove(open, threshold = 0.3f, left = 0, right = WIDTH / 4) > 20)
        assertTrue(inkAbove(open, threshold = 0.3f, left = 3 * WIDTH / 4, right = WIDTH) > 20)
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
