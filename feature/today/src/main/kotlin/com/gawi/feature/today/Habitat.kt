// Every number here is a coordinate, a period or an amplitude of the tank life
// as drawn and tuned on the Gawi Redesign canvas (docs/ux/momo.md §4): the four
// weeds and four bubbles from its Momo motion boards, and the per-mood tempo
// from its Habitat & motion page. They are the drawing, not values standing in
// for one — the reason Momo.kt gives. Narrowed to this file, never widened in
// config/detekt/detekt.yml, for the reason Color.kt gives.
@file:Suppress("MagicNumber")

package com.gawi.feature.today

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.mascot.Mood
import kotlin.math.PI
import kotlin.math.cos

/**
 * How the tank life keeps time with a mood (docs/ux/momo.md §4).
 *
 * One [tempo] per mood rather than a period per part: it scales the canvas's
 * 5.2 s weed sway and its 7.4–10 s bubble rises together, so the habitat
 * cannot drift out of step with itself. The numbers are the Habitat & motion
 * page's defaults.
 */
internal data class HabitatMotion(
    /** Multiplies every period; 1 is the Momo motion boards' own timing. */
    val tempo: Float,
    /** Half the weed sway, degrees. */
    val sway: Float,
    /** How far each weed leans from upright while drained, degrees. */
    val droop: Float,
    /** Whether bubbles rise at all. */
    val bubbles: Boolean,
) {
    companion object {
        val THRIVING = HabitatMotion(0.6f, 5f, 0f, true)
        val CONTENT = HabitatMotion(1f, 5f, 0f, true)
        val WORRIED = HabitatMotion(1.3f, 3f, 0f, true)
        val REGENERATING = HabitatMotion(1.7f, 1.5f, 22f, false)

        fun of(mood: Mood): HabitatMotion = when (mood) {
            Mood.THRIVING -> THRIVING
            Mood.CONTENT -> CONTENT
            Mood.WORRIED -> WORRIED
            Mood.REGENERATING -> REGENERATING
        }
    }
}

/** One bubble at one instant: where it is on its lane, 0 at the floor and 1 gone. */
internal data class Bubble(val lane: Int, val progress: Float, val alpha: Float)

/**
 * Where every moving part of the tank life is at one instant — a pure
 * function of the mood and the clock, like [com.gawi.core.ui.component.MomoFrame],
 * for the same reason: a test can pin it without a composition.
 */
internal data class HabitatFrame(
    /** The four weeds' sway, degrees, left pair then right pair. */
    val weeds: List<Float>,
    /** The lean of every weed, degrees; left weeds lean left, right weeds right. */
    val droop: Float,
    val bubbles: List<Bubble>,
    /** 1 while regenerating: the weeds grey and the bubbles stop. */
    val drained: Float,
) {
    companion object {
        /** The still frame, what a viewer with animations off sees. */
        fun rest(mood: Mood): HabitatFrame = at(mood, 0f)

        /** The frame [seconds] into the loop. Every curve is the canvas's CSS keyframe pair. */
        fun at(mood: Mood, seconds: Float): HabitatFrame {
            val m = HabitatMotion.of(mood)
            val period = WEED_PERIOD * m.tempo
            return HabitatFrame(
                weeds = WEED_DELAYS.map { delay -> -m.sway * cos(TAU * (seconds + delay * m.tempo) / period) },
                droop = m.droop,
                bubbles = if (m.bubbles) {
                    Lanes.mapIndexed { lane, l ->
                        val progress = ((seconds + l.delay * m.tempo) / (l.rise * m.tempo)) % 1f
                        Bubble(lane, progress, l.alpha * riseAlpha(progress))
                    }
                } else {
                    emptyList()
                },
                drained = if (mood == Mood.REGENERATING) 1f else 0f,
            )
        }

        /**
         * The frame [t] of the way from [from] to [to]: sway, lean and drain
         * interpolate, while the bubbles — whose lanes run at different tempos
         * and cannot be averaged — crossfade, the way a face does.
         */
        fun between(from: HabitatFrame, to: HabitatFrame, t: Float): HabitatFrame {
            fun lerp(a: Float, b: Float) = when {
                t <= 0f -> a
                t >= 1f -> b
                else -> a + (b - a) * t
            }
            return HabitatFrame(
                weeds = from.weeds.zip(to.weeds) { a, b -> lerp(a, b) },
                droop = lerp(from.droop, to.droop),
                bubbles = from.bubbles.mapNotNull { b -> fade(b, 1f - t) } + to.bubbles.mapNotNull { b -> fade(b, t) },
                drained = lerp(from.drained, to.drained),
            )
        }

        private fun fade(b: Bubble, by: Float): Bubble? = if (by <= 0f) null else b.copy(alpha = b.alpha * by)

        /** The canvas's `rise` keyframes: in by 12 %, easing to 40 % opacity at 80 %, gone at 100 %. */
        private fun riseAlpha(p: Float): Float = when {
            p < 0.12f -> 0.55f * (p / 0.12f)
            p < 0.80f -> 0.55f - 0.15f * ((p - 0.12f) / 0.68f)
            else -> 0.40f * (1f - (p - 0.80f) / 0.20f)
        }

        /** The Momo motion boards' `weedSway`, at tempo 1. */
        const val WEED_PERIOD = 5.2f

        private const val TAU = (2 * PI).toFloat()

        // The CSS animation-delays, negative there; as offsets added to the
        // clock they come out the same way round.
        private val WEED_DELAYS = listOf(0f, 1.6f, 2.2f, 3.1f)
    }
}

