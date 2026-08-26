// The whole file is timing and amplitude constants, transcribed from the motion
// page that decided them (docs/ux/momo.md §3). Naming each — "the seconds a
// content float takes" — would alias the table to invented words and put the
// numbers a reviewer wants to compare one indirection further from the doc.
// Narrowed to this file and MomoDrawing.kt, never widened in
// config/detekt/detekt.yml, for the reason Color.kt gives.
@file:Suppress("MagicNumber")

package com.gawi.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * duration scale* set to off, read by [rememberAnimationsEnabled], because a
 * viewer who turned animations off should not have to find a second switch.
 *
 * **A mood change is one Momo, not two** (docs/ux/momo.md §3). The first cut
 * crossfaded two whole drawings, and because each mood floats at its own
 * tempo the two bodies sat at different heights while both were visible. Now
 * the body, tail and gills are drawn once from [MomoFrame.between] — the two
 * moods' frames at the same instant, interpolated by a progress that runs
 * over [MomoMotion.TRANSITION_MILLIS] — and only the face, its extras and the
 * regrowing gill crossfade. With animations off the change is a cut, because a
 * fade is an animation too.
 *
 * The composable carries no description of its own. The copy line beside it
 * is the mood's description (docs/ux/momo.md §5), and a second announcement of
 * the same state would be the "anonymous checkbox beside a named picture"
 * mistake the widget made in reverse. The caller owns the semantics; this
 * exposes a test tag only, `momo:<mood>` for the mood being shown or arrived at.
 *
 * **A Robolectric test that composes this must set
 * `Settings.Global.ANIMATOR_DURATION_SCALE` to 0 before the activity launches**
 * — `AnimationsOffRule` in `:feature:today`'s and `:app`'s test sets does it —
 * or `waitForIdle` never returns: [rememberFrameClock] is a permanent awaiter
 * on the frame clock, and the timeout it produces names nothing here.
 */
@Composable
fun Momo(mood: Mood, modifier: Modifier = Modifier, animated: Boolean = true) {
    val animationsOn by rememberAnimationsEnabled(animated)
    Momo(rememberMoodTransition(mood, animationsOn), animationsOn, modifier)
}

/**
 * Momo following a [transition] the caller owns — the entry for a surface
 * that has already read the animations gate and shares the mood change with
 * something else, as the Today tank does with its water and weeds. One gate,
 * one transition, so the face and the habitat can never disagree about where
 * a mood change stands.
 */
@Composable
fun Momo(transition: MoodTransitionState, animationsOn: Boolean, modifier: Modifier = Modifier) {
    Momo(transition, rememberFrameClock(animationsOn), modifier)
}

/**
 * Momo following a [transition] on a [seconds] clock the caller owns, both
 * shared with whatever else moves around it — so the tank's water, weeds and
 * Momo are one gate, one transition and one clock by construction, not three
 * effects that happen to start on the same frame.
 */
@Composable
fun Momo(transition: MoodTransitionState, seconds: State<Float>, modifier: Modifier = Modifier) {
    // fillMaxSize is load-bearing: the caller sizes the box, and a Canvas with
    // no size of its own measures 0 x 0 — which is a tank with nothing in it,
    // while every test that only asked whether the node existed stayed green.
    // Caught on the emulator. The clock and the progress are read in here and
    // not above, so a frame redraws this Canvas and recomposes nothing.
    Canvas(modifier.fillMaxSize().testTag("momo:${transition.to}")) {
        val t = transition.current
        val s = seconds.value
        drawMomo(t.from, t.to, t.progress, MomoFrame.between(MomoFrame.at(t.from, s), MomoFrame.at(t.to, s), t.progress))
    }
}

/**
 * Where a mood change stands: [progress] of the way from [from] to [to].
 * Settled when [progress] is 1, at which point [from] equals [to].
 */
data class MoodTransition(val from: Mood, val to: Mood, val progress: Float) {
    /** How far the habitat has drained: 1 while regenerating, 0 otherwise, and between during a change (momo.md §4). */
    val drained: Float
        get() {
            val a = if (from == Mood.REGENERATING) 1f else 0f
            val b = if (to == Mood.REGENERATING) 1f else 0f
            return a + (b - a) * progress
        }
}

/**
 * A mood change in flight, as state. [from] and [to] are the moods either
 * side of it; [current] is the snapshot to draw with, read inside a draw
 * lambda so that the progress advancing redraws and does not recompose.
 * Obtain one with [rememberMoodTransition].
 */
@Stable
class MoodTransitionState internal constructor(mood: Mood) {
    var from: Mood by mutableStateOf(mood)
        private set
    var to: Mood by mutableStateOf(mood)
        private set
    private val progress = Animatable(1f)

    val current: MoodTransition get() = MoodTransition(from, to, progress.value)

