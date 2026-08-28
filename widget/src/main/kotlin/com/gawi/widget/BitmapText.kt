package com.gawi.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.createBitmap
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.ContentScale
import com.gawi.core.ui.R
import kotlin.math.ceil

/**
 * One line of text in Outfit, rasterised for a widget.
 *
 * **Why a bitmap.** A Glance tree is `RemoteViews`, and `RemoteViews` inflation
 * resolves `fontFamily` only to the platform's four generic family names — a
 * bundled font resource is dropped silently, in every spelling it can take.
 * Measured on 2026-08-24 (docs/ux/visual-identity.md §2), and the measurement
 * still stands. What a host *will* draw faithfully is a bitmap, so the text is
 * drawn here, in this process, with the typeface the app uses, and shipped as
 * pixels. Decided on 2026-08-25, reversing §2's earlier "not worth it for a
 * checkbox list"; §5 has the trade.
 *
 * **Why white ink and a tint.** The bitmap carries shape only. Colour arrives
 * through `ColorFilter.tint` on the `Image`, from [WidgetPalette], and what that
 * does depends on the host's API level: on 31+ Glance hands the launcher a
 * day/night pair and the launcher picks, so the text follows dark mode as the
 * background does; on 29–30 there is no resource path for an image tint at all,
 * so Glance resolves the provider in this process at translation time.
 *
 * That asymmetry is what shipped a defect, and the numbers are kept because they
 * are the argument for the palette. This paragraph used to claim a night-mode
 * change below 31 left the whole widget stale together. **Measured on API 29 and
 * 30 on 2026-08-28, the same to the decimal on each, it did not**: the
 * background was resource-backed, so the host re-resolved it on its own while
 * this tint and the checkbox glyph kept the last render's baked value. A toggle
 * left the widget illegible rather than stale, until the next render repaired it.
 * Re-measured against the unfixed build on API 29 the same day, as WCAG ratios
 * — the unit the tests use, and the unit every figure here is in, because the
 * first pass reported these on a scale it never named — the name fell to
 * **1.31:1** against its own ground and the checkbox to **1.60:1**. The same run
 * found the glyph below the floor in dark mode even freshly rendered, at 2.91:1
 * checked and 1.60:1 unchecked, which is a second defect the toggle was hiding.
 *
 * Fixed by giving all three colours one kind of provider, so they are translated
 * by the same path and cannot disagree ([WidgetPalette] has the mechanism).
 * Below 31 a toggle now leaves the widget stale *together* and readable, which is
 * what docs/running.md §4 expected of it before it was measured: 16.59:1 for the
 * name in light and 14.82:1 in dark, unchanged across a toggle either way, and
 * on 31 and up the whole widget still follows one within about two seconds.
 *
 * **Why `setFontVariationSettings` and not `Typeface.create`.** `outfit.ttf` is
 * one variable file whose `fvar` default is `wght` 100, Thin — `Type.kt`
 * records the same trap on the Compose side. `Typeface.create(base, 400, false)`
 * picks a style from the family's font *list* and never instances an axis, so
 * it returns Thin with a bold bit at best. `Paint.setFontVariationSettings`
 * goes through `Typeface.createFromTypefaceWithVariation` and instances the
 * axis for real; it also returns whether any axis applied, which [OutfitPaint]
 * surfaces so a test can pin it.
 *
 * **Why `StaticLayout` and not `Canvas.drawText`.** A bitmap has no bidi of its
 * own, so shaping and direction have to be settled before the pixels exist.
 * `StaticLayout` runs the same bidi and shaping a `TextView` would, with
 * `FIRSTSTRONG_LTR` so a Hebrew or Arabic name reads correctly and a Latin one
 * is unaffected; the `Row` around it is mirrored by the host under an RTL
 * locale, so placement needs nothing from here.
 *
 * **What it costs.** `RemoteViews` bitmaps are capped at 1.5 × the screen's
 * pixels × 4 bytes (`AppWidgetManager`'s documented limit). One row at 16sp on
 * a 440dpi phone is roughly 720 × 56 px in ARGB_8888, about 160 KB, against a
 * budget near 14 MB. Two multipliers eat into that: `SizeMode.Exact` composes
 * once per size the host reports and ships them together, and a bitmap grows
 * with the square of the font scale — at 200 % on a two-size launcher the cap is
 * nearer twenty rows than dozens, still far beyond a real list. Width is clamped
 * to what the row can show, never a fixed canvas. Font scale is honoured, because
 * the size is resolved in sp at render time, but only at *render* time: Glance
 * recomposes on a locale change and not on a configuration change, so a scale
 * change shows at the next update (a write, a rollover, or the 30-minute
 * period), which is the latency the widget already accepts for a day rollover.
 */
internal object BitmapText {

    /** The size Glance's `CheckBox` and `Text` drew at, and `bodyLarge` in the app. */
    internal const val TEXT_SIZE_SP = 16f

    /** Outfit's `wght` for body text; the file's own default is 100, Thin. */
    private const val OUTFIT_WEIGHT_NORMAL = 400

