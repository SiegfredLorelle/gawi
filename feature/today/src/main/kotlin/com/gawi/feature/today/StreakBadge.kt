package com.gawi.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gawi.core.ui.streak.StreakUi
import com.gawi.core.ui.theme.GawiSpacing

/**
 * A row's streak, drawn by unit.
 *
 * docs/ux/today-view.md §5 says a daily streak and a weekly one must never be
 * styled as the same number. They differ here twice over — the week form
 * carries a `w` and a different colour role — so the distinction survives a
 * reader who cannot tell the two colours apart.
 *
 * While the row's streak is being celebrated (momo.md §6) the number sits on
 * a pill of its own container role — the distinction holds there too — and,
 * when [pulse] is given, swells with the tank's sequence. The pill is what
 * animations off keeps: [celebrating] alone is a static highlight for the two
 * seconds the copy line is swapped, so the row and the line always agree.
 * [pulse] is read inside a layer lambda, so a frame redraws and recomposes
 * nothing. A broken or absent streak cannot be at a rung and ignores both.
 */
@Composable
internal fun StreakBadge(streak: StreakUi, modifier: Modifier = Modifier, celebrating: Boolean = false, pulse: (() -> Float)? = null) {
    when (streak) {
        // Nothing has ever run, so there is nothing to say. §5's rule about
        // never reading zero is about a live streak, which this is not.
        StreakUi.None -> Unit

        is StreakUi.Days -> Text(
            text = stringResource(R.string.today_streak_days, streak.count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.celebrated(celebrating, MaterialTheme.colorScheme.primaryContainer, pulse),
        )

        is StreakUi.Weeks -> Text(
            text = stringResource(R.string.today_streak_weeks, streak.count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = modifier.celebrated(celebrating, MaterialTheme.colorScheme.tertiaryContainer, pulse),
        )

        is StreakUi.Broken -> BrokenStreak(streak, modifier)
    }
}

/** The pill and the swell, or nothing at all when the row is not celebrating. */
private fun Modifier.celebrated(celebrating: Boolean, pill: Color, pulse: (() -> Float)?): Modifier {
    if (!celebrating) return this
    val scaled = if (pulse == null) {
        this
    } else {
        graphicsLayer {
            val s = pulse()
            scaleX = s
            scaleY = s
        }
    }
    return scaled
        .testTag("milestone-badge")
        .background(pill, CircleShape)
        .padding(horizontal = PillInset, vertical = GawiSpacing.Line)
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

/** The pill's horizontal inset — the canvas's 8; the vertical one is the theme's Line. */
private val PillInset = 8.dp
