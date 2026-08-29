// Every number here is a timing or an amplitude of the milestone celebration as
// tuned on the Gawi Redesign canvas's Milestone celebration page (docs/ux/momo.md
// §6) — the "frame by frame" board is these stops, printed. They are the
// sequence, not values standing in for one, the reason Celebration.kt gives.
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.streak.Milestones
import com.gawi.core.ui.component.MomoPalette
import com.gawi.core.ui.component.sparkleStar
import com.gawi.core.ui.streak.StreakUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * The rung a streak going from [previous] to [next] crosses, or null —
 * [Milestones.crossed] over the drawn streak types.
 *
 * `null` [previous] is the first sighting of the row and never fires — a
 * screen opened on day seven shows a seven, not a party for something that
 * happened earlier (the rule `celebrates` follows). None and Broken count as
 * zero on the way in and never fire on the way out. A change of unit is a
 * schedule edit rather than a streak earned, and is a first sighting again.
 */
internal fun milestoneCrossed(previous: StreakUi?, next: StreakUi): Int? {
    val count = next.liveCount()
    val was = previous?.countBefore(next)
    if (count == null || was == null) return null
    return Milestones.crossed(was, count, weekly = next is StreakUi.Weeks)
}

/** A live streak's count, or null for None and Broken, which have nothing to reach. */
private fun StreakUi.liveCount(): Int? = when (this) {
    is StreakUi.Days -> count
    is StreakUi.Weeks -> count
    StreakUi.None, is StreakUi.Broken -> null
}

/** The count this streak hands on to [next]: zero from nothing or a break, its own when the unit matches, null across units. */
private fun StreakUi.countBefore(next: StreakUi): Int? = when (this) {
    StreakUi.None -> 0

    // A break remembers its unit too: a daily habit's break handing on to a
    // weekly count is the schedule edit the KDoc above rules out.
    is StreakUi.Broken -> if (weekly == (next is StreakUi.Weeks)) 0 else null

    is StreakUi.Days -> if (next is StreakUi.Days) count else null

    is StreakUi.Weeks -> if (next is StreakUi.Weeks) count else null
}

/** What fired: the row, the rung it reached and the rung's unit — what the copy line and the badge need. */
internal data class Milestone(val habitId: HabitId, val count: Int, val weekly: Boolean) {
    /** Position on its ladder, for choosing one when two rows cross on the same tick. */
    val rank: Int get() = Milestones.ladder(weekly).indexOf(count)
}

/** Which of [rows] just crossed a rung against the streaks last [seen] for them. Pure, so a test can drive it. */
internal fun milestonesIn(rows: List<HabitRowUi>, seen: Map<HabitId, StreakUi>): List<Milestone> = rows.mapNotNull { row ->
    milestoneCrossed(seen[row.id], row.streak)?.let { Milestone(row.id, it, row.streak is StreakUi.Weeks) }
}

/** One star of the ring at one instant: where it is around Momo (degrees, clockwise from up), how far out (dp), and how big and bright. */
internal data class RingSparkle(val index: Int, val angleDegrees: Float, val radius: Float, val scale: Float, val alpha: Float)

/**
 * The milestone celebration [progress] of the way through, 0..1 — a pure
 * function like [CelebrationFrame], and bigger in every dimension it shares
 * with it: Momo hops twice, a wider rush of bubbles leaves from under the
 * tail, the water brightens harder and for longer, and a ring of eight of the
 * thriving face's own sparkles opens out around her. The row that earned it
 * swells its badge in the first half. At 1 there is nothing left to draw.
 */
