package com.gawi.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * While the row's streak is being celebrated (momo.md §6) — [pulse] given —
 * the number sits on a pill of its own container role, so the distinction
 * holds there too, and swells by what [pulse] returns; with animations off
 * that is a constant 1 and the pill alone is the highlight, for the two
 * seconds the copy line is swapped, so the row and the line always agree.
 * The pill is painted behind the number rather than laid out around it, so
 * the badge measures the same whether celebrating or not and neither the
 * number nor the title column beside it ever shifts; [pulse] is read inside
 * a layer lambda, so a frame redraws and recomposes nothing. A broken or
 * absent streak cannot be at a rung and ignores it.
 */
@Composable
internal fun StreakBadge(streak: StreakUi, modifier: Modifier = Modifier, pulse: (() -> Float)? = null) {
    when (streak) {
        // Nothing has ever run, so there is nothing to say. §5's rule about
        // never reading zero is about a live streak, which this is not.
        StreakUi.None -> Unit

        is StreakUi.Days -> Text(
            text = stringResource(R.string.today_streak_days, streak.count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier.celebrated(MaterialTheme.colorScheme.primaryContainer, pulse),
        )

        is StreakUi.Weeks -> Text(
            text = stringResource(R.string.today_streak_weeks, streak.count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = modifier.celebrated(MaterialTheme.colorScheme.tertiaryContainer, pulse),
        )

        is StreakUi.Broken -> BrokenStreak(streak, modifier)
    }
}

/** The swell and the pill behind the number, or nothing at all when the row is not celebrating. */
private fun Modifier.celebrated(pill: Color, pulse: (() -> Float)?): Modifier {
    if (pulse == null) return this
    return graphicsLayer {
        val s = pulse()
        scaleX = s
        scaleY = s
    }
        .testTag("milestone-badge")
        .drawBehind {
            val insetX = PillInset.toPx()
            val insetY = GawiSpacing.Line.toPx()
            val pillSize = Size(size.width + 2 * insetX, size.height + 2 * insetY)
            drawRoundRect(pill, Offset(-insetX, -insetY), pillSize, CornerRadius(pillSize.height / 2f))
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

/** The pill's horizontal outset beyond the number — the canvas's 8; the vertical one is the theme's Line. */
private val PillInset = 8.dp
