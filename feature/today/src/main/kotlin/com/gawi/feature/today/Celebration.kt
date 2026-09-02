// Every number here is a timing or an amplitude of the celebration as tuned on
// the Gawi Redesign canvas's Habitat & motion page (docs/ux/momo.md §6). They
// are the sequence, not values standing in for one — the reason Momo.kt gives.
// Narrowed to this file, never widened in config/detekt/detekt.yml.
@file:Suppress("MagicNumber")

package com.gawi.feature.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.ui.component.rememberAnimationsEnabled

/**
 * Whether going from [previous] to [next] finishes the day: the mood enters
 * thriving from somewhere else (docs/ux/momo.md §6). `null` is the first
 * composition, which never celebrates — a screen opened on a finished day
 * shows a thriving Momo, not a party for something that happened earlier.
 */
internal fun celebrates(previous: Mood?, next: Mood): Boolean = previous != null && previous != Mood.THRIVING && next == Mood.THRIVING

/** One bubble of the burst at one instant: its lane, how far up its rise it is (0..1), and its alpha. */
internal data class BurstBubble(val lane: Int, val progress: Float, val alpha: Float)

/**
 * The celebration [progress] of the way through, 0..1 — a pure function, like
 * [HabitatFrame]: Momo hops once, a rush of bubbles leaves from under the tail,
 * and the water brightens for a beat. At 1 there is nothing left to draw.
 */
internal data class CelebrationFrame(
    /** How far through the run this is, 0..1; 1 is over. */
    val progress: Float,
    /** How far Momo is lifted, dp, positive upward; 0 at rest. */
    val hop: Float,
    /** The white wash over the water, 0..1. */
    val glow: Float,
    val bubbles: List<BurstBubble>,
) {
    /** Over means nothing left to draw and nothing to lift — the state before a run and after one. */
    val isOver: Boolean get() = progress >= 1f

    companion object {
        /** How long the whole sequence takes. */
        const val MILLIS = 1400

        /** Nothing: what [at] returns once the run is over. */
        val NONE = CelebrationFrame(1f, 0f, 0f, emptyList())

        fun at(progress: Float): CelebrationFrame {
            val p = progress.coerceIn(0f, 1f)
            if (p >= 1f) return NONE
            return CelebrationFrame(
                progress = p,
                hop = HOP * piecewise(p, HOP_STOPS),
                glow = GLOW * piecewise(p, GLOW_STOPS),
                bubbles = burstAt(BurstLanes, p, BURST_ALPHA),
            )
        }

        /** The Habitat & motion page's defaults, approved 2026-08-26. */
        private const val HOP = 14f
        private const val GLOW = 0.22f
        private const val BURST_ALPHA = 0.85f

        private val HOP_STOPS = listOf(0f to 0f, 0.28f to 1f, 0.52f to 0f, 0.68f to 0.3f, 0.82f to 0f, 1f to 0f)
        private val GLOW_STOPS = listOf(0f to 0f, 0.30f to 1f, 1f to 0f)
    }
}

/**
 * The burst's envelope, shared with [MilestoneFrame]: each of [lanes] leaves on
 * its own delay, brightens to [peakAlpha] over the first 12 % of its rise and
 * fades to nothing by the top; lanes that have not left yet are not in the list.
 */
internal fun burstAt(lanes: List<BurstLane>, p: Float, peakAlpha: Float): List<BurstBubble> = lanes.mapIndexed { lane, l ->
    val q = (p - l.delay) / (1f - l.delay)
    BurstBubble(lane, q.coerceIn(0f, 1f), if (q <= 0f) 0f else peakAlpha * piecewise(q, BURST_STOPS))
}.filter { it.alpha > 0f }

private val BURST_STOPS = listOf(0f to 0f, 0.12f to 1f, 1f to 0f)

/**
 * Linear between keyframe stops, each `position to value`, as the CSS keyframes
 * are. Shared with [MilestoneFrame], whose board is written in the same stops.
 */
internal fun piecewise(p: Float, stops: List<Pair<Float, Float>>): Float {
    val next = stops.indexOfFirst { it.first >= p }
    if (next <= 0) return stops.first().second
    val (x0, y0) = stops[next - 1]
    val (x1, y1) = stops[next]
    return y0 + (y1 - y0) * ((p - x0) / (x1 - x0))
}