internal data class MilestoneFrame(
    /** How far through the run this is, 0..1; 1 is over. */
    val progress: Float,
    /** How far Momo is lifted, dp, positive upward; 0 at rest. */
    val hop: Float,
    /** The white wash over the water, 0..1. */
    val glow: Float,
    val bubbles: List<BurstBubble>,
    val ring: List<RingSparkle>,
    /** The streak badge's scale, 1 at rest. */
    val badgeScale: Float,
) {
    /** Over means nothing left to draw and nothing to lift — the state before a run and after one. */
    val isOver: Boolean get() = progress >= 1f

    companion object {
        /** How long the whole sequence takes — longer than the day's 1400, and the copy line's stay. */
        const val MILLIS = 2000

        /** Nothing: what [at] returns once the run is over. */
        val NONE = MilestoneFrame(1f, 0f, 0f, emptyList(), emptyList(), 1f)

        fun at(progress: Float): MilestoneFrame {
            val p = progress.coerceIn(0f, 1f)
            if (p >= 1f) return NONE
            return MilestoneFrame(
                progress = p,
                hop = HOP * piecewise(p, HOP_STOPS),
                glow = GLOW * piecewise(p, GLOW_STOPS),
                bubbles = burstAt(MilestoneLanes, p, BURST_ALPHA),
                ring = ringAt(p),
                badgeScale = badgeScaleAt(p),
            )
        }

        /** The badge's scale alone, for the rows: the frame's other parts are the tank's. */
        fun badgeScaleAt(progress: Float): Float = if (progress >= 1f) 1f else piecewise(progress.coerceAtLeast(0f), BADGE_STOPS)

        private fun ringAt(p: Float): List<RingSparkle> {
            val alpha = piecewise(p, RING_ALPHA_STOPS)
            if (alpha <= 0f) return emptyList()
            val radius = piecewise(p, RING_RADIUS_STOPS)
            val scale = piecewise(p, RING_SCALE_STOPS)
            val drift = piecewise(p, RING_DRIFT_STOPS)
            return List(RING_STARS) { i -> RingSparkle(i, i * 360f / RING_STARS + drift, radius, scale, alpha) }
        }

        /** The Milestone celebration page's defaults, approved 2026-08-29. */
        private const val HOP = 18f
        private const val GLOW = 0.30f
        private const val BURST_ALPHA = 0.9f
        private const val RING_STARS = 8

        private val HOP_STOPS =
            listOf(0f to 0f, 0.14f to 1f, 0.28f to 0f, 0.38f to 0.8f, 0.52f to 0f, 0.60f to 0.25f, 0.70f to 0f, 1f to 0f)
        private val GLOW_STOPS = listOf(0f to 0f, 0.16f to 1f, 0.50f to 0.4f, 1f to 0f)
        private val RING_ALPHA_STOPS = listOf(0f to 0f, 0.30f to 0f, 0.42f to 1f, 0.85f to 0f, 1f to 0f)
        private val RING_RADIUS_STOPS = listOf(0f to 40f, 0.30f to 40f, 0.42f to 61f, 0.85f to 110f, 1f to 110f)
        private val RING_SCALE_STOPS = listOf(0f to 0.6f, 0.30f to 0.6f, 0.42f to 1f, 0.85f to 1.2f, 1f to 1.2f)
        private val RING_DRIFT_STOPS = listOf(0f to 0f, 0.30f to 0f, 0.42f to 12f, 0.85f to 40f, 1f to 40f)
        private val BADGE_STOPS = listOf(0f to 1f, 0.10f to 1.28f, 0.24f to 1f, 0.40f to 1.12f, 0.55f to 1f, 1f to 1f)
    }
}

/**
 * A milestone celebration in flight, as state, obtained with
 * [rememberMilestone]. [frame] is derived from the progress once per change
 * and read inside draw and layout lambdas, so a run redraws and does not
 * recompose; [isOver] changes only at its two ends; [current] and [pulsing]
 * change twice per run and are what the copy line and the rows read in
 * composition.
 *
 * Unlike [CelebrationState], whose run lives inside the effect keyed on the
 * mood, this one runs on a scope of its own: the effect that feeds it is keyed
 * on the rows, which change on every tick, and a run inside that effect would
 * be cut short by the user ticking an unrelated habit half a second later.
 */
@Stable
internal class MilestoneState(private val scope: CoroutineScope) {
    private val progress = Animatable(1f)
    private val frameState = derivedStateOf { MilestoneFrame.at(progress.value) }
    private var seen: Map<HabitId, StreakUi> = emptyMap()
    private var run: Job? = null

    /** Every row this run is for, with the rung each reached; trimmed as rows fall back below theirs. */
    private var live: List<Milestone> = emptyList()

    /** The milestone the tank names, null when none — set with animations off too, for the copy line. */
    var current: Milestone? by mutableStateOf(null)
        private set

    /** Every row that crossed a rung this run and still holds it, not only the one the tank names; their badges pulse. */
    var pulsing: Set<HabitId> by mutableStateOf(emptySet())
        private set

    val frame: MilestoneFrame get() = frameState.value
    val badgeScale: Float get() = MilestoneFrame.badgeScaleAt(progress.value)
    val isOver: State<Boolean> = derivedStateOf { progress.value >= 1f }

    /**
     * Sees [rows], called from an effect keyed on them. Fires for whichever rows
     * [milestonesIn] says just crossed a rung — one sequence, for the highest
     * rung (ties go to the row nearer the top), every crossing row pulsing.
     * Otherwise it keeps the run honest: a pulsing row unticked or archived
     * loses its pill, and if that row is the one the tank names the run is cut,
     * so nothing freezes mid-air. Ticking an unrelated habit leaves a run alone.
     */
    fun see(rows: List<HabitRowUi>, animationsOn: Boolean) {
        val fired = milestonesIn(rows, seen)
        seen = rows.associate { it.id to it.streak }
        if (fired.isEmpty()) {
            trim(rows)
            return
        }
        val chosen = fired.maxWith(compareBy<Milestone> { it.rank }.thenBy { -rows.indexOfFirst { r -> r.id == it.habitId } })
        val previous = run
        run = scope.launch {
            // Joined, not merely cancelled: the old run's teardown clears the
            // fields this one is about to set, and must land first.
            previous?.cancelAndJoin()
            try {
                live = fired
                current = chosen
                pulsing = fired.map { it.habitId }.toSet()
                if (animationsOn) {
                    progress.snapTo(0f)
                    progress.animateTo(1f, tween(MilestoneFrame.MILLIS, easing = LinearEasing))
                } else {
                    // A line is text, not motion: it stays for the same two seconds the
                    // run would have taken, so on and off never disagree about how long
                    // a milestone is, and the badge's highlight rides the same window.
                    progress.snapTo(1f)
                    delay(MilestoneFrame.MILLIS.toLong())
                }
            } finally {
                // Cancellation is the teardown too, and the settle must not be skipped by it.
                withContext(NonCancellable) { progress.snapTo(1f) }
                live = emptyList()
                current = null
                pulsing = emptySet()
            }
        }
    }

