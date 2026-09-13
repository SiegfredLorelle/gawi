package com.gawi.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/**
 * The completion mark beside a habit's name, rasterised as one white mask
 * (docs/ux/widget.md §8).
 *
 * **Why a drawn mark and not a `CheckBox`.** Glance's checkbox lands as a
 * control inside a wrapper `FrameLayout`, and the control is a second
 * accessibility stop of its own at 32dp — under the 48dp floor, and carrying no
 * words the row does not already say. Glance offers no way to take it out of the
 * tree or to grow it, so the exit is to stop emitting a control and draw the
 * state instead: one stop per row, the row's, and a decorative image inside it.
 * What that costs is the compound button's optimism — a `CheckBox` flips itself
 * on tap before the new `RemoteViews` arrives and an `Image` cannot, so the mark
 * turns over when the widget redraws rather than under the finger.
 *
 * **Why a mask and not a VectorDrawable.** The same reason [BandBitmap] is one:
 * a white mask tinted by a `ColorProvider` keeps both schemes on the palette's
 * single translation path, with no resource to resolve in the host's theme and
 * no `-night` variant to forget. It also keeps Lucide out of it — the set this
 * app vendors has no box and no tick, and `scripts/convert-lucide.py` writes to
 * `:core:ui` rather than here.
 *
 * **The geometry is the canvas's own, in its 36-unit space** (canvas page 32,
 * "The 32 dp control"): a 27-unit rounded square at a 6-unit radius inset 4.5,
 * stroked at 3 when outstanding and filled when done, with the tick
 * `11,18.5 → 16,23.5 → 25.5,14` at 3.4 — **re-origined to the mark itself**.
 * The board carries a 3-unit margin on every side for the row it sits in, and a
 * bitmap has no use for it: the `Image` is sized to this bitmap, so a margin
 * baked in here would shrink the drawn mark inside its own box and silently make
 * [TodayWidget]'s `GLYPH_SIZE` a lie. Dropping it leaves the mark spanning all
 * 30 units, so `render(sizePx)` draws a mark exactly `sizePx` across; every
 * number below is the board's minus that margin.
 *
 * **The tick is cut out rather than painted.** One bitmap carries one tint, so a
 * second colour would mean a second mask stacked on the first. Clearing the tick
 * lets the widget's own ground through it, which is the dark-on-teal the board
 * draws and one image instead of two.
 */
internal object GlyphBitmap {

    /**
     * The mark at [sizePx] square, white on transparent, or `null` when there is
     * no room to draw one. [checked] picks the filled mark with its tick over the
     * outline.
     *
     * Tagged with [densityDpi] for the reason [BitmapText.render] gives: an
     * untagged bitmap is scaled by the host against its own density.
     */
    internal fun render(sizePx: Int, densityDpi: Int, checked: Boolean): Bitmap? {
        if (sizePx <= 0) return null
        val bitmap = createBitmap(sizePx, sizePx)
        bitmap.density = densityDpi
        val canvas = Canvas(bitmap)
        val unit = sizePx / CANVAS_UNITS
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        if (checked) {
            // The fill takes the outline's *outer* silhouette rather than its
            // rect, so both states are the same size on the row. A stroke is
            // centred on its path, so the outline reaches half a stroke beyond
            // the rect on every side, and the corner grows by the same to stay
            // concentric with it. Filling the bare rect instead leaves the done
            // mark a stroke-width smaller than the outstanding one.
            val outer = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
            val radius = (CORNER + STROKE / 2f) * unit
            canvas.drawRoundRect(outer, radius, radius, paint)
            canvas.cutTick(unit)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = STROKE * unit
            val edge = (CANVAS_UNITS - INSET) * unit
            canvas.drawRoundRect(RectF(INSET * unit, INSET * unit, edge, edge), CORNER * unit, CORNER * unit, paint)
        }
        return bitmap
    }

    /** The tick, cleared out of the fill so the widget's own ground shows through it. */
    private fun Canvas.cutTick(unit: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = TICK_STROKE * unit
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
        drawLines(
            floatArrayOf(
                TICK_START_X * unit,
                TICK_START_Y * unit,
                TICK_CORNER_X * unit,
                TICK_CORNER_Y * unit,
                TICK_CORNER_X * unit,
                TICK_CORNER_Y * unit,
                TICK_END_X * unit,
                TICK_END_Y * unit,
            ),
            paint,
        )
    }

    /**
     * The board's coordinate space with its 3-unit margin taken off each side,
     * so the mark fills the bitmap: 36 − 3 − 3. The radius and the two stroke
     * widths are unchanged, being lengths rather than positions; every
     * coordinate is the board's less 3.
     */
    private const val CANVAS_UNITS = 30f
    private const val INSET = 1.5f
    private const val CORNER = 6f
    private const val STROKE = 3f
    private const val TICK_STROKE = 3.4f

    /* The tick's three points, as the board draws it: down to the corner, then up. */
    private const val TICK_START_X = 8f
    private const val TICK_START_Y = 15.5f
    private const val TICK_CORNER_X = 13f
    private const val TICK_CORNER_Y = 20.5f
    private const val TICK_END_X = 22.5f
    private const val TICK_END_Y = 11f
}
