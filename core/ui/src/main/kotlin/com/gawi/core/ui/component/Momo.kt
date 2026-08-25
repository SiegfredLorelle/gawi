// The whole file is timing and amplitude constants, transcribed from the motion
// page that decided them (docs/ux/momo.md §3). Naming each — "the seconds a
// content float takes" — would alias the table to invented words and put the
// numbers a reviewer wants to compare one indirection further from the doc.
// Narrowed to this file and MomoDrawing.kt, never widened in
// config/detekt/detekt.yml, for the reason Color.kt gives.
@file:Suppress("MagicNumber")

package com.gawi.core.ui.component

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.gawi.core.domain.mascot.Mood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Momo, animated — docs/ux/momo.md.
 *
 * The character is the one drawn on the Gawi Redesign canvas and tuned on its
 * Momo Motion page; every shape is in [drawMomo] and every timing is in
 * [MomoMotion]. It is drawn in code rather than played from an asset because
 * the motion is entirely rigid — translate, rotate, scale, opacity on fixed
 * groups — which is what a `Canvas` does natively, and because the alternative
 * routes both failed a gate: Rive's export is behind a paid plan, and Lottie
 * would add a runtime and a JSON pipeline to reach the same pixels
 * (docs/ux/momo.md §1).
 *
 * **The frame is a pure function of the mood and the clock**, [MomoFrame.at],
 * so a test can ask for the frame at any instant without a composition, and
 * the widget and notification can ask for the resting one ([MomoFrame.rest]).
 * `animated = false` draws that resting frame; so does the system's *Animator
 * duration scale* set to off, read once per composition, because a viewer who
 * turned animations off should not have to find a second switch.
 *
 * The composable carries no description of its own. The copy line beside it
 * is the mood's description (docs/ux/momo.md §5), and a second announcement of
 * the same state would be the "anonymous checkbox beside a named picture"
 * mistake the widget made in reverse. The caller owns the semantics; this
 * exposes a test tag only.
 */
