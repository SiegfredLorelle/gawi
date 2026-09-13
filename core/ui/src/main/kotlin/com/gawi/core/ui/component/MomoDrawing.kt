// Every number here is a coordinate, radius or colour of the character as
// drawn on the Gawi Redesign canvas (docs/ux/momo.md §2), transcribed from its
// SVG. They are the drawing, not values standing in for one; see Momo.kt.
@file:Suppress("MagicNumber")

package com.gawi.core.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.streak.Streaks

/** The design space the geometry below is written in. */
val MomoDesignSize: Size = Size(260f, 200f)

/**
 * Draws Momo in [mood] at [frame], scaled to fit this scope's size and centred.
 *
 * Draw order is the canvas's: gills behind, then body, then face, then the
 * mood's extras — so a gill swinging forward never crosses an eye.
 *
 * Public, with [MomoFrame] and [MomoMotion], for `:widget`'s still frame
 * (docs/ux/momo.md §4), which is another module; `:feature:today` calls [Momo]
 * and none of these. Their tests live in `:core:ui` for the same reason —
 * `MomoFrameTest` for the maths; the pixels need Robolectric, which this module
 * does not take, so `MomoRenderTest` in `:feature:today` draws them.
 */
fun DrawScope.drawMomo(mood: Mood, frame: MomoFrame, spare: Int = Streaks.MAX_SPARE) = drawMomo(mood, mood, 1f, frame, spare)

/**
 * Draws Momo part-way through a mood change: [progress] of the way from [from]
 * to [to], with [frame] already interpolated by [MomoFrame.between].
 *
 * The body, tail and five ordinary gills are drawn once, from [frame]. What
 * crossfades is what differs between two moods' drawings — the eyes and mouth,
 * the sparkles, the sweat bead, and the right upper gill, which is short while
 * it regrows — so [from]'s face fades out as [to]'s fades in over one body.
 * At 0 and 1, and whenever the two moods are the same, this is exactly
 * [drawMomo] of one mood: the single-mood overload is this call.
 */
