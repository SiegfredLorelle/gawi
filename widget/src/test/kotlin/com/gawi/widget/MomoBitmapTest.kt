package com.gawi.widget

import android.util.DisplayMetrics
import com.gawi.core.domain.mascot.Mood
import com.gawi.widget.testsupport.inkedPixels
import com.gawi.widget.testsupport.pixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Momo's still frame is drawn, sized and tagged as claimed — in pixels.
 *
 * `GraphicsMode.NATIVE` for the reason `BitmapTextTest` gives: in Robolectric's
 * default a canvas accepts every call and paints nothing, so an ink count would
 * pass on a blank bitmap. The character's own rendering — that each mood is a
 * different face, that the regrowing gill is shorter — is `MomoRenderTest`'s
 * business in `:feature:today`; this asks only whether the widget's route to the
 * same `drawMomo` arrives with a picture, at the size it was asked for.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MomoBitmapTest {

    @Test
    fun `the resting frame renders ink`() {
        Mood.entries.forEach { mood ->
            assertTrue("$mood drew nothing", MomoBitmap.render(mood, HEIGHT, DisplayMetrics.DENSITY_DEFAULT)!!.inkedPixels() > MIN_INK)
        }
    }

    @Test
    fun `the bitmap is the height asked for, and the character's width`() {
        val bitmap = MomoBitmap.render(Mood.CONTENT, HEIGHT, DisplayMetrics.DENSITY_DEFAULT)!!

        assertEquals(HEIGHT, bitmap.height)
        // 260 × 200 is the design space; 1.3 × 200 is 260 exactly.
        assertEquals(260, bitmap.width)
    }

    /** The lesson BitmapText learnt: an untagged bitmap is drawn at its pixel size on every density. */
    @Test
    fun `the bitmap carries the density it was drawn for`() {
        // Rendered at a density the display is not at, so a bitmap that merely
        // inherited the default could not pass this.
        assertEquals(DisplayMetrics.DENSITY_XHIGH, MomoBitmap.render(Mood.CONTENT, HEIGHT, DisplayMetrics.DENSITY_XHIGH)!!.density)
    }

    /** momo.md §5: every mood must read at rest, or the motion was carrying meaning the drawing should. */
    @Test
    fun `the four resting faces differ pairwise`() {
        val faces = Mood.entries.associateWith { MomoBitmap.render(it, HEIGHT, DisplayMetrics.DENSITY_DEFAULT)!!.pixels() }
        Mood.entries.forEach { a ->
            Mood.entries.filter { it > a }.forEach { b ->
                assertFalse("$a and $b rest on the same picture", faces.getValue(a).contentEquals(faces.getValue(b)))
            }
        }
    }

    @Test
    fun `no room draws nothing rather than throwing`() {
        assertNull(MomoBitmap.render(Mood.CONTENT, 0, DisplayMetrics.DENSITY_DEFAULT))
    }

    private companion object {
        const val HEIGHT = 200

        /** Well under the body alone; a threshold, not a measurement, so a stroke width change cannot fail it. */
        const val MIN_INK = 5_000
    }
}
