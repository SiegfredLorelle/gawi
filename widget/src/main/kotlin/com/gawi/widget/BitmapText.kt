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
import androidx.glance.unit.ColorProvider
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
 * pixels. §5 concluded that this was "not worth it for a checkbox list"; §2
 * records that conclusion reversed, and this file is the result.
 *
 * **Why white ink and a tint.** The bitmap carries shape only. Colour arrives
 * through `ColorFilter.tint` on the `Image`, from [WidgetPalette], and what that
 * does depends on the host's API level: on 31+ Glance hands the launcher a
 * day/night pair and the launcher picks, so the text follows dark mode as the
 * background does; on 29–30 there is no resource path for an image tint at all,
 * so Glance resolves the provider in this process at translation time.
 *
 * That asymmetry is what shipped a defect, and the numbers are kept because
 * they are the argument for the palette. **Mix the two provider kinds and,
 * below 31, a night-mode change does not leave the whole widget stale
 * together** — measured on API 29 and 30 on 2026-08-28, the same to the decimal
 * on each. A resource-backed background is re-resolved by the host on its own
 * while a baked tint and checkbox glyph keep the last render's value, so a
 * toggle leaves the widget illegible rather than stale until the next render
 * repairs it. Against that build on API 29, as WCAG ratios — the unit the tests
 * use, and the unit every figure here is in — the name falls to **1.31:1**
 * against its own ground and the checkbox to **1.60:1**. The same run found the
 * glyph below the floor in dark mode even freshly rendered, at 2.91:1 checked
 * and 1.60:1 unchecked, a second defect the toggle hides.
 *
 * All three colours take one kind of provider, so they are translated by the
 * same path and cannot disagree ([WidgetPalette] has the mechanism). Below 31 a
 * toggle leaves the widget stale *together* and readable, which is what
 * docs/running.md §4 expects of it: 16.59:1 for the name in light and 14.82:1
 * in dark, unchanged across a toggle either way, and on 31 and up the whole
 * widget follows one within about two seconds.
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
    internal const val OUTFIT_WEIGHT_NORMAL = 400

    /**
     * Outfit's `wght` for a number that is the point of its row — the streak
     * widget's numerals, which the canvas drew at 600 against names at 400.
     * A fourth signal separating a streak from the name beside it, and the only
     * one that survives greyscale *and* a narrow slot.
     */
    internal const val OUTFIT_WEIGHT_SEMIBOLD = 600

    /** A caption: the streak widget's "as of" line, drawn smaller than its rows. */
    internal const val CAPTION_SIZE_SP = 12f

    /** White, so a tint of any colour reproduces it exactly. */
    private const val INK = Color.WHITE

    /** A paint carrying Outfit, and whether the weight axis actually took. */
    internal class OutfitPaint(val paint: TextPaint, val weightAxisApplied: Boolean)

    /**
     * Outfit at [weight] and [textSizeSp], scaled by the device's density and
     * font scale as they are *now*. `Resources.getFont` is API 26; minSdk is 29,
     * so no compat shim is needed.
     *
     * **[textSizeSp] is an sp size and therefore grows with the user's font
     * scale**, which is the whole point and also a trap for any caller that
     * reserves room for the result in dp: the ink scales and the dp does not.
     * [StreakWidget] scales its slots to match.
     *
     * **`letterSpacing` is left at `Paint`'s 0em default, and that is the app's
     * value too** — `Type.kt` zeroes Material's positive tracking, and its KDoc
     * has the measurement. Setting a Material figure here would make the two
     * surfaces differ at the same nominal 16sp, so the
     * omission is deliberate and `BitmapTextTest` pins it.
     */
    internal fun outfitPaint(context: Context, textSizeSp: Float = TEXT_SIZE_SP, weight: Int = OUTFIT_WEIGHT_NORMAL): OutfitPaint {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        paint.typeface = context.resources.getFont(R.font.outfit)
        val applied = paint.setFontVariationSettings("'wght' $weight")
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics)
        paint.color = INK
        return OutfitPaint(paint, applied)
    }

    /**
     * How much wider sp text is than the same number of dp, on this device, now —
     * measured at [TEXT_SIZE_SP], the size the widget actually draws.
     *
     * **Not `configuration.fontScale`, and that difference is measurable.** Since
     * Android 14 font scaling is non-linear: at a reported `fontScale` of 2.0,
     * 16sp resolves to 28px rather than 32, so the ink grows 1.75× while the
     * setting says 2. Anything reserving dp room for sp ink has to scale by what
     * the text actually does.
     *
     * **Probed at [TEXT_SIZE_SP] rather than at 1sp, and the difference is the
     * whole point.** `FontScaleConverter`'s curve is defined per text size and
     * its table starts at 8sp; below that it interpolates from the origin and is
     * effectively linear, so a 1sp probe returns ≈`fontScale`, exactly the
     * value ruled out above. Measured under Robolectric at
     * `fontScale = 2.0`: the 1sp probe gives 2.0, and this gives 1.75 against a
     * paint of 28.0px and ink growth of 1.74–1.8× for the widest numerals.
     *
     * [textSizeSp] is the size the caller actually draws, because the ratio is
     * a function of it: Android 14's curve grows small text more than large, so
     * 12sp resolves nearer 2.0× where 16sp resolves at 1.75. One size cannot
     * serve every caller: the large Today body's width gate and the Momo
     * widget's face both reserve for [CAPTION_SIZE_SP] ink and must pass it, or
     * they under-reserve by the gap between the two curves.
     *
     * Floored at 1: shrinking the text does not make a widget's cells wider in
     * any way the user asked for.
     */
    internal fun textScale(context: Context, textSizeSp: Float = TEXT_SIZE_SP): Float {
        val metrics = context.resources.displayMetrics
        val inkPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, metrics)
        return (inkPx / (textSizeSp * metrics.density)).coerceAtLeast(1f)
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
 * How a rasterised string is drawn: which face, and which ink.
 *
 * The two travel together because a caller almost never varies one without
 * meaning to hold the other fixed — the streak rows share one [paint] across
 * every row and vary only [tint] by streak unit, which is the case that made a
 * pair worth naming.
 */
