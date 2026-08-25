package com.gawi.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.ui.component.Momo
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
internal fun MascotPanel(mood: Mood, remaining: Int, total: Int, modifier: Modifier = Modifier) {
    val copy = stringResource(moodCopy(mood, total))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GawiSpacing.Row)
            // One node for the picture and its caption, so TalkBack reads the
            // mood once — the copy is the description of the face, and a
            // description on the tank as well would read it twice, while none
            // at all leaves a nameless image beside a line that names it: the
            // widget's lesson (docs/ux/widget.md §5) from either side.
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Tank(mood)
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
        if (total > 0) {
            Text(
                text = if (remaining == 0) {
                    stringResource(R.string.today_remaining_none)
                } else {
                    // Counted by today-view §4's rule rather than by unticked
                    // rows, so a weekly habit with its target still reachable
                    // is not counted here.
                    stringResource(R.string.today_remaining, remaining, total)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The habitat: a teal water gradient in the theme's own roles, drained to
 * the neutral containers while Momo regenerates (momo.md §3). Fixed height,
 * not a floor — the character has a size and the copy below it is what grows
 * with the font scale.
 */
@Composable
private fun Tank(mood: Mood, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val water = if (mood == Mood.REGENERATING) {
        listOf(scheme.surfaceContainerHighest, scheme.surfaceContainerHigh)
    } else {
        listOf(scheme.primaryContainer, scheme.primaryFixedDim)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TankHeight)
            .clip(RoundedCornerShape(TankCorner))
            .background(Brush.linearGradient(water)),
        contentAlignment = Alignment.Center,
    ) {
        Momo(mood, Modifier.fillMaxSize().padding(GawiSpacing.Row))
    }
}

/** The copy for a mood — one line each, all four (today-view §4). */
private fun moodCopy(mood: Mood, total: Int): Int = when {
    // The empty case gets its own line rather than sharing content's.
    // today-view §4's rule 0 makes a habitless first run `content`
    // deliberately, so the mood is right and "Momo is pottering about" would
    // still be the wrong thing to say to someone who has not added anything
    // yet.
    total == 0 -> R.string.today_mood_empty

    else -> when (mood) {
        Mood.THRIVING -> R.string.today_mood_happy
        Mood.CONTENT -> R.string.today_mood_neutral
        Mood.WORRIED -> R.string.today_mood_worried
        Mood.REGENERATING -> R.string.today_mood_regenerating
    }
}

/**
 * The redesign's tank, 250 by the phone's width, as the Momo Motion page drew
 * it and the user approved it (momo.md §4). Replaces the 96dp floor the copy
 * alone needed.
 */
private val TankHeight = 250.dp
private val TankCorner = 20.dp