    private fun trim(rows: List<HabitRowUi>) {
        val named = current ?: return
        val kept = live.filter { m -> (rows.firstOrNull { it.id == m.habitId }?.streak?.liveCount() ?: 0) >= m.count }
        if (kept.none { it.habitId == named.habitId }) {
            run?.cancel()
        } else if (kept.size != live.size) {
            live = kept
            pulsing = kept.map { it.habitId }.toSet()
        }
    }
}

/**
 * The streak-milestone celebration, following [rows] (docs/ux/momo.md §6).
 *
 * Remembered **outside** the list that scrolls the tank, for the reason
 * [rememberCelebration] gives: the edge is each row's streak against the one
 * seen before for that habit, and that memory has to outlive the mascot item.
 * Detected in composition and not on the state flow for the same reason as
 * well — the flow re-emits on every return to the screen, and the composition
 * sees one change. A cold start on day seven, a rotation and a return from the
 * background never fire it; an untick and re-tick replays it, which is the
 * user's own doing. Keyed on the gate like [rememberCelebration]: a re-run with
 * the same rows is a no-op because `seen` already matches.
 */
@Composable
internal fun rememberMilestone(rows: List<HabitRowUi>, animationsOn: Boolean): MilestoneState {
    val scope = rememberCoroutineScope()
    val state = remember { MilestoneState(scope) }
    LaunchedEffect(rows, animationsOn) { state.see(rows, animationsOn) }
    return state
}

/** Draws the whole milestone at [frame] — glow, burst, ring — over this scope; the hop is the caller's, applied to Momo. */
internal fun DrawScope.drawMilestone(frame: MilestoneFrame, colour: Color) {
    drawMilestoneGlow(frame, colour)
    drawMilestoneBurst(frame, colour)
    drawMilestoneRing(frame)
}

/** The brightening of the water: a white wash over the whole tank, behind Momo. */
internal fun DrawScope.drawMilestoneGlow(frame: MilestoneFrame, colour: Color) {
    if (frame.glow > 0f) drawRect(colour, alpha = frame.glow)
}

/** The burst, in dp: twenty-two lanes across the middle 200 dp, each on its own delay, rising 190. */
internal fun DrawScope.drawMilestoneBurst(frame: MilestoneFrame, colour: Color) =
    drawBurst(MilestoneLanes, frame.bubbles, BURST_RISE, colour)

/**
 * The ring: the thriving face's star, eight times, orbiting outward from the
 * centre of the tank in [MomoPalette.Sparkle]. Each star is placed as the
 * canvas does it — rotate by its angle, then move out by the radius — and
 * the one unit star is drawn there at its scale, so no path is built per frame.
 */
internal fun DrawScope.drawMilestoneRing(frame: MilestoneFrame) {
    val dp = 1.dp.toPx()
    frame.ring.forEach { s ->
        val theta = Math.toRadians(s.angleDegrees.toDouble())
        val cx = size.width / 2f + (sin(theta) * s.radius * dp).toFloat()
        val cy = size.height / 2f - (cos(theta) * s.radius * dp).toFloat()
        translate(cx, cy) {
            scale(s.scale * dp, s.scale * dp, pivot = Offset.Zero) {
                drawPath(UnitStar, MomoPalette.Sparkle, alpha = s.alpha)
            }
        }
    }
}

/**
 * Twenty-two lanes, spread deterministically across the middle 200 dp of the
 * canvas's 362 dp tank and staggered over the first 45 % of the run — the
 * page's `(i * 37) % 200` and `(i * 53) % 45` — so no randomness reaches a frame.
 */
private val MilestoneLanes = List(22) { i ->
    BurstLane(across = (81f + (i * 37) % 200) / 362f, size = 3f + (i % 5) * 1.5f, delay = ((i * 53) % 45) / 100f)
}

/** The milestone's bubbles rise this far, dp — twenty more than the day's. */
private const val BURST_RISE = 190f

/** The canvas's 18 px star (`l 2.2,5.8 …`) at scale 1 dp, centred on the origin, built once. */
private const val STAR_A = 2.2f
private const val STAR_B = 5.8f
private val UnitStar = sparkleStar(Offset(0f, -(STAR_A + STAR_B)), STAR_A, STAR_B)