@Composable
fun Momo(mood: Mood, modifier: Modifier = Modifier, animated: Boolean = true) {
    val context = LocalContext.current
    val animationsOn = remember(animated) {
        animated && Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationsOn) {
        if (!animationsOn) {
            seconds.floatValue = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    // Opacity only, the canvas's .55s. Both moods draw during the fade, which
    // is what makes a gill that changes length read as a change rather than a
    // cut.
    Crossfade(targetState = mood, animationSpec = tween(MomoMotion.CROSSFADE_MILLIS), modifier = modifier) { shown ->
        Canvas(Modifier.testTag("momo:$shown")) {
            drawMomo(shown, MomoFrame.at(shown, seconds.floatValue))
        }
    }
}

/**
 * The per-mood timings, in seconds and design-space units (docs/ux/momo.md
 * §3). The values are the Momo Motion page's, approved 2026-08-25.
 */
data class MomoMotion(
    /** One float cycle: down and back. The worried fidget reuses it. */
    val floatPeriod: Float,
    /** How far the float rises, in design px; the fidget ignores it. */
    val floatAmplitude: Float,
    val breathePeriod: Float,
    val gillPeriod: Float,
    /** Half the sway, in degrees: the gill swings between minus and plus this. */
    val gillSway: Float,
    /** Worried: the gills hang lower, in design px. */
    val gillDrop: Float,
    /** Content: seconds between blinks; null means the eyes never close. */
    val blinkPeriod: Float?,
    /** Regenerating: how much colour Momo keeps, 1 being all of it. */
    val saturation: Float,
) {
    companion object {
        const val CROSSFADE_MILLIS = 550

        val THRIVING = MomoMotion(2.5f, 7f, 1.5f, 1.5f, 4.5f, 0f, null, 1f)
        val CONTENT = MomoMotion(4.2f, 7f, 3.4f, 2.9f, 4.5f, 0f, 5.4f, 1f)
        val WORRIED = MomoMotion(1.7f, 0f, 3.4f, 4.4f, 4.5f, 7f, 5.4f, 1f)
        val REGENERATING = MomoMotion(6f, 7f, 5f, 4.6f, 4.5f, 0f, 5.4f, 0.34f)

        fun of(mood: Mood): MomoMotion = when (mood) {
            Mood.THRIVING -> THRIVING
            Mood.CONTENT -> CONTENT
            Mood.WORRIED -> WORRIED
            Mood.REGENERATING -> REGENERATING
        }
    }
}

/**
 * Where every moving part is at one instant, in design space (260 x 200).
 *
 * A value object rather than a bag of animated states so that [drawMomo] is
 * deterministic and a test can pin it: the same mood at the same second is the
 * same picture.
 */
data class MomoFrame(
    /** Whole-character offset, design px. */
    val dx: Float,
    val dy: Float,
    /** Whole-character tilt, degrees. */
    val tilt: Float,
    /** Body scale about its own centre. */
    val breatheX: Float,
    val breatheY: Float,
    /** Six gills — left top to bottom, then right top to bottom — in degrees. */
    val gills: List<Float>,
    /** How far the gill group hangs, design px. */
    val gillDrop: Float,
    /** Eye height, 1 open to 0.12 mid-blink. */
    val eyeOpen: Float,
    /** Thriving's two sparkles: 0 dim and small, 1 bright and large. */
    val sparkle: Float,
    /** Worried's sweat bead, 0..1 through its fall; null when there is none. */
    val bead: Float?,
    /** Regenerating's halo and regrowing gill, 0..1 through their pulse. */
    val regrow: Float,
    val saturation: Float,
) {
    companion object {
        /** The still frame: what the widget shows and what a viewer with animations off sees. */
        fun rest(mood: Mood): MomoFrame = at(mood, 0f)

        /**
         * The frame [seconds] into the loop. Every curve is the CSS
         * `ease-in-out` keyframe pair it transcribes, which a half-cosine is.
         */
        fun at(mood: Mood, seconds: Float): MomoFrame {
            val m = MomoMotion.of(mood)
            val fidget = mood == Mood.WORRIED
            val floatPhase = wave(seconds, m.floatPeriod)
            return MomoFrame(
                dx = if (fidget) -2.5f * sin(TAU * seconds / m.floatPeriod) else 0f,
                dy = if (fidget) abs(sin(TAU * seconds / m.floatPeriod)) else -m.floatAmplitude * floatPhase,
                tilt = if (fidget) 0.8f * sin(TAU * seconds / m.floatPeriod) else -1.1f * floatPhase,
                breatheX = 1f + 0.028f * wave(seconds, m.breathePeriod),
                breatheY = 1f + 0.036f * wave(seconds, m.breathePeriod),
                gills = GILL_DELAYS.map { delay -> -m.gillSway * cos(TAU * (seconds + delay) / m.gillPeriod) },
                gillDrop = m.gillDrop,
                eyeOpen = m.blinkPeriod?.let { blink(seconds, it) } ?: 1f,
                sparkle = wave(seconds, 2.1f),
                bead = if (fidget) (seconds / 2.6f) % 1f else null,
                regrow = wave(seconds, 2.7f),
                saturation = m.saturation,
            )
        }

        /** 0 at the start of a period, 1 halfway, 0 again at the end: `ease-in-out` there and back. */
        private fun wave(seconds: Float, period: Float): Float = 0.5f - 0.5f * cos(TAU * seconds / period)

        /** The canvas's `steps(1,end)` blink: closed between 96% and 98% of the period. */
        private fun blink(seconds: Float, period: Float): Float {
            val phase = (seconds / period) % 1f
            return if (phase >= 0.96f && phase < 0.98f) 0.12f else 1f
        }

        private const val TAU = (2 * PI).toFloat()

        // The CSS animation-delays, negative there so each gill starts part way
        // through its cycle; as an offset added to the clock they come out the
        // same way round.
        private val GILL_DELAYS = listOf(0.2f, 0.7f, 1.2f, 0.45f, 0.95f, 1.5f)
    }
}

/**
 * Momo's colours. The character's own, not theme roles: docs/ux/visual-identity.md
 * §3 keeps the mascot off the scheme so it stays the one warm thing in a teal
 * tank, and these are the same in both themes for the same reason a plush toy
 * is. [MomoFrame.saturation] is the one thing that varies them, and only while
 * regenerating.
 */
object MomoPalette {
    val Body = Color(0xFFF7C3D1)
    val Belly = Color(0xFFFFE3EA)
    val Accent = Color(0xFFE8879F)
    val Bead = Color(0xFFF2A0B8)
    val Blush = Color(0xFFF58AA6)
    val Ink = Color(0xFF3A2530)
    val Mouth = Color(0xFFC2607A)
    val Sparkle = Color(0xFFFFCE5C)
    val Sweat = Color(0xFF8FD3E8)
    val Highlight = Color(0xFFFFFFFF)
}

/** Fade toward the colour's own grey; 1 is the colour, 0 is greyscale. */
internal fun Color.saturated(amount: Float): Color {
    if (amount >= 1f) return this
    val grey = luminance()
    return Color(
        red = grey + (red - grey) * amount,
        green = grey + (green - grey) * amount,
        blue = grey + (blue - grey) * amount,
        alpha = alpha,
    )
}
