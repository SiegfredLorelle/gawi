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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * dead and it is gone. §1's behaviour — the collapse into an app-bar chip on
 * scroll — is built, and is [TodayChip] below rather than anything here; the
 * slot is unchanged by it, which is why that is the whole of the news.
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
 * today-view §1's app-bar chip: the mood and the remaining count once the tank
 * has scrolled away, and the milestone line in the count's place while one runs.
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
 * **One semantics node, and deliberately not a live region — but not for the
 * reason it first appeared.** [MascotPanel] is a `LazyColumn` item, so once the
 * chip is up the panel is *disposed*, not merely off-screen: `HabitList` says so
 * where it hoists the motion state, and `chip_carriesTheMoodAndTheRemainingCount`
 * proves it, since its `assertDoesNotExist` could not pass on a panel that was
 * still composed. The two nodes never coexist, so there was never a double read
 * to avoid.
 *
 * What that leaves is a silence, and it is chosen rather than inherited: a tick
 * made while the chip is up changes this description, and a plain description
 * change on a non-live node is not announced. Three reasons to keep it that way.
 * The row's own checkbox announces its state change, so the tick is not
 * feedback-free. A live region fires when its node *appears* as well as when it
 * changes, so every scroll past the tank would speak the whole sentence — a poor
 * trade for a surface §1 calls "deliberately small". And no emulator image here
 * has TalkBack, so a live region would be shipped unheard. `chip_isNotALiveRegion`
 * pins the choice; today-view §1 keeps it open for a device that can verify it.
 *
 * **What is shown and what is spoken are different strings, and the description
 * has to carry both facts.** The bar has no room for "3 of 8 left today" beside
 * three action icons, so the label is the short form — and the description was
 * built on the premise that a node with a `contentDescription` has its `text`
 * ignored by TalkBack, so a description of the mood alone would silently drop
 * the count from the announcement and leave the chip saying less than the panel
 * it replaced. It is built from the panel's own mood line and count for that
 * reason, and `chip_speaksTheCountItShows` is the assertion that keeps it. The
 * premise was half right: on a device (TalkBack 17, 2026-09-02) the label is
 * read *after* the description, so the chip said its count twice — see
 * docs/ux/today-view.md §1 and docs/running.md §4. Since 2026-09-02 the Row
 * clears its subtree instead of merging it, so the label is drawn and not read;
 * `chip_doesNotAlsoReadItsLabel` pins that the chip's node carries no text and
 * the label lives only in the unmerged tree. Reading the label back still
 * proves nothing about what is spoken. `testTag` stays ahead of the clearing
 * modifier because it is semantics too and would otherwise be wiped with the
 * children.
 *
 * **It carries the milestone line, and the line replaces the count rather than
 * joining it.** The panel swaps its mood line for the milestone's for the length
 * of a celebration and momo.md §6 makes the swap the announcement; this does the
 * same to the one string it has room for. Until 2026-09-01 it could not: the
 * milestone lived on `TodayMotion`, which `HabitList` owned one level *below*
 * the app bar, so a rung crossed while scrolled down was neither drawn nor
 * spoken. `rememberTodayMotion` now sits above the `Scaffold` for this reason
 * and says so. Replacing rather than joining because the width this bar does
 * not have is the same width the mood line was already denied — and because the
 * panel's own treatment of the line is a swap, not an addition.
 *
 * The **spoken** half of that swap is not the drawn one, and here the two are
 * different *strings* rather than the same one in different slots. The label is
 * [chipMilestoneCopy]'s short form — "7 days!" — because the panel's sentence,
 * drawn here, truncated to "7 days in a row. Mom…" on a 720 dp screen at font
 * scale 1.0. The description keeps the panel's long form, in the *mood line's*
 * place, with the count still riding along; a description narrowed to the short
 * label would drop both the sentence and the count from the announcement, which
 * is the defect the paragraph above records, arrived at from the other side.
 *
 * That truncation is worth dwelling on, because **no test could see it**: a
 * Compose assertion that a string is present passes on a node drawing it
 * clipped, so `chip_carriesTheMilestoneLine` was green on a chip nobody could
 * read. It took ticking a rung on a device. What a test *can* hold is the
 * negative, and that test now also asserts the panel's sentence is absent from
 * the bar.
 *
 * What is still open in today-view §6 is the *announcing*, not the carrying: a
 * description change on a non-live node is not spoken, so a rung crossed while
 * the chip is up is now drawn but still silent. That is the live-region question
 * above, and it needs a device with TalkBack rather than another decision here.
 *
 * One note for tests: this draws its own [Momo], carrying the same `momo:<mood>`
 * tag the tank does. A query while the chip is up still matches exactly one
 * node, because the tank's panel is disposed by then — an earlier version of
 * this line claimed two, on the same mistake the live-region paragraph above
 * corrects.
 */