    /** White, so a tint of any colour reproduces it exactly. */
    private const val INK = Color.WHITE

    /** A paint carrying Outfit, and whether the weight axis actually took. */
    internal class OutfitPaint(val paint: TextPaint, val weightAxisApplied: Boolean)

    /**
     * Outfit at [OUTFIT_WEIGHT_NORMAL] and [textSizeSp], scaled by the device's
     * density and font scale as they are *now*. `Resources.getFont` is API 26;
     * minSdk is 29, so no compat shim is needed.
     */
    internal fun outfitPaint(context: Context, textSizeSp: Float = TEXT_SIZE_SP): OutfitPaint {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        paint.typeface = context.resources.getFont(R.font.outfit)
        val applied = paint.setFontVariationSettings("'wght' $OUTFIT_WEIGHT_NORMAL")
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
        paint.color = INK
        return OutfitPaint(paint, applied)
    }

    /** Ascent to descent for [paint], with no line-spacing padding: what one line of Outfit is tall. */
    internal fun lineHeightPx(paint: TextPaint): Int {
        val metrics = paint.fontMetricsInt
        return metrics.descent - metrics.ascent
    }

    /**
     * [text] in at most [maxLines] lines, ellipsised at [maxWidthPx], as wide as
     * the text needs and no wider. `null` when there is nothing to draw — blank
     * text or no room — so the caller emits nothing rather than an empty image.
     *
     * The layout is built at the text's own width, not at [maxWidthPx], and that
     * is load-bearing for RTL: `ALIGN_NORMAL` puts a right-to-left line at the
     * *right* edge of the layout, so a layout as wide as the room drawn into a
     * bitmap as wide as the text would paint every glyph off the canvas. Review
     * caught that in the first cut; the height comes from the layout for the same
     * kind of reason — a fallback glyph (an emoji, a script outside Outfit) can
     * be taller than Outfit's own metrics and would be clipped by them.
     *
     * [densityDpi] is the density [paint] and [maxWidthPx] were computed at, and
     * the bitmap is tagged with it. An untagged bitmap carries
     * `DENSITY_DEVICE`, read once from `ro.sf.lcd_density`, while the host's
     * `BitmapDrawable` scales by `targetDensity / bitmap.density` — so under a
     * non-default Display size the text would be rasterised at the current
     * density and then scaled a second time, blurry and past the room it was
     * clamped to. Review caught it; docs/running.md's widget block checks it.
     */
    internal fun render(text: String, paint: TextPaint, maxWidthPx: Int, densityDpi: Int, maxLines: Int = 1): Bitmap? {
        if (text.isBlank() || maxWidthPx <= 0) return null
        val width = ceil(Layout.getDesiredWidth(text, paint)).toInt().coerceIn(1, maxWidthPx)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(width)
            .setIncludePad(false)
            .build()
        val bitmap = createBitmap(width, layout.height.coerceAtLeast(1))
        bitmap.density = densityDpi
        layout.draw(Canvas(bitmap))
        return bitmap
    }
}

/**
 * One Outfit paint for a composition, built once rather than once per row:
 * `setFontVariationSettings` creates a native `Typeface` instance each call.
 */
@Composable
internal fun rememberOutfitPaint(): TextPaint {
    val context = LocalContext.current
    val configuration = context.resources.configuration
    return remember(configuration.fontScale, configuration.densityDpi) { BitmapText.outfitPaint(context).paint }
}

/**
 * [text] drawn in Outfit, in [WidgetPalette]'s ink, no wider than [maxWidth].
 *
 * The bitmap is remembered against everything that would change its pixels:
 * the text, the room it has, the paint, and the density and font scale in
 * force — the paint is a key in its own right rather than a proxy through the
 * other two, so a caller with a different paint cannot get a stale bitmap. Colour
 * is not among them, because the bitmap has none — see [BitmapText].
 *
 * [maxWidth] is floored at [MIN_WIDTH_DP]: Glance's `Exact` size falls back to
 * zero when the host has no info for the id yet, and a widget that draws
 * nothing in that window is worse than one that ellipsises hard.
 *
 * [contentDescription] is `null` by default — decorative — because the row's
 * name belongs on the checkbox, where TalkBack pairs it with the checked state;
 * the copy states pass their text. Blank text emits nothing at all.
 */
@Composable
internal fun OutfitText(
    text: String,
    maxWidth: Dp,
    paint: TextPaint = rememberOutfitPaint(),
    maxLines: Int = 1,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val configuration = context.resources.configuration
    val metrics = context.resources.displayMetrics
    val bitmap = remember(text, maxWidth, maxLines, paint, configuration.fontScale, configuration.densityDpi) {
        val widthDp = maxWidth.value.coerceAtLeast(MIN_WIDTH_DP)
        val maxWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp, metrics)
        BitmapText.render(text, paint, maxWidthPx.toInt(), metrics.densityDpi, maxLines)
    } ?: return
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(WidgetPalette.onSurface),
    )
}

/** The least room a string is ever given, so a size the host has not reported yet still draws something. */
private const val MIN_WIDTH_DP = 48f