/** The colours the habitat is drawn in — theme roles resolved by the caller, so the drain is a role swap. */
internal data class HabitatColours(
    /** Weeds while the tank is full; `primary`. */
    val weed: Color,
    /** Weeds while it is drained; `outline`. */
    val weedDrained: Color,
    /** Bubbles: a highlight, the same in both themes. */
    val bubble: Color,
)

/**
 * Draws the tank life at [frame] over this scope, in dp: four weeds rooted at
 * the floor, two a side, and up to four bubbles rising on fixed lanes. The
 * geometry is the Momo motion boards' `tank-life` SVG at 1 px = 1 dp; the
 * left pair is placed from the left edge and the right pair from the right, so
 * the tank keeps its shape at any width.
 */
internal fun DrawScope.drawHabitat(frame: HabitatFrame, colours: HabitatColours) {
    val dp = 1.dp.toPx()
    val floor = size.height - FLOOR_INSET * dp
    val weedColour = lerp(colours.weed, colours.weedDrained, frame.drained)
    Weeds.forEachIndexed { index, weed ->
        val x = if (weed.fromLeft) weed.offset * dp else size.width - weed.offset * dp
        val lean = if (index < 2) -frame.droop else frame.droop
        withTransform({
            translate(x, floor)
            rotate(lean + frame.weeds[index], pivot = Offset.Zero)
            scale(dp, dp, pivot = Offset.Zero)
        }) {
            drawPath(weed.stem, weedColour, alpha = WEED_ALPHA, style = Stroke(5f, cap = StrokeCap.Round))
            drawPath(weed.leaf, weedColour, alpha = WEED_ALPHA, style = Stroke(3.5f, cap = StrokeCap.Round))
        }
    }
    frame.bubbles.forEach { b ->
        if (b.alpha <= 0f) return@forEach
        val lane = Lanes[b.lane]
        val x = if (lane.fromLeft) lane.offset * dp else size.width - lane.offset * dp
        val y = size.height - (BUBBLE_START + b.progress * BUBBLE_RISE) * dp
        val radius = lane.size / 2f * (0.6f + 0.45f * b.progress) * dp
        drawCircle(colours.bubble, radius, Offset(x, y), alpha = b.alpha)
    }
}

private class Weed(val fromLeft: Boolean, val offset: Float, val stem: Path, val leaf: Path)

/** The four fronds, left pair then right pair, as the canvas drew them. */
private val Weeds = listOf(
    Weed(
        fromLeft = true,
        offset = 26f,
        stem = Path().apply {
            moveTo(0f, 0f)
            cubicTo(-7f, -22f, 7f, -34f, 0f, -52f)
        },
        leaf = Path().apply {
            moveTo(0f, -26f)
            relativeCubicTo(-9f, -6f, -11f, -16f, -6f, -22f)
        },
    ),
    Weed(
        fromLeft = true,
        offset = 58f,
        stem = Path().apply {
            moveTo(0f, 0f)
            cubicTo(-7f, -15f, 7f, -24f, 0f, -36f)
        },
        leaf = Path().apply {
            moveTo(0f, -18f)
            relativeCubicTo(-9f, -4f, -11f, -11f, -6f, -15f)
        },
    ),
    Weed(
        fromLeft = false,
        offset = 62f,
        stem = Path().apply {
            moveTo(0f, 0f)
            cubicTo(-7f, -13f, 7f, -20f, 0f, -30f)
        },
        leaf = Path().apply {
            moveTo(0f, -15f)
            relativeCubicTo(-9f, -4f, -11f, -9f, -6f, -13f)
        },
    ),
    Weed(
        fromLeft = false,
        offset = 32f,
        stem = Path().apply {
            moveTo(0f, 0f)
            cubicTo(-7f, -19f, 7f, -30f, 0f, -46f)
        },
        leaf = Path().apply {
            moveTo(0f, -23f)
            relativeCubicTo(-9f, -6f, -11f, -14f, -6f, -19f)
        },
    ),
)

private class Lane(val fromLeft: Boolean, val offset: Float, val size: Float, val alpha: Float, val rise: Float, val delay: Float)

/** The four bubbles' lanes: where they start, how big, how bright, how long a rise takes, and their phase. */
private val Lanes = listOf(
    Lane(fromLeft = true, offset = 41.5f, size = 7f, alpha = 0.50f, rise = 7.4f, delay = 0f),
    Lane(fromLeft = true, offset = 74.25f, size = 4.5f, alpha = 0.42f, rise = 9.0f, delay = 2.2f),
    Lane(fromLeft = false, offset = 59f, size = 6f, alpha = 0.46f, rise = 8.2f, delay = 1.1f),
    Lane(fromLeft = false, offset = 24f, size = 4f, alpha = 0.38f, rise = 10.0f, delay = 3.0f),
)

/** The weeds' roots sit this far above the tank floor, as the canvas's SVG does. */
private const val FLOOR_INSET = 4f
private const val WEED_ALPHA = 0.55f

/** Bubbles start this far above the floor and rise this far, dp — the canvas's `bottom:12px` and `translateY(-150px)`. */
private const val BUBBLE_START = 12f
private const val BUBBLE_RISE = 150f
