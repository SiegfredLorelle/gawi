package com.gawi.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.ui.component.Momo
import com.gawi.core.ui.component.MomoPalette
import com.gawi.core.ui.component.rememberFrameClock
import com.gawi.core.ui.component.rememberMoodTransition
import com.gawi.core.ui.theme.GawiSpacing

/**
 * Momo's slot — docs/ux/today-view.md §3, with the character in it.
 *
 * today-view §3 promises one box, at one size, in one place. It held Phase 0's
 * copy line alone; since 2026-08-25 it holds the tank with Momo in it
 * (momo.md §4) above the same copy, which is now the description of the drawn
 * face rather than a stand-in for it. The tank is drawn here and not in `:core:ui`
 * because only Today is a habitat — the widget and the reminder get the
 * character on their own grounds (momo.md §4).
 *
 * `toMvp()` used to be called here and nowhere else; the fourth face made it
 * dead and it is gone. What is deferred is still §1's behaviour — the collapse
 * into an app-bar chip on scroll — not the slot.
 */
@Composable
internal fun MascotPanel(mascot: MascotUi, motion: TodayMotion, modifier: Modifier = Modifier) {
    // For the length of a milestone run the line is the milestone's, and its
    // changing is the announcement (momo.md §5): the node below merges the
    // picture and the caption and is a polite live region, so TalkBack reads
    // the new line once and the mood line once more when it returns, wherever
    // focus is. A line is text, not motion, so this swap happens with
    // animations off too — MilestoneState keeps `current` set for the same two
    // seconds either way.
    val milestone = motion.milestone.current
    val copy = if (milestone == null) {
        moodLine(mascot)
    } else {
        pluralStringResource(milestoneCopy(milestone), milestone.count, milestone.count)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GawiSpacing.Row)
            // One node for the picture and its caption, so TalkBack reads the
            // mood once — the copy is the description of the face, and a
            // description on the tank as well would read it twice, while none
            // at all leaves a nameless image beside a line that names it: the
            // widget's lesson (docs/ux/widget.md §5) from either side. A polite
            // live region, because the line changing is the whole announcement
            // (momo.md §5) and without one it would only be read when this node
            // already held focus — never, right after ticking a row. The cost is
            // one short sentence after every tick, the remaining count riding
            // along with the mood line.
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tank(mascot.mood, motion.animationsOn, motion.celebration, motion.milestone)
        Text(
            text = copy,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        // Nothing at all to say when there is nothing to do yet. "Nothing left
        // today" under an empty list reads as an achievement, which is the exact
        // reading today-view §4's rule 0 exists to prevent — the mood line
        // above already speaks for this state, and the empty copy below it
        // says the rest.
        if (mascot.total > 0) {
            Text(
                text = if (mascot.remaining == 0) {
                    stringResource(R.string.today_remaining_none)
                } else {
                    // Counted by today-view §4's rule rather than by unticked
                    // rows, so a weekly habit with its target still reachable
                    // is not counted here.
                    stringResource(R.string.today_remaining, mascot.remaining, mascot.total)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The habitat: teal water in the theme's own roles with the tank life behind
 * Momo (momo.md §4) — four weeds and four bubbles keeping the mood's tempo —
 * draining to the neutral containers while Momo regenerates, the weeds greying
 * and leaning as it does. Fixed height, not a floor — the character has a size
 * and the copy below it is what grows with the font scale.
 *
 * One animations gate, one [rememberMoodTransition] and one frame clock for
 * the water, the weeds and Momo, so nothing here can disagree about where a
 * mood change stands; the gate and the celebration are the screen's, passed
 * in, for the reasons [rememberCelebration] gives. Everything that moves is
 * read inside a draw or layout lambda, so a frame redraws two canvases and
 * recomposes nothing.
 */
@Composable
private fun Tank(
    mood: Mood,
    animationsOn: Boolean,
    celebration: CelebrationState,
    milestone: MilestoneState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberMoodTransition(mood, animationsOn)
    val seconds = rememberFrameClock(animationsOn)
    val full = listOf(scheme.primaryContainer, scheme.primaryFixedDim)
    val drained = listOf(scheme.surfaceContainerHighest, scheme.surfaceContainerHigh)
    val colours = HabitatColours(weed = scheme.primary, weedDrained = scheme.outline, bubble = MomoPalette.Highlight)
    // Built once per scheme, not per frame: a Brush caches its native shader
    // per instance, and outside the ~33 frames of a transition the water is
    // exactly one of these two. Only the change in between lerps the stops.
    val fullWater = remember(scheme) { Brush.linearGradient(full) }
    val drainedWater = remember(scheme) { Brush.linearGradient(drained) }
    val celebrationOver by celebration.isOver
    // The milestone owns the tank while it runs (momo.md §6): the last habit
    // of the day is often the one crossing a rung, and two hops summed with two
    // bursts would read as a glitch. The day-complete run finishes unseen
    // underneath — 1400 ms inside 2000 — so the tank is at the thriving rest
    // when the milestone ends.
    val milestoneOver by milestone.isOver
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TankHeight)
            .clip(RoundedCornerShape(TankCorner)),
        contentAlignment = Alignment.Center,
    ) {
        // Water and tank life together, behind Momo so a frond never crosses
        // the face; fillMaxSize for the reason Momo gives — a Canvas with no
        // size measures 0 x 0.
        Canvas(Modifier.fillMaxSize().testTag("habitat")) {
            val t = transition.current
            val s = seconds.value
            val d = t.drained
            when {
                d <= 0f -> drawRect(fullWater)
                d >= 1f -> drawRect(drainedWater)
                else -> drawRect(Brush.linearGradient(full.zip(drained) { a, b -> lerp(a, b, d) }))
            }
            drawHabitat(HabitatFrame.between(HabitatFrame.at(t.from, s), HabitatFrame.at(t.to, s), t.progress), colours)
        }
        // The glow goes between the water and Momo — it brightens the water,
        // not the face — but the burst goes in front: its lanes rise straight
        // up behind the body, and drawn under Momo the bubbles simply vanish.
        // Checked on the emulator; the design board's layering hid them too.
        if (!milestoneOver) {
            Canvas(Modifier.fillMaxSize().testTag("milestone")) { drawMilestoneGlow(milestone.frame, MomoPalette.Highlight) }
        } else if (!celebrationOver) {
            Canvas(Modifier.fillMaxSize().testTag("celebration")) { drawCelebrationGlow(celebration.frame, MomoPalette.Highlight) }
        }
        Momo(
            transition,
            seconds,
            Modifier
                .fillMaxSize()
                .padding(GawiSpacing.Row)
                .offset {
                    val hop = if (milestoneOver) celebration.frame.hop else milestone.frame.hop
                    IntOffset(0, -hop.dp.roundToPx())
                },
        )
        if (!milestoneOver) {
            Canvas(Modifier.fillMaxSize()) {
                val frame = milestone.frame
                drawMilestoneBurst(frame, MomoPalette.Highlight)
                drawMilestoneRing(frame)
            }
        } else if (!celebrationOver) {
            Canvas(Modifier.fillMaxSize()) { drawCelebrationBurst(celebration.frame, MomoPalette.Highlight) }
        }
    }
}

/** The milestone line, by the rung's unit — plurals, because it counts a noun. */
private fun milestoneCopy(milestone: Milestone): Int =
    if (milestone.weekly) R.plurals.today_milestone_weeks else R.plurals.today_milestone_days

/**
 * The panel's line — one per mood (today-view §4), and for regenerating the one
 * that names the habit.
 *
 * The name is [MascotUi.regeneratingHabit] rather than anything worked out here:
 * which habit a break belongs to is a decision, and `TodayUiMapper` is where
 * this module's decisions are asserted without a device. The mood is not
 * re-tested for it either — the mapper sets the field only for
 * [Mood.REGENERATING], so a non-null name *is* that state.
 */
@Composable
private fun moodLine(mascot: MascotUi): String {
    val named = mascot.regeneratingHabit
    return when {
        // The empty case gets its own line rather than sharing content's.
        // today-view §4's rule 0 makes a habitless first run `content`
        // deliberately, so the mood is right and "Momo is pottering about" would
        // still be the wrong thing to say to someone who has not added anything
        // yet.
        mascot.total == 0 -> stringResource(R.string.today_mood_empty)

        // Mood.REGENERATING's whole promise: the copy names the habit and offers
        // the repair, and it never scolds. The unnamed line below is the
        // fallback for a break whose row the screen no longer holds, not a
        // second phrasing anyone picks.
        named != null -> stringResource(R.string.today_mood_regenerating_named, named)

        else -> stringResource(moodCopy(mascot.mood))
    }
}

/** The unnamed line for a mood — one each, all four (today-view §4). */
private fun moodCopy(mood: Mood): Int = when (mood) {
    Mood.THRIVING -> R.string.today_mood_happy
    Mood.CONTENT -> R.string.today_mood_neutral
    Mood.WORRIED -> R.string.today_mood_worried
    Mood.REGENERATING -> R.string.today_mood_regenerating
}

/**
 * The redesign's tank, 250 by the phone's width, as the Momo Motion page drew
 * it and the user approved it (momo.md §4). Replaces the 96dp floor the copy
 * alone needed.
 */
private val TankHeight = 250.dp
private val TankCorner = 20.dp
