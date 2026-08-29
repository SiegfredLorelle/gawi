package com.gawi.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap

/**
 * The woven day band, rasterised as two masks (docs/ux/widget.md §7).
 *
 * **Why masks and not boxes.** The first cut drew one Glance `Box` per habit
 * with a `Spacer` between each — 2N−1 children in one `Row` — and Glance caps a
 * container at ten children: past five habits it truncates the row and logs an
 * error, so the band silently showed five segments for a six-habit day. Caught
 * on review. A bitmap has no child count, so the band is drawn here the way
 * [BitmapText] draws a string, at whatever N the day has.
 *
 * **Why two masks and not one picture.** The two fills are day/night
 * `ColorProvider`s and a bitmap can be tinted with exactly one, so the woven
 * segments and the outstanding ones are drawn as separate white masks and each
 * `Image` carries its own tint. Stacked in one `Box`, they compose into the
 * band, and every colour still resolves through the palette's single
 * translation path — the property [WidgetPalette] exists for.
 *
 * [Geometry] is px, computed by the caller from dp the way [BitmapText.render]
 * takes its width: a gap between segments, each a full-height pill. Every habit
 * is placed from its share of the width — `index · width / n` — so the last one
 * ends at the edge at any N, and the gap gives way before the segment does: it
 * shrinks to nothing rather than the segment going under a pixel. The first cut
 * floored the segment and kept the gap, which let the *advance* outrun the
 * bitmap and clip the tail from about 48 habits — the truncation the masks
 * were written to remove, found on the PR. Tagged with the density for the
 * reason [BitmapText.render] gives.
 */
internal object BandBitmap {

    /** The band's room in px, and the density it was computed at. */
    internal data class Geometry(val widthPx: Int, val heightPx: Int, val gapPx: Float, val densityDpi: Int)

    /**
     * The mask for the segments whose `completed` flag equals [woven], white on
     * transparent; `null` when nothing would be drawn — no rows, no room, or no
     * segment in that state — so the caller emits no image at all for it.
     */
    internal fun render(rows: List<Boolean>, geometry: Geometry, woven: Boolean): Bitmap? {
        val nothingToDraw = rows.none { it == woven }
        val noRoom = geometry.widthPx <= 0 || geometry.heightPx <= 0
        if (nothingToDraw || noRoom) return null
        val bitmap = createBitmap(geometry.widthPx, geometry.heightPx)
        bitmap.density = geometry.densityDpi
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        // Each habit's share of the width, gap included, so the last segment ends
        // at the bitmap's edge whatever N is; the gap gives way before the segment
        // does, down to nothing, and a segment is never under a pixel.
        val pitch = geometry.widthPx.toFloat() / rows.size
        val gap = geometry.gapPx.coerceIn(0f, (pitch - 1f).coerceAtLeast(0f))
        val segment = pitch - gap
        val radius = geometry.heightPx / 2f
        rows.forEachIndexed { index, completed ->
            if (completed != woven) return@forEachIndexed
            val left = index * pitch
            canvas.drawRoundRect(RectF(left, 0f, left + segment, geometry.heightPx.toFloat()), radius, radius, paint)
        }
        return bitmap
    }
}
