package com.gawi.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gawi.core.ui.streak.StreakUi
import com.gawi.core.ui.theme.GawiSpacing

/**
 * A row's streak, drawn by unit.
 *
 * docs/ux/today-view.md §5 says a daily streak and a weekly one must never be
 * styled as the same number. They differ here twice over — the week form
 * carries a `w` and a different colour role — so the distinction survives a
 * reader who cannot tell the two colours apart.
 */
@Composable
internal fun StreakBadge(streak: StreakUi, modifier: Modifier = Modifier) {
    when (streak) {
        // Nothing has ever run, so there is nothing to say. §5's rule about
        // never reading zero is about a live streak, which this is not.
        StreakUi.None -> Unit

        is StreakUi.Days -> Text(
            text = stringResource(R.string.today_streak_days, streak.count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )

        is StreakUi.Weeks -> Text(
            text = stringResource(R.string.today_streak_weeks, streak.count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = modifier,
        )

        is StreakUi.Broken -> BrokenStreak(streak, modifier)
    }
}

/** Zero, with what was lost kept beside it — §5's "was 4" and its cut thread. */
@Composable
private fun BrokenStreak(streak: StreakUi.Broken, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Line)) {
            Text(
                text = stringResource(R.string.today_streak_broken_glyph),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = stringResource(R.string.today_streak_broken),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            text = stringResource(
                if (streak.weekly) R.string.today_streak_was_weeks else R.string.today_streak_was_days,
                streak.previous,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