    /**
     * Follows [mood], called from an effect keyed on it and on [animationsOn].
     * A change that lands while a run is in flight — a second tick within the
     * transition, or the gate flipping — first finishes the run it interrupted,
     * over what was left of its time, so the body never jumps from an
     * interpolated pose to a full frame; then the new run starts from where
     * the last one settled. With animations off every step is a cut.
     */
    internal suspend fun follow(mood: Mood, animationsOn: Boolean) {
        if (progress.value < 1f) {
            if (animationsOn) {
                progress.animateTo(1f, tween((MomoMotion.TRANSITION_MILLIS * (1f - progress.value)).toInt()))
            } else {
                progress.snapTo(1f)
            }
            from = to
        }
        if (to == mood) return
        from = to
        to = mood
        progress.snapTo(0f)
        if (animationsOn) {
            progress.animateTo(1f, tween(MomoMotion.TRANSITION_MILLIS))
        } else {
            progress.snapTo(1f)
        }
        from = mood
    }
}

/**
 * Follows [mood] through its changes: each new value starts a run from the one
 * being left over [MomoMotion.TRANSITION_MILLIS], or a cut when [animationsOn]
 * is false — a fade is an animation too. Starts settled, so the first
 * composition draws one face and not a fade-in. [to] moves when the effect
 * runs, one frame after the mood does; the alternative, moving it in
 * composition, would draw the destination's full frame for that one frame
 * before the fade began, which is the pop this exists to remove.
 *
 * Shared between [Momo] and the tank around it in `:feature:today`, which
 * passes the one instance to the transition overload of [Momo], so the water
 * drains and the weeds droop on exactly the progress the face changes on.
 */
@Composable
fun rememberMoodTransition(mood: Mood, animationsOn: Boolean): MoodTransitionState {
    val state = remember { MoodTransitionState(mood) }
    LaunchedEffect(mood, animationsOn) { state.follow(mood, animationsOn) }
    return state
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
        /** How long a mood change takes, the Habitat & motion page's default (docs/ux/momo.md §3). */
        const val TRANSITION_MILLIS = 550

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
    /** Thriving's first sparkle: 0 dim and small, 1 bright and large. */
    val sparkle: Float,
    /**
     * The second sparkle, a third of a cycle behind the first. Its own field
     * because the lag has to be applied to the clock, not to [sparkle]'s value:
     * a wave is not injective, so no arithmetic on the value recovers the phase,
     * and the first cut's `(sparkle + 0.667) % 1` snapped twice a cycle.
     */
    val sparkleLag: Float,
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
                sparkleLag = wave(seconds + 0.7f, 2.1f),
                bead = if (fidget) (seconds / 2.6f) % 1f else null,
                regrow = wave(seconds, 2.7f),
                saturation = m.saturation,
            )
        }

        /**
         * The frame [t] of the way from [from] to [to], both taken at the same
         * instant: everything that moves the body is interpolated, so the
         * character stays one animal through a mood change, while the face
         * fields are the destination's — a face is not a thing to average, it
         * crossfades in [drawMomo]. The bead is the exception: a worried face
         * fading out keeps its bead until it is gone.
         *
         * Continuous at both ends by construction, whatever the two moods'
         * periods: at 0 the body is [from]'s frame, at 1 it is [to]'s.
         */
        fun between(from: MomoFrame, to: MomoFrame, t: Float): MomoFrame {
            // Exact at both ends: `a + (b - a) * 1f` can miss b by an ulp, and a
            // frame that has arrived should equal the frame it arrived at.
            fun lerp(a: Float, b: Float) = when {
                t <= 0f -> a
                t >= 1f -> b
                else -> a + (b - a) * t
            }
            return to.copy(
                dx = lerp(from.dx, to.dx),
                dy = lerp(from.dy, to.dy),
                tilt = lerp(from.tilt, to.tilt),
                breatheX = lerp(from.breatheX, to.breatheX),
                breatheY = lerp(from.breatheY, to.breatheY),
                gills = from.gills.zip(to.gills) { a, b -> lerp(a, b) },
                gillDrop = lerp(from.gillDrop, to.gillDrop),
                saturation = lerp(from.saturation, to.saturation),
                bead = to.bead ?: from.bead,
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

/**
 * Fade toward the colour's grey; 1 is the colour, 0 is greyscale.
 *
 * The grey is the CSS `saturate()` filter's — Rec. 709 weights on the
 * *encoded* channels — because that filter is what the approved canvas applied
 * (docs/ux/momo.md §3), and it keeps the encoded lightness. `luminance()`
 * would not: it linearises first, so mixing it into sRGB components lands on a
 * grey about a fifth darker, and the first cut dimmed Momo by that much without
 * anyone choosing to. The tank's own drain is what carries the dimming.
 */
internal fun Color.saturated(amount: Float): Color {
    if (amount >= 1f) return this
    val grey = 0.2126f * red + 0.7152f * green + 0.0722f * blue
    return Color(
        red = grey + (red - grey) * amount,
        green = grey + (green - grey) * amount,
        blue = grey + (blue - grey) * amount,
        alpha = alpha,
    )
}
