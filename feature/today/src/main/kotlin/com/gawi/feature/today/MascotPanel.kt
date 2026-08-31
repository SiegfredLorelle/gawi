package com.gawi.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.contentDescription
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
        remainingLine(mascot)?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * today-view §1's app-bar chip: the mood and the remaining count, once the tank
 * has scrolled away.
 *
 * §1 accepted that Momo leaves the screen on a long list and named this chip as
 * the mitigation, "deliberately small". Small is what it is — a face and a short
 * count, replacing the title rather than joining it, because at a large font
 * scale a title, a chip and three action icons do not fit across one bar.
 *
 * The face is drawn with animations off whatever the system setting says. At
 * [ChipFace] the idle motion is invisible, and a frame clock running behind a
 * scrolling list is the one cost this chip could impose and has no need to.
 *
 * **One semantics node, and deliberately not a live region.** [MascotPanel]'s
 * copy is a polite one already and is only scrolled off rather than gone, so a
 * second here would have TalkBack read the mood twice after every tick.
 *
 * The count is its own shorter wording rather than the panel's sentence: the bar
 * has no room for "3 of 8 left today" beside three action icons at a large font
 * scale, and the mood line merged in above it supplies the context that the
 * short form drops.
 *
 * One consequence for tests: this draws a second [Momo], carrying the same
 * `momo:<mood>` tag the tank does, so a tag query made while the chip is up
 * matches two nodes rather than one.
 */
@Composable
internal fun TodayChip(mascot: MascotUi, modifier: Modifier = Modifier) {
    val line = moodLine(mascot)
    Row(
        modifier = modifier
            .testTag(CHIP_TAG)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        // The face is described and the count is left to speak for itself —
        // the widget's lesson (docs/ux/widget.md §5) from both sides at once: a
        // nameless picture beside a line that names it, or the same words twice.
        Box(Modifier.size(ChipFace).semantics { contentDescription = line }) {
            // Sized by the Box: a Canvas with no size of its own measures 0 x 0,
            // and a test that only asked whether the node existed would pass on
            // an empty chip.
            Momo(mascot.mood, animated = false)
        }
        Text(
            text = if (mascot.remaining == 0) {
                stringResource(R.string.today_chip_remaining_none)
            } else {
                stringResource(R.string.today_chip_remaining, mascot.remaining)
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
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

/**
 * The remaining count as a sentence, or null when there is nothing to count.
 *
 * Null rather than an empty string for the reason the panel used to spell out
 * inline: "Nothing left today" under an empty list reads as an achievement,
 * which is exactly the reading today-view §4's rule 0 exists to prevent. The
 * mood line already speaks for that state.
 *
 * Shared with [TodayChip] so the chip speaks the same count the panel shows,
 * counted by today-view §4's rule rather than by unticked rows — a weekly habit
 * with its target still reachable is not in it.
 */
@Composable
private fun remainingLine(mascot: MascotUi): String? = when {
    mascot.total == 0 -> null
    mascot.remaining == 0 -> stringResource(R.string.today_remaining_none)
    else -> stringResource(R.string.today_remaining, mascot.remaining, mascot.total)
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

/** The chip's face. Small enough to sit on one line of the app bar beside a count. */
private val ChipFace = 28.dp

internal const val CHIP_TAG = "today-chip"
