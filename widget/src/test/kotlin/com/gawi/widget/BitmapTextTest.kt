package com.gawi.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import com.gawi.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * The Outfit rasteriser draws what it claims to, measured in pixels.
 *
 * **`GraphicsMode.NATIVE`, and on this class only.** Robolectric's default is
 * `LEGACY`, in which a `Canvas` accepts every call and paints nothing, so a
 * pixel assertion there would pass on a blank bitmap — the empty-tree failure
 * this module's colour test already guards against, in another costume. Native
 * graphics run the real Skia, at the cost of a slower first test; the shared
 * `config/robolectric/robolectric.properties` is left alone so no other module
 * pays that for tests that never draw.
 *
 * **The weight test is the one that matters.** `outfit.ttf` opens at `wght`
 * 100, Thin, and the Compose side once shipped a whole screen at that weight
 * with every test green (Type.kt). Asserting the axis *applied* is cheap and
 * hollow on its own; comparing ink between the default and 400 is what tells a
 * Thin regression from a working render.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapTextTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a name renders visible ink`() {
        val bitmap = render("read")

        assertTrue(bitmap.inkedPixels() > 0)
    }

    @Test
    fun `a longer name renders a wider bitmap`() {
        assertTrue(render("read read read").width > render("read").width)
    }

    @Test
    fun `the weight axis applies, and 400 is heavier than the file's Thin default`() {
        val outfit = BitmapText.outfitPaint(context)
        // A fresh paint on the same resource, never asked for a weight: the file's
        // own default, Thin. Clearing `fontVariationSettings` on the rendered paint
        // would NOT do — the variation is baked into its Typeface instance.
        val thin = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = context.resources.getFont(R.font.outfit)
            textSize = outfit.paint.textSize
            color = Color.WHITE
        }

        assertTrue("wght did not apply to the resource font", outfit.weightAxisApplied)
        val regular = BitmapText.render(SAMPLE, outfit.paint, WIDE)!!.ink()
        val light = BitmapText.render(SAMPLE, thin, WIDE)!!.ink()
        assertTrue("wght 400 ($regular) should carry more ink than the default ($light)", regular > light)
    }

    @Test
    fun `a Latin line is ascent-to-descent tall`() {
        val paint = BitmapText.outfitPaint(context).paint
        val metrics = paint.fontMetricsInt

        assertEquals(metrics.descent - metrics.ascent, BitmapText.lineHeightPx(paint))
        assertEquals(BitmapText.lineHeightPx(paint), BitmapText.render("read", paint, WIDE)!!.height)
        assertEquals(BitmapText.lineHeightPx(paint), BitmapText.render("y", paint, WIDE)!!.height)
    }

    /**
     * The first cut of this test asserted only width, and blamed zero ink on
     * Robolectric's font bundle. Review found the real cause: `ALIGN_NORMAL`
     * puts a right-to-left line at the right edge of the layout, and a layout
     * as wide as the room drawn into a bitmap as wide as the text painted every
     * glyph off the canvas — on a device too. The layout is now built at the
     * text's own width, and this asserts the ink.
     */
    @Test
    fun `right-to-left and mixed-direction names render`() {
        assertTrue(render("לקרוא").inkedPixels() > 0)
        assertTrue(render("اقرأ").inkedPixels() > 0)
        assertTrue(render("read לקרוא 10").inkedPixels() > 0)
    }

    /** A fallback glyph can be taller than Outfit; the bitmap takes the layout's height, not the paint's. */
    @Test
    fun `a name with an emoji is not clipped to Outfit's metrics`() {
        val paint = BitmapText.outfitPaint(context).paint

        val bitmap = BitmapText.render("Gym 💪", paint, WIDE)!!

        assertTrue(bitmap.height >= BitmapText.lineHeightPx(paint))
        assertTrue(bitmap.inkedPixels() > 0)
    }

    @Test
    fun `copy may wrap onto more lines when allowed`() {
        val paint = BitmapText.outfitPaint(context).paint
        val long = "read ".repeat(REPEATS).trim()

        val one = BitmapText.render(long, paint, NARROW, maxLines = 1)!!
        val three = BitmapText.render(long, paint, NARROW, maxLines = 3)!!

        assertTrue(three.height > one.height)
        assertTrue(three.inkedPixels() > one.inkedPixels())
    }

    @Test
    fun `blank text draws nothing`() {
        val paint = BitmapText.outfitPaint(context).paint

        assertNull(BitmapText.render("", paint, WIDE))
        assertNull(BitmapText.render("   ", paint, WIDE))
    }

    @Test
    fun `no room draws nothing`() {
        val paint = BitmapText.outfitPaint(context).paint

        assertNull(BitmapText.render("read", paint, 0))
        assertNull(BitmapText.render("read", paint, -1))
    }

    @Test
    fun `a long name is clipped to the room it has`() {
        val paint = BitmapText.outfitPaint(context).paint
        val long = "read ".repeat(REPEATS).trim()

        val bitmap = BitmapText.render(long, paint, NARROW)

        assertNotNull(bitmap)
        // Ellipsised, so the line is whatever fits plus "…": at most the room,
        // and well short of the unclipped width.
        assertTrue(bitmap!!.width <= NARROW)
        assertTrue(bitmap.width < BitmapText.render(long, paint, WIDE)!!.width)
        assertTrue(bitmap.inkedPixels() > 0)
    }

    @Test
    fun `the paint draws white, at a real size`() {
        val paint = BitmapText.outfitPaint(context).paint

        assertTrue(paint.textSize > 0f)
        assertEquals(Color.WHITE, paint.color)
    }

    private fun render(text: String): Bitmap = BitmapText.render(text, BitmapText.outfitPaint(context).paint, WIDE)!!

    private companion object {
        const val SAMPLE = "read the paper"
        const val WIDE = 2000
        const val NARROW = 300
        const val REPEATS = 40
    }
}

/** Pixels with any alpha at all. */
private fun Bitmap.inkedPixels(): Int = pixels().count { Color.alpha(it) > 0 }

/** Alpha-weighted ink, so a heavier weight measures heavier even where both cover the same pixels. */
private fun Bitmap.ink(): Long = pixels().sumOf { Color.alpha(it).toLong() }

private fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
