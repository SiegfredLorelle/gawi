package com.gawi.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.DisplayMetrics
import com.gawi.core.ui.R
import com.gawi.widget.testsupport.ink
import com.gawi.widget.testsupport.inkedPixels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
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
 * pays that for tests that never draw. One consequence to recognise if it ever
 * bites: native graphics pull Robolectric's native-runtime artifact at test
 * time, outside the version catalogue, so this is the first test here that an
 * offline or air-gapped build would fail to run.
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
        val regular = BitmapText.render(SAMPLE, outfit.paint, WIDE, density)!!.ink()
        val light = BitmapText.render(SAMPLE, thin, WIDE, density)!!.ink()
        assertTrue("wght 400 ($regular) should carry more ink than the default ($light)", regular > light)
    }

    @Test
    fun `a Latin line is ascent-to-descent tall`() {
        val paint = BitmapText.outfitPaint(context).paint
        val metrics = paint.fontMetricsInt

        assertEquals(metrics.descent - metrics.ascent, BitmapText.lineHeightPx(paint))
        assertEquals(BitmapText.lineHeightPx(paint), BitmapText.render("read", paint, WIDE, density)!!.height)
        assertEquals(BitmapText.lineHeightPx(paint), BitmapText.render("y", paint, WIDE, density)!!.height)
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

        val bitmap = BitmapText.render("Gym 💪", paint, WIDE, density)!!

        assertTrue(bitmap.height >= BitmapText.lineHeightPx(paint))
        assertTrue(bitmap.inkedPixels() > 0)
    }

    @Test
    fun `copy may wrap onto more lines when allowed`() {
        val paint = BitmapText.outfitPaint(context).paint
        val long = "read ".repeat(REPEATS).trim()

        val one = BitmapText.render(long, paint, NARROW, density, maxLines = 1)!!
        val three = BitmapText.render(long, paint, NARROW, density, maxLines = 3)!!

        assertTrue(three.height > one.height)
        assertTrue(three.inkedPixels() > one.inkedPixels())
    }

    @Test
    fun `blank text draws nothing`() {
        val paint = BitmapText.outfitPaint(context).paint

        assertNull(BitmapText.render("", paint, WIDE, density))
        assertNull(BitmapText.render("   ", paint, WIDE, density))
    }

    @Test
    fun `no room draws nothing`() {
        val paint = BitmapText.outfitPaint(context).paint

        assertNull(BitmapText.render("read", paint, 0, density))
        assertNull(BitmapText.render("read", paint, -1, density))
    }

    @Test
    fun `a long name is clipped to the room it has`() {
        val paint = BitmapText.outfitPaint(context).paint
        val long = "read ".repeat(REPEATS).trim()

        val bitmap = BitmapText.render(long, paint, NARROW, density)

        assertNotNull(bitmap)
        // Ellipsised, so the line is whatever fits plus "…": at most the room,
        // and well short of the unclipped width.
        assertTrue(bitmap!!.width <= NARROW)
        assertTrue(bitmap.width < BitmapText.render(long, paint, WIDE, density)!!.width)
        assertTrue(bitmap.inkedPixels() > 0)
    }

    /**
     * Tagged with the density it was drawn at, not the device default: the host
     * scales a bitmap by `target / bitmap.density`, so an untagged one is
     * re-scaled whenever Display size is off default. `xhdpi` here so the two
     * differ under Robolectric, whose device default is mdpi.
     */
    @Test
    @Config(qualifiers = "xhdpi")
    fun `the bitmap carries the density it was rendered at`() {
        val bitmap = render("read")

        assertEquals(DisplayMetrics.DENSITY_XHIGH, context.resources.displayMetrics.densityDpi)
        assertEquals(context.resources.displayMetrics.densityDpi, bitmap.density)
        assertNotEquals(DisplayMetrics.DENSITY_DEFAULT, bitmap.density)
    }

    @Test
    fun `the paint draws white, at a real size`() {
        val paint = BitmapText.outfitPaint(context).paint

        assertTrue(paint.textSize > 0f)
        assertEquals(Color.WHITE, paint.color)
    }

    /**
     * The one metric this side shares with the app by *not* setting it. Both
     * draw at 0em since 2026-08-30, when `Type.kt` zeroed Material's positive
     * tracking; before that the widget was already at 0em and the app was not,
     * at the same nominal 16sp. Pinned because the way to reopen that gap is to
     * copy a Material figure into [BitmapText.outfitPaint], which reads like a
     * fix.
     */
    @Test
    fun `the paint tracks at zero, the way the app's scale now does`() {
        assertEquals(0f, BitmapText.outfitPaint(context).paint.letterSpacing, 0f)
    }

    private val density: Int get() = context.resources.displayMetrics.densityDpi

    private fun render(text: String): Bitmap = BitmapText.render(text, BitmapText.outfitPaint(context).paint, WIDE, density)!!

    private companion object {
        const val SAMPLE = "read the paper"
        const val WIDE = 2000
        const val NARROW = 300
        const val REPEATS = 40
    }
}
