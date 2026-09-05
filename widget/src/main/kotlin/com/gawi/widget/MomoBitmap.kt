package com.gawi.widget

import android.graphics.Bitmap
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.ContentScale
import androidx.glance.layout.height
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.ui.component.MomoDesignSize
import com.gawi.core.ui.component.MomoFrame
import com.gawi.core.ui.component.drawMomo
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Momo's resting frame, rasterised for a widget.
 *
 * **Why a bitmap.** The same reason as [BitmapText]: a Glance tree is
 * `RemoteViews`, and a host can no more run a Compose `Canvas` than load a font.
 * What it draws faithfully is pixels, so the character is drawn here, in this
 * process, by the same [drawMomo] the Today screen animates, at the frame it
 * rests on — [MomoFrame.rest], which is also what a viewer with animations off
 * sees (docs/ux/momo.md §4–§5). No second copy of the geometry, and no asset
 * per mood, which is what visual-identity §7.4's "four static drawables" would
 * have cost.
 *
 * **No tint.** Unlike a line of text, Momo carries her own palette, the same in
 * both themes (momo.md §2), so the `Image` takes no `ColorFilter`. That also
 * means a test matching tinted images for text does not see her — deliberate,
 * and `WidgetTextColourTest` says so.
 *
 * **What it costs.** One bitmap per composition, [HEIGHT_DP] tall and 1.3× as
 * wide: at 440dpi about 198 × 258 px in ARGB_8888, near 200 KB, against the
 * ~14 MB `RemoteViews` budget [BitmapText] documents. `SizeMode.Exact` ships one
 * per size the host reports, so two sizes is two of them. The height is a
 * constant, never derived from the widget's size, so the cost cannot grow with
 * a host's idea of "large". `Density(1f)` is right for the draw: [drawMomo]
 * scales itself to the pixel box it is given and uses no dp of its own.
 */
internal object MomoBitmap {

    /** Momo's height on the widget, dp. Wide enough that the face reads; short enough to leave the rows their room. */
    internal const val HEIGHT_DP = 72f

    /**
     * Her height inside the large body's pill, dp — the 66×52 pill the canvas
     * drew, less a little air. A second constant rather than a value derived
     * from the pill or the widget, for the reason [HEIGHT_DP] is one: the
     * bitmap's cost must not follow a host's idea of "large".
     */
    internal const val PILL_HEIGHT_DP = 48f

    /** Width over height, from the character's design space, so nothing is squashed. */
    internal val ASPECT: Float = MomoDesignSize.width / MomoDesignSize.height

    /**
     * [mood]'s resting frame, [heightPx] tall, tagged [densityDpi] so the host
     * scales it like any other resource — the same lesson [BitmapText.render]
     * records. Null when there is no room, so the caller draws nothing rather
     * than a zero-sized bitmap, which `createBitmap` throws on.
     */
    internal fun render(mood: Mood, heightPx: Int, densityDpi: Int): Bitmap? {
        if (heightPx <= 0) return null
        val widthPx = ceil(heightPx * ASPECT).toInt()
        val image = ImageBitmap(widthPx, heightPx)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(widthPx.toFloat(), heightPx.toFloat())) {
            drawMomo(mood, MomoFrame.rest(mood))
        }
        return image.asAndroidBitmap().also { it.density = densityDpi }
    }
}

/**
 * Momo at rest, [heightDp] tall — [MomoBitmap.HEIGHT_DP] above the rows,
 * [MomoBitmap.PILL_HEIGHT_DP] inside the large body's pill — in [mood]'s face.
 *
 * Remembered against the mood, the height and the density — everything that
 * changes the pixels. Not the font scale: this is not text, and the character has a size
 * rather than growing with the type (momo.md §4).
 *
 * [contentDescription] is the caller's call, because it depends on what sits
 * beside the face: above the rows there is no copy line, so the face is the one
 * place the mood can be read and it is described; beside the no-habits copy
 * the copy is read once and the face is decorative (docs/ux/momo.md §4).
 */
@Composable
internal fun MomoImage(mood: Mood, contentDescription: String?, heightDp: Float = MomoBitmap.HEIGHT_DP) {
    val metrics = LocalContext.current.resources.displayMetrics
    val bitmap = remember(mood, heightDp, metrics.densityDpi) {
        val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightDp, metrics)
        MomoBitmap.render(mood, heightPx.roundToInt(), metrics.densityDpi)
    } ?: return
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = GlanceModifier.height(heightDp.dp),
        // No colorFilter: see MomoBitmap.
    )
}
