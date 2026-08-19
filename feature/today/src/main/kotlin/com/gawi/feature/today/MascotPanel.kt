package com.gawi.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.mascot.MvpMood
import com.gawi.core.domain.mascot.toMvp
import com.gawi.core.ui.theme.GawiSpacing

/**
 * Momo's slot — docs/ux/today-view.md §3, at MVP fidelity.
 *
 * §3 promises one box, at one size, in one place, holding the Phase 0 indicator
 * now and the Rive character later. This is that box with its Phase 0 contents:
 * a line of copy chosen by the mood, and the remaining count. What is deferred
 * is §1's behaviour — the collapse into an app-bar chip on scroll — not the
 * slot, so adding it later moves this composable rather than rewriting it.
 *
 * `toMvp()` is called here and nowhere else, and nothing outside this file
 * reads the mood. That is what makes swapping in the Rive state machine delete
 * one `when` and touch nothing else on the screen, which is §3's contract
 * stated as a call-site rule.
 */
@Composable
internal fun MascotPanel(mood: Mood, remaining: Int, total: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(PanelHeight)
            .padding(horizontal = GawiSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(moodCopy(mood, total)),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (remaining == 0) {
                stringResource(R.string.today_remaining_none)
            } else {
                // Counted by §4's rule rather than by unticked rows, so a weekly
                // habit with its target still reachable is not counted here.
                stringResource(R.string.today_remaining, remaining, total)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The copy for a mood, on Phase 0's three faces.
 *
 * The empty case gets its own line rather than sharing neutral's. §4's rule 0
 * makes a habitless first run `content` deliberately, so the mood is right and
 * "Momo is pottering about" would still be the wrong thing to say to someone
 * who has not added anything yet.
 */
private fun moodCopy(mood: Mood, total: Int): Int = when {
    total == 0 -> R.string.today_mood_empty

    else -> when (mood.toMvp()) {
        MvpMood.HAPPY -> R.string.today_mood_happy
        MvpMood.NEUTRAL -> R.string.today_mood_neutral
        MvpMood.WORRIED -> R.string.today_mood_worried
    }
}

/**
 * §3's fixed height. Fixed because the slot's whole promise is that swapping
 * the placeholder for the character moves nothing around it.
 */
private val PanelHeight = 96.dp