internal data class OutfitInk(val paint: TextPaint, val tint: ColorProvider)

/**
 * One Outfit paint for a composition, built once rather than once per row:
 * `setFontVariationSettings` creates a native `Typeface` instance each call.
 */
@Composable
internal fun rememberOutfitPaint(textSizeSp: Float = BitmapText.TEXT_SIZE_SP, weight: Int = BitmapText.OUTFIT_WEIGHT_NORMAL): TextPaint {
    val context = LocalContext.current
    val configuration = context.resources.configuration
    return remember(configuration.fontScale, configuration.densityDpi, textSizeSp, weight) {
        BitmapText.outfitPaint(context, textSizeSp, weight).paint
    }
}

/** [rememberOutfitPaint] with an ink, defaulting to the widget's body colour at body size. */
@Composable
internal fun rememberOutfitInk(
    tint: ColorProvider = WidgetPalette.onSurface,
    textSizeSp: Float = BitmapText.TEXT_SIZE_SP,
    weight: Int = BitmapText.OUTFIT_WEIGHT_NORMAL,
): OutfitInk = OutfitInk(paint = rememberOutfitPaint(textSizeSp, weight), tint = tint)

/**
 * [text] drawn in Outfit, in [ink], no wider than [maxWidth].
 *
 * The bitmap is remembered against everything that would change its pixels:
 * the text, the room it has, the paint, and the density and font scale in
 * force — the paint is a key in its own right rather than a proxy through the
 * other two, so a caller with a different paint cannot get a stale bitmap. Colour
 * is not among them, because the bitmap has none — see [BitmapText]. That is also
 * why the tint half of [ink] is free, and why only its paint is a remember key:
 * two rows with the same string in different roles share one bitmap and differ
 * only in the `ColorFilter`, so the streak widget's day and week numerals cost no
 * extra rasterisation.
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
    ink: OutfitInk = rememberOutfitInk(),
    maxLines: Int = 1,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val configuration = context.resources.configuration
    val metrics = context.resources.displayMetrics
    val bitmap = remember(text, maxWidth, maxLines, ink.paint, configuration.fontScale, configuration.densityDpi) {
        val widthDp = maxWidth.value.coerceAtLeast(MIN_WIDTH_DP)
        val maxWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp, metrics)
        BitmapText.render(text, ink.paint, maxWidthPx.toInt(), metrics.densityDpi, maxLines)
    } ?: return
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(ink.tint),
    )
}

/** The least room a string is ever given, so a size the host has not reported yet still draws something. */
private const val MIN_WIDTH_DP = 48f