fun DrawScope.drawMomo(from: Mood, to: Mood, progress: Float, frame: MomoFrame, spare: Int = Streaks.MAX_SPARE) {
    val scale = minOf(size.width / MomoDesignSize.width, size.height / MomoDesignSize.height)
    val dx = (size.width - MomoDesignSize.width * scale) / 2f
    val dy = (size.height - MomoDesignSize.height * scale) / 2f
    val t = progress.coerceIn(0f, 1f)
    withTransform({
        translate(dx, dy)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        withTransform({
            translate(frame.dx, frame.dy)
            rotate(frame.tilt, pivot = Offset(130f, 110f))
        }) {
            val tint = { c: Color -> c.saturated(frame.saturation) }
            val wasRegrowing = if (from == Mood.REGENERATING) 1f else 0f
            val willRegrow = if (to == Mood.REGENERATING) 1f else 0f
            drawGills(frame, spare, regrowing = wasRegrowing + (willRegrow - wasRegrowing) * t, tint)
            drawBody(frame, tint)
            // The blush is every mood's, so it is drawn once and never fades.
            drawEllipse(Offset(92f, 114f), 11f, 7f, tint(MomoPalette.Blush), alpha = 0.40f)
            drawEllipse(Offset(168f, 114f), 11f, 7f, tint(MomoPalette.Blush), alpha = 0.40f)
            if (from == to) {
                drawFace(to, frame, tint, alpha = 1f)
            } else {
                drawFace(from, frame, tint, alpha = 1f - t)
                drawFace(to, frame, tint, alpha = t)
            }
        }
    }
}

/**
 * The six gills: the left three fixed, the right three carrying [spare] spare
 * lives, shortened from the bottom up so the count reads as how far the
 * shortening has climbed (docs/ux/momo.md §3).
 *
 * [regrowing] is the top right gill's own blend — 1 while regenerating, 0
 * otherwise, and in between during a change to or from that mood — and the halo
 * comes and goes with it. That gill is keyed to the mood rather than to [spare]
 * because zero spare and regenerating are one moment seen twice: a streak can
 * only break once the last spare is gone. Keying it to [spare] instead would
 * draw a habit three clean days old as broken, since a run too young to have
 * banked anything also reports zero.
 */
private fun DrawScope.drawGills(frame: MomoFrame, spare: Int, regrowing: Float, tint: (Color) -> Color) {
    val spent = SpendOrder.take(Streaks.MAX_SPARE - spare.coerceIn(0, Streaks.MAX_SPARE))
    translate(top = frame.gillDrop) {
        Gills.forEachIndexed { index, gill ->
            // How short this gill is drawn, 0 full to 1 spent. The count says
            // it, and the mood says it too: §3's table draws **all three** right
            // gills short while regenerating, because zero spare and
            // regenerating are one moment seen twice. Taking the larger is what
            // makes a surface with no habit to name — the widget's still frame —
            // draw that face rather than a full cluster with one gill regrowing.
            val shortness = if (index in SpendOrder) maxOf(if (index in spent) 1f else 0f, regrowing) else 0f
            val isRegrowing = index == REGROWING_GILL && regrowing > 0f
            if (isRegrowing) {
                // The halo sits behind the short gill, breathing with it.
                drawCircle(
                    tint(MomoPalette.Bead),
                    radius = 20f * (0.8f + 0.32f * frame.regrow),
                    center = Offset(190f, 68f),
                    alpha = (0.10f + 0.20f * frame.regrow) * regrowing,
                )
            }
            rotate(frame.gills[index], pivot = gill.root) {
                when {
                    isRegrowing -> {
                        // It fades from whatever it was already drawn as — short
                        // when the count had already spent it. Fading from the
                        // full one regardless pops a spent gill back to full
                        // length for the length of the mood change.
                        val settled = if (index in spent) ShortGills[index] else gill
                        if (regrowing < 1f) settled.draw(this, tint, alpha = 1f - regrowing)
                        withTransform({
                            scale(0.82f + 0.24f * frame.regrow, pivot = gill.root)
                            rotate(-3f + 6f * frame.regrow, pivot = gill.root)
                        }) {
                            ShortGills[index].draw(this, tint, alpha = (0.72f + 0.28f * frame.regrow) * regrowing)
                        }
                    }

                    // A spent gill is the regrowing one's drawing held still:
                    // one recipe, so the two can never disagree. Only the top
                    // right gill animates its regrowth; the other two are short
                    // and still.
                    shortness >= 1f -> ShortGills[index].draw(this, tint, alpha = 1f)

                    shortness > 0f -> {
                        gill.draw(this, tint, alpha = 1f - shortness)
                        ShortGills[index].draw(this, tint, alpha = shortness)
                    }

                    else -> gill.draw(this, tint, alpha = 1f)
                }
            }
        }
    }
}

private fun DrawScope.drawBody(frame: MomoFrame, tint: (Color) -> Color) {
    withTransform({ scale(frame.breatheX, frame.breatheY, pivot = Offset(130f, 118f)) }) {
        drawPath(Tail, tint(MomoPalette.Accent))
        rotate(-26f, pivot = Offset(82f, 152f)) { drawEllipse(Offset(82f, 152f), 15f, 10f, tint(MomoPalette.Body)) }
        rotate(26f, pivot = Offset(178f, 152f)) { drawEllipse(Offset(178f, 152f), 15f, 10f, tint(MomoPalette.Body)) }
        drawEllipse(Offset(130f, 104f), 62f, 51f, tint(MomoPalette.Body))
        drawEllipse(Offset(130f, 122f), 42f, 28f, tint(MomoPalette.Belly), alpha = 0.62f)
    }
}

/** One mood's eyes, mouth and extras, at [alpha] — the part of the drawing that crossfades. */
private fun DrawScope.drawFace(mood: Mood, frame: MomoFrame, tint: (Color) -> Color, alpha: Float) {
    if (alpha <= 0f) return
    val ink = tint(MomoPalette.Ink)
    val mouth = tint(MomoPalette.Mouth)
    when (mood) {
        // The arc is thriving's alone: a squint of delight, not the face content
        // wears by default (docs/ux/momo.md §3).
        Mood.THRIVING -> {
            eye(Offset(104f, 93f), frame.eyeOpen) { drawPath(HappyEyeLeft, ink, alpha, style = EyeStroke) }
            eye(Offset(156f, 93f), frame.eyeOpen) { drawPath(HappyEyeRight, ink, alpha, style = EyeStroke) }
            drawPath(Smile, mouth, alpha)
        }

        // Content and worried are one eye at two sizes, and the mood carrying
        // anxiety is the wider-eyed of the two. Nothing else separates them, so
        // a change to one shape belongs in both.
        Mood.CONTENT -> {
            eye(Offset(104f, 96f), frame.eyeOpen) {
                drawEllipse(Offset(104f, 96f), 8.5f, 10.5f, ink, alpha)
                drawCircle(MomoPalette.Highlight, 3.0f, Offset(106.6f, 92.6f), alpha)
            }
            eye(Offset(156f, 96f), frame.eyeOpen) {
                drawEllipse(Offset(156f, 96f), 8.5f, 10.5f, ink, alpha)
                drawCircle(MomoPalette.Highlight, 3.0f, Offset(158.6f, 92.6f), alpha)
            }
            drawPath(Smile, mouth, alpha)
        }

        Mood.WORRIED -> {
            eye(Offset(104f, 96f), frame.eyeOpen) {
                drawEllipse(Offset(104f, 96f), 9.7f, 12f, ink, alpha)
                drawCircle(MomoPalette.Highlight, 3.4f, Offset(106.6f, 92.6f), alpha)
            }
            eye(Offset(156f, 96f), frame.eyeOpen) {
                drawEllipse(Offset(156f, 96f), 9.7f, 12f, ink, alpha)
                drawCircle(MomoPalette.Highlight, 3.4f, Offset(158.6f, 92.6f), alpha)
            }
            drawPath(WavyMouth, mouth, alpha, style = Stroke(3.6f, cap = StrokeCap.Round))
        }

        Mood.REGENERATING -> {
            eye(Offset(104f, 99f), frame.eyeOpen) { drawPath(SadEyeLeft, ink, alpha, style = EyeStroke) }
            eye(Offset(156f, 99f), frame.eyeOpen) { drawPath(SadEyeRight, ink, alpha, style = EyeStroke) }
            drawPath(SmallMouth, mouth, alpha, style = Stroke(3.6f, cap = StrokeCap.Round))
        }
    }
    if (mood == Mood.THRIVING) {
        sparkle(Offset(46f, 47f), SparkleLarge, frame.sparkle, frame.sparkleDrift, alpha)
        sparkle(Offset(214f, 37.3f), SparkleSmall, frame.sparkleLag, frame.sparkleDriftLag, alpha)
    }
    if (mood == Mood.WORRIED) frame.bead?.let { drawBead(it, alpha) }
}

/** A blink scales the eye vertically about its own centre, as the canvas does. */
private inline fun DrawScope.eye(centre: Offset, open: Float, draw: DrawScope.() -> Unit) {
    scale(1f, open, pivot = centre) { draw() }
}

private fun DrawScope.sparkle(centre: Offset, path: Path, phase: Float, drift: Offset, alpha: Float) {
    withTransform({
        // The drift first, so the pulse still scales and turns about the star's
        // own centre wherever the orbit has carried it.
        translate(drift.x, drift.y)
        scale(0.72f + 0.40f * phase, pivot = centre)
        rotate(70f * phase, pivot = centre)
    }) {
        drawPath(path, MomoPalette.Sparkle, alpha = (0.35f + 0.65f * phase) * alpha)
    }
}

/**
 * The sweat bead's fall: appears above its resting spot, holds, drops away.
 * Piecewise linear through the canvas's 0 / 45 / 70 / 100 percent stops.
 */
private fun DrawScope.drawBead(progress: Float, fade: Float) {
    val (alpha, dy, scale) = when {
        progress < 0.45f -> {
            val t = progress / 0.45f
            Triple(0.95f * t, -4f + 4f * t, 0.7f + 0.3f * t)
        }

        progress < 0.70f -> {
            val t = (progress - 0.45f) / 0.25f
            Triple(0.95f * (1f - t), 9f * t, 1f - 0.15f * t)
        }

        else -> Triple(0f, 9f, 0.85f)
    }
    if (alpha <= 0f) return
    withTransform({
        translate(top = dy)
        scale(scale, pivot = Offset(186f, 66f))
    }) {
        drawPath(SweatBead, MomoPalette.Sweat, alpha = alpha * fade)
    }
}

private fun DrawScope.drawEllipse(centre: Offset, rx: Float, ry: Float, color: Color, alpha: Float = 1f) {
    drawOval(color, topLeft = Offset(centre.x - rx, centre.y - ry), size = Size(rx * 2, ry * 2), alpha = alpha, style = Fill)
}

private class Bead(val x: Float, val y: Float, val r: Float)

private class Gill(val root: Offset, val tip: Offset, val beads: List<Bead>, val stroke: Float = 7.5f) {
    /** The form the geometry below is written in; [Gills] is a list of literals. */
    constructor(root: Offset, tip: Offset, vararg beads: Bead) : this(root, tip, beads.toList())

    fun draw(scope: DrawScope, tint: (Color) -> Color, alpha: Float) = with(scope) {
        drawLine(tint(MomoPalette.Accent), root, tip, strokeWidth = stroke, cap = StrokeCap.Round, alpha = alpha)
        beads.forEach { drawCircle(tint(MomoPalette.Bead), it.r, Offset(it.x, it.y), alpha = alpha) }
    }

    /**
     * This gill spent: every point pulled toward its own [root] at [SHORT_REACH]
     * (docs/ux/momo.md §3). The root itself does not move, so the sway above is
     * untouched — a short gill turns through the same angle at the same offset
     * as a long one.
     */
    fun shortened() = Gill(
        root,
        root + (tip - root) * SHORT_REACH,
        beads.map {
            Bead(
                root.x + (it.x - root.x) * SHORT_REACH,
                root.y + (it.y - root.y) * SHORT_REACH,
                it.r * SHORT_BEAD,
            )
        },
        SHORT_STROKE,
    )
}

/** Left top to bottom, then right top to bottom — the order [MomoFrame.gills] uses. */
private val Gills = listOf(
    Gill(
        Offset(84f, 78f),
        Offset(60.4f, 59.5f),
        Bead(54.9f, 73.9f, 8f),
        Bead(58.9f, 58.4f, 10f),
        Bead(73.0f, 50.7f, 8f),
        Bead(65.9f, 69.9f, 6.2f),
        Bead(72.4f, 62.0f, 6.2f),
    ),
    Gill(
        Offset(74f, 100f),
        Offset(42.2f, 96.7f),
        Bead(45.4f, 112.8f, 8f),
        Bead(40.3f, 96.5f, 10f),
        Bead(48.6f, 81.6f, 8f),
        Bead(53.1f, 102.9f, 6.2f),
        Bead(54.4f, 92.1f, 6.2f),
    ),
    Gill(
        Offset(84f, 122f),
        Offset(59.3f, 135.1f),
        Bead(69.5f, 145.3f, 8f),
        Bead(57.8f, 135.9f, 10f),
        Bead(56.6f, 121.0f, 8f),
        Bead(70.3f, 134.4f, 6.2f),
        Bead(65.9f, 125.8f, 6.2f),
    ),
    Gill(
        Offset(176f, 78f),
        Offset(199.6f, 59.5f),
        Bead(187.0f, 50.7f, 8f),
        Bead(201.1f, 58.4f, 10f),
        Bead(205.1f, 73.9f, 8f),
        Bead(188.2f, 62.4f, 6.2f),
        Bead(194.4f, 70.6f, 6.2f),
    ),
    Gill(
        Offset(186f, 100f),
        Offset(217.8f, 96.7f),
        Bead(211.4f, 81.6f, 8f),
        Bead(219.7f, 96.5f, 10f),
        Bead(214.6f, 112.8f, 8f),
        Bead(205.8f, 92.8f, 6.2f),
        Bead(206.8f, 103.7f, 6.2f),
    ),
    Gill(
        Offset(176f, 122f),
        Offset(200.7f, 135.1f),
        Bead(203.4f, 121.0f, 8f),
        Bead(202.2f, 135.9f, 10f),
        Bead(190.5f, 145.3f, 8f),
        Bead(193.9f, 126.5f, 6.2f),
        Bead(189.3f, 134.8f, 6.2f),
    ),
)

/**
 * The gill the halo marks, and the last of the three to be spent: the top right
 * one, which is therefore the first to grow back (docs/ux/momo.md §3). The
 * bottom-up spend order below exists to keep it here.
 */
private const val REGROWING_GILL = 3

/**
 * Which right gill is spent first, and then next: bottom-up, so the count reads
 * as how far the shortening has climbed. [Gills] runs left top-to-bottom then
 * right top-to-bottom, so the right cluster is 3, 4, 5 from the top.
 */
private val SpendOrder = listOf(5, 4, 3)

/** How far a spent gill reaches, as a fraction of its own full length. */
private const val SHORT_REACH = 0.661f
private const val SHORT_BEAD = 0.75f
private const val SHORT_STROKE = 5.5f

/**
 * Every gill spent, indexed alongside [Gills] so a spent one is its own drawing
 * shortened rather than a second transcription of it. Only the right three are
 * ever drawn from here.
 */
private val ShortGills = Gills.map { it.shortened() }

private val EyeStroke = Stroke(4.5f, cap = StrokeCap.Round)

private val Tail = Path().apply {
    moveTo(130f, 150f)
    quadraticTo(122f, 176f, 134f, 184f)
    quadraticTo(148f, 174f, 146f, 150f)
    close()
}
private val HappyEyeLeft = Path().apply {
    moveTo(95f, 99f)
    quadraticTo(104f, 87f, 113f, 99f)
}
private val HappyEyeRight = Path().apply {
    moveTo(147f, 99f)
    quadraticTo(156f, 87f, 165f, 99f)
}
private val SadEyeLeft = Path().apply {
    moveTo(95f, 95f)
    quadraticTo(104f, 103f, 113f, 95f)
}
private val SadEyeRight = Path().apply {
    moveTo(147f, 95f)
    quadraticTo(156f, 103f, 165f, 95f)
}
private val Smile = Path().apply {
    moveTo(113f, 118f)
    quadraticTo(130f, 138f, 147f, 118f)
    close()
}
private val WavyMouth = Path().apply {
    moveTo(117f, 125f)
    relativeQuadraticTo(6.5f, -7f, 13f, 0f)
    relativeQuadraticTo(6.5f, 7f, 13f, 0f)
}
private val SmallMouth = Path().apply {
    moveTo(119f, 121f)
    quadraticTo(130f, 130f, 141f, 121f)
}
private val SweatBead = Path().apply {
    moveTo(186f, 66f)
    relativeQuadraticTo(6f, 10f, 0f, 14f)
    relativeQuadraticTo(-6f, -4f, 0f, -14f)
    close()
}

/**
 * An eight-point star, as the canvas draws it: `l a,b b,a -b,a -a,b -a,-b -b,-a b,-a`,
 * starting from its top point at [origin]; it is `2 * (a + b)` across and its centre
 * sits `a + b` below [origin]. Public because Today's milestone ring (docs/ux/momo.md
 * §6) draws eight of the same star: a second copy would be a second geometry.
 */
fun sparkleStar(origin: Offset, a: Float, b: Float): Path = Path().apply {
    moveTo(origin.x, origin.y)
    relativeLineTo(a, b)
    relativeLineTo(b, a)
    relativeLineTo(-b, a)
    relativeLineTo(-a, b)
    relativeLineTo(-a, -b)
    relativeLineTo(-b, -a)
    relativeLineTo(b, -a)
    close()
}
private val SparkleLarge = sparkleStar(Offset(46f, 30f), 4.6f, 11.5f)
private val SparkleSmall = sparkleStar(Offset(214f, 24f), 3.8f, 9.5f)