/**
 * A celebration in flight, as state: [frame] is the snapshot to draw with,
 * read inside a draw or layout lambda so the run redraws and does not
 * recompose; [isOver] changes only at its two ends, so a composable may branch
 * on it. Obtain one with [rememberCelebration].
 */
@Stable
internal class CelebrationState {
    private val progress = Animatable(1f)
    private var previous: Mood? = null

    val frame: CelebrationFrame get() = CelebrationFrame.at(progress.value)
    val isOver: State<Boolean> = derivedStateOf { progress.value >= 1f }

    /**
     * Yields: settles a run in flight without forgetting the mood seen. Called
     * when a milestone fires, so a day-complete run that started during one
     * does not surface mid-air when the milestone ends (the same-tick case
     * would finish unseen anyway — 1400 ms inside 2000 — but a run started a
     * second in would not).
     */
    internal suspend fun yield() {
        progress.snapTo(1f)
    }

    /**
     * Sees [mood], called from an effect keyed on it. Fires when [celebrates]
     * says the day was just finished; otherwise settles, so a run cut short by
     * the mood leaving thriving again does not freeze mid-air.
     */
    internal suspend fun see(mood: Mood, animationsOn: Boolean) {
        val was = previous
        previous = mood
        if (animationsOn && celebrates(was, mood)) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(CelebrationFrame.MILLIS, easing = LinearEasing))
        } else {
            progress.snapTo(1f)
        }
    }
}

/**
 * The day-complete celebration, following [mood] (docs/ux/momo.md §6).
 *
 * Remember it **outside** the list that scrolls the tank, not in the tank: the
 * edge is detected by comparing each mood with the one seen before, and a
 * `remember` inside a `LazyColumn` item is disposed when the item scrolls off,
 * so a user who ticks the last habit near the bottom of a long list and scrolls
 * back up would find the tank composed fresh, with nothing to compare against.
 * The screen outlives the item; its memory is the one that holds.
 *
 * Detected in composition and not on the state flow, because the flow re-emits
 * the same mood on every return to the screen and every reminder tick, and a
 * detector there would celebrate a finished day again each time the app came
 * back. A cold start on a finished day, a rotation and animations off never
 * fire it, and the resting thriving frame already says thriving.
 *
 * Keyed on the gate as well as the mood, like `rememberMoodTransition`: the
 * gate reads false on the first composition and flips a frame later, and a
 * re-run with the same mood is a no-op — `celebrates(mood, mood)` is false
 * and `previous` is already set — so the key costs nothing and the capture is
 * never stale.
 *
 * **A null [mood] is a screen with no mood yet — `Loading` and `Unavailable` —
 * and is not seen at all.** This state is remembered above the branch that
 * chooses between them, so it outlives the change from one to another, and the
 * guard is what keeps that from mattering: `celebrates` fires only against a
 * non-null `previous`, so feeding a stand-in mood while loading would seed
 * `previous` and turn the first real thriving into a party for something that
 * happened before the app opened — the one case the paragraph above says never
 * fires. Skipping the sighting leaves `previous` null until there is a real
 * mood, which is exactly the cold start the guard is for.
 *
 * **It skips the sighting without clearing `previous`, and that shows on one
 * path.** A review reasoned that the cold start is the *only* way through the
 * null, because `Loading` is nothing but `stateIn`'s initial value. That holds
 * for `Loading` and not for `Unavailable`: `TodayViewModel`'s `catch` terminates
 * the subscription, and says itself that recovery is the screen re-subscribing,
 * which emits `Habits` again. `TodayRoute` composes `TodayScreen` for every
 * state, so this object survives `Habits` to `Unavailable` and back with its
 * `previous` intact, and returning to a finished day celebrates. The old
 * per-branch state could not: `Unavailable` disposed `HabitList` and the motion
 * with it, so coming back was a first sighting.
 *
 * Left standing rather than reset. Either reading is defensible — the day may
 * have been finished by the very tick that raced the failure, in which case the
 * celebration is owed — and reaching it needs a Room, codec or settings failure
 * and then a recovery, which is not worth a forget-path on this class. Recorded
 * because the hoist changed it and nobody chose it.
 */
@Composable
internal fun rememberCelebration(mood: Mood?, animationsOn: Boolean): CelebrationState {
    val state = remember { CelebrationState() }
    LaunchedEffect(mood, animationsOn) { if (mood != null) state.see(mood, animationsOn) }
    return state
}

