package com.gawi.widget

import android.text.Layout
import android.text.StaticLayout
import android.util.TypedValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every mood line fits the large header's copy slot at the width gate, in the
 * lines the header allows, at the default scale and at 200 % — and the gate
 * itself moves with the scale, so the slot it admits is always one the text
 * fits.
 *
 * Review found the first cut allowed two lines: at the gate the copy has 128dp
 * and the regenerating line needs three of them even at the default scale, so
 * the one sentence carrying an instruction was ellipsised. Measured against the
 * paint under `GraphicsMode.NATIVE` for the reason `StreakSlotTest` gives —
 * LEGACY's `measureText` returns a pixel per character and would pass anything.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeaderCopyTest {

    @Test
    fun `every mood line fits the header at the gate`() = assertFits()

    @Test
    @Config(fontScale = 2f)
    fun `every mood line still fits at a doubled font scale, at the width the gate then needs`() = assertFits()

    /**
     * The gate reserves at the caption's scale, not the numeral's: small text
     * grows at least as much as large under the platform's curve, so probing at
     * 16sp would under-reserve for 12sp ink. Robolectric's curve may be linear,
     * in which case both are 2.0 and the direction still holds.
     */
    @Test
    @Config(fontScale = 2f)
    fun `the caption scale is never below the body scale`() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(BitmapText.textScale(context, BitmapText.CAPTION_SIZE_SP) >= BitmapText.textScale(context))
    }

    /** The gate divides by the text scale, so at 2.0 a 220dp widget is not large and a 440dp one is. */
    @Test
    fun `the width gate scales with the text`() {
        val ready = WidgetContent.Ready(
            todaySnapshot(habits = listOf(todayHabit())).toWidgetState(),
        )
        assertEquals(WidgetBodyContent.Rows::class, ready.body(DpSize(LARGE_MIN_WIDTH.dp, MOMO_MIN_HEIGHT.dp), textScale = 2f)::class)
        assertEquals(
            WidgetBodyContent.Large::class,
            ready.body(DpSize((LARGE_MIN_WIDTH * 2).dp, MOMO_MIN_HEIGHT.dp), textScale = 2f)::class,
        )
    }

    private fun assertFits() {
        val context = RuntimeEnvironment.getApplication()
        val metrics = context.resources.displayMetrics
        val scale = BitmapText.textScale(context, BitmapText.CAPTION_SIZE_SP)
        // The narrowest widget the gate admits at this scale, and the copy room it leaves.
        val copyDp = LARGE_MIN_WIDTH * scale - 2 * WIDGET_PADDING - MOMO_PILL_WIDTH - HEADER_GAP
        val copyPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, copyDp, metrics).toInt()
        val paint = BitmapText.outfitPaint(context, BitmapText.CAPTION_SIZE_SP, BitmapText.OUTFIT_WEIGHT_SEMIBOLD).paint
        for (mood in Mood.entries) {
            val text = context.getString(mood.description())
            val lines = StaticLayout.Builder.obtain(text, 0, text.length, paint, copyPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
                .lineCount
            assertTrue(
                "\"$text\" needs $lines lines in ${copyDp}dp at scale $scale; the header allows $HEADER_COPY_LINES",
                lines <= HEADER_COPY_LINES,
            )
        }
    }
}