@Composable
internal fun TodayChip(mascot: MascotUi, milestone: Milestone?, modifier: Modifier = Modifier) {
    // Two strings for one milestone, drawn and spoken, for the same reason the
    // count has two: the label is what fits the bar, the description is what is
    // read. chipMilestoneCopy says what the device found out about sharing one.
    val milestoneLabel = milestone?.let { pluralStringResource(chipMilestoneCopy(it), it.count, it.count) }
    val milestoneSpoken = milestone?.let { pluralStringResource(milestoneCopy(it), it.count, it.count) }
    // The panel's own first line and its count, joined: the face has no
    // description of its own and the label is cleared from the tree below, so
    // this one string is the whole announcement. The milestone line takes the mood line's place
    // here, not the count's — the drawn label gives up its count for the run
    // because it has room for one string; the description has room for both and
    // dropping the count from what is spoken is the very defect above.
    val spoken = listOfNotNull(milestoneSpoken ?: moodLine(mascot), remainingLine(mascot)).joinToString(" ")
    Row(
        modifier = modifier
            // The tag first: it is semantics too, and clearAndSetSemantics wipes
            // every semantics modifier after it in the chain along with the
            // children.
            .testTag(CHIP_TAG)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        // Sized by the Box: a Canvas with no size of its own measures 0 x 0,
        // and a test that only asked whether the node existed would pass on
        // an empty chip.
        Box(Modifier.size(ChipFace)) { Momo(mascot.mood, animated = false) }
        Text(
            // The milestone owns the label while it runs, the way it owns the
            // tank and the panel's line. MilestoneState keeps `current` set for
            // the same two seconds with animations off, so the swap happens
            // either way — a line is text, not motion.
            text = milestoneLabel ?: if (mascot.remaining == 0) {
                stringResource(R.string.today_chip_remaining_none)
            } else {
                stringResource(R.string.today_chip_remaining, mascot.remaining)
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            // Ellipsis rather than the default clip. today-view §1's own
            // measurement puts the chip's first appearance at nine habits, so a
            // two-digit count is the ordinary case here, not the edge one, and
            // clipping would cut it mid-glyph.
            overflow = TextOverflow.Ellipsis,
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
 * The same milestone in the chip's register, the way [R.string.today_chip_remaining]
 * is the count's: the bar has room for one string beside three action icons.
 *
 * Not an abbreviation for its own sake. The panel's sentence, drawn here,
 * truncated to "7 days in a row. Mom…" on a 720 dp screen at font scale 1.0 —
 * found by ticking a rung on a device, and invisible to every test, because a
 * Compose assertion that the text is present passes on a node drawing it
 * clipped. The description keeps the long form; only the drawn label shortens.
 */
private fun chipMilestoneCopy(milestone: Milestone): Int =
    if (milestone.weekly) R.plurals.today_chip_milestone_weeks else R.plurals.today_chip_milestone_days

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
        // the repair, and it never scolds. The unnamed line below is what shows
        // when the mood is regenerating and no habit can be named — see
        // Mascot.recentlyBrokenHabits, which will not name one already ticked.
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