/**
 * What the screen decides about motion and hands to the tank: the one reading
 * of the animations gate, the day-complete celebration and the streak-milestone
 * one, both of which have to outlive the list item the tank sits in. When the
 * two fire on one tick the milestone owns the tank ([MascotPanel] draws only
 * its frame while it runs); the day-complete run finishes unseen underneath.
 */
internal class TodayMotion(val animationsOn: Boolean, val celebration: CelebrationState, val milestone: MilestoneState)

/**
 * The screen's [TodayMotion] for [mood] and [rows].
 *
 * Remember it above the `Scaffold`, not merely above the list: the app-bar chip
 * reads `milestone.current` to carry the milestone line (today-view §1), and the
 * bar is the list's sibling rather than its parent, so a motion owned by the
 * list is invisible to it. Above the `Scaffold` it is above both.
 *
 * [mood] is nullable because the states without one — `Loading`, `Unavailable` —
 * are branches *under* this, and [rememberCelebration] explains what the null
 * protects. [rows] is empty for the same states, which needs no guard of its
 * own: `milestoneCrossed` treats a first sighting as no crossing, so an empty
 * list leaves `seen` empty and is indistinguishable from a fresh state.
 */
@Composable
internal fun rememberTodayMotion(mood: Mood?, rows: List<HabitRowUi>): TodayMotion {
    val animationsOn by rememberAnimationsEnabled()
    val celebration = rememberCelebration(mood, animationsOn)
    val milestone = rememberMilestone(rows, animationsOn)
    // The milestone owns the tank: the day's run yields whenever one begins.
    LaunchedEffect(milestone.current) { if (milestone.current != null) celebration.yield() }
    return TodayMotion(animationsOn, celebration, milestone)
}

/**
 * Draws the whole celebration at [frame] — the glow, then the burst — over
 * this scope; the hop is the caller's, applied to Momo. The tank draws the two
 * halves separately, [drawCelebrationGlow] behind Momo and
 * [drawCelebrationBurst] in front, so the wash never covers the face while the
 * bubbles — whose lanes run up behind the body — stay visible.
 */
internal fun DrawScope.drawCelebration(frame: CelebrationFrame, colour: Color) {
    drawCelebrationGlow(frame, colour)
    drawCelebrationBurst(frame, colour)
}

/** The brightening of the water: a white wash over the whole tank, behind Momo. */
internal fun DrawScope.drawCelebrationGlow(frame: CelebrationFrame, colour: Color) {
    if (frame.glow > 0f) drawRect(colour, alpha = frame.glow)
}

/**
 * The burst, in dp: bubbles rising from under the tail on fixed lanes across
 * the middle of the tank, each on its own delay, so the same frame always
 * draws the same picture.
 */
internal fun DrawScope.drawCelebrationBurst(frame: CelebrationFrame, colour: Color) {
    drawBurst(BurstLanes, frame.bubbles, BURST_RISE, colour)
}

/**
 * Draws [bubbles] on their [lanes], shared with the milestone: each starts
 * under Momo's tail and climbs [rise] dp, growing as it goes.
 */
internal fun DrawScope.drawBurst(lanes: List<BurstLane>, bubbles: List<BurstBubble>, rise: Float, colour: Color) {
    val dp = 1.dp.toPx()
    bubbles.forEach { b ->
        val lane = lanes[b.lane]
        val x = size.width * lane.across
        val y = size.height - (BURST_START + b.progress * rise) * dp
        val radius = lane.size / 2f * (0.4f + 0.7f * b.progress) * dp
        drawCircle(colour, radius, Offset(x, y), alpha = b.alpha)
    }
}

/** One lane of a burst: where across the tank it rises (0..1), the bubble's size in dp, and when in the run it leaves (0..1). */
internal class BurstLane(val across: Float, val size: Float, val delay: Float)

/**
 * Fourteen lanes, spread deterministically across the middle 120 dp of the
 * canvas's 362 dp tank and staggered over the first 35 % of the run — the
 * page's `(i * 37) % 120` and `(i * 53) % 35` — so no randomness reaches a frame.
 */
private val BurstLanes = List(14) { i ->
    BurstLane(across = (120f + (i * 37) % 120) / 362f, size = 3f + (i % 4) * 1.5f, delay = ((i * 53) % 35) / 100f)
}

/** Bubbles start this far above the floor — under Momo's tail — and, for the day's burst, rise this far, dp. */
private const val BURST_START = 70f
private const val BURST_RISE = 170f
