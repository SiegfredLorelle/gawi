package com.gawi.widget

import android.util.TypedValue
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The numeral fits the room reserved for it, at the device default and at 200 %.
 *
 * **Why this needs its own class.** The slots are declared in dp and the numeral
 * is drawn in sp, so the two only agree at a font scale of 1 — and every other
 * render test in this module runs at exactly that scale, which is how a slot that
 * ellipsises the number at 200 % passed everything. Found by review, and the
 * failure it would have shipped is the worst kind this widget has: the numeral
 * *is* the payload, so a truncated one is not a cosmetic clip but a wrong number,
 * and in the compact form the `w` — one of only two signals separating weeks from
 * days at that size — is the first character to go.
 *
 * Measured against the paint rather than asserted off a rendered bitmap, because
 * `BitmapText.render` ellipsises silently: a truncated numeral produces a
 * perfectly valid image, which is precisely why no existing test caught it.
 *
 * **`@GraphicsMode(NATIVE)` is load-bearing and the reason is worth writing
 * down.** Under Robolectric's default graphics, `Paint.measureText` is a stub
 * that returns one pixel per character regardless of text size — so the first
 * version of this file passed at every scale, and passed just as happily with the
 * scaling removed. It measured nothing. NATIVE gives real font metrics: `99w` is
 * 30dp of ink at the default and 54dp at a reported `fontScale` of 2.0 — a 1.8×
 * growth against a scale the platform reports as 2.0, which is the non-linearity
 * `BitmapText.textScale` exists to measure rather than assume.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreakSlotTest {

    @Test
    fun `every compact numeral fits its slot at the device default`() = assertFits(COMPACT_LABELS, COMPACT_NUMERAL_SLOT)

    @Test
    fun `every full numeral fits its slot at the device default`() = assertFits(FULL_LABELS, FULL_NUMERAL_SLOT)

    @Test
    @Config(fontScale = 2f)
    fun `every compact numeral still fits at a doubled font scale`() = assertFits(COMPACT_LABELS, COMPACT_NUMERAL_SLOT)

    /**
     * The full form is only ever chosen when the widget has room for it at the
     * scale in force ([fitsFullForm]), so this is the belt to that braces: even if
     * the gate let it through, the slot itself holds.
     */
    @Test
    @Config(fontScale = 2f)
    fun `every full numeral still fits at a doubled font scale`() = assertFits(FULL_LABELS, FULL_NUMERAL_SLOT)

    private fun assertFits(labels: List<String>, slotDp: Int) {
        val context = RuntimeEnvironment.getApplication()
        val metrics = context.resources.displayMetrics
        // The same scale the widget uses, which is NOT configuration.fontScale:
        // at a reported 2.0 the ink grows 1.75x, because font scaling has been
        // non-linear since Android 14. BitmapText.textScale probes at the drawn
        // size to get that number — an earlier version probed at 1sp, which sits
        // below FontScaleConverter's table and so returned the 2.0 its own KDoc
        // said not to read. Measured here at 1.75 against a 28.0px paint.
        val scale = BitmapText.textScale(context)
        // The same two conversions the widget makes: the slot is scaled by hand,
        // then handed to OutfitText as a Dp and turned into px there.
        val slotPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, slotDp * scale, metrics)
        val paint = BitmapText.outfitPaint(context, weight = BitmapText.OUTFIT_WEIGHT_SEMIBOLD).paint

        for (label in labels) {
            val width = paint.measureText(label)
            assertTrue(
                "\"$label\" needs ${width}px at font scale $scale but the slot is ${slotPx}px, so it ellipsises",
                width <= slotPx,
            )
        }
    }

    private companion object {
        /**
         * The widest string each form can produce. Two digits because a streak in
         * the hundreds is not a size this widget is designed around — and if one
         * happens, the name gives up room rather than the number, which is the
         * whole point of reserving the slot on the numeral's side.
         */
        val COMPACT_LABELS = listOf("12", "99", "3w", "99w", "0")
        val FULL_LABELS = listOf("12 days", "99 days", "3 weeks", "99 weeks", "was 99", "was 99w", "—")
    }
}
