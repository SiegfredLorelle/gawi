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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.gawi.core.ui.streak.StreakUi
import com.gawi.core.ui.streak.spokenStreak
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
 *
 * **What it says is not what it draws.** "3" and "3w" are silent about what
 * they count, and TalkBack 17 read them as a bare *"1"*, *"7"* and *"1 w"*
 * (docs/running.md §4, 2026-09-02), so the badge speaks [spokenStreak]'s words
 * — *"3 days in a row"*, *"1 week in a row"*, *"Streak broken, was 12 days"* —
 * the same words habit detail speaks, from `:core:ui`. `clearAndSetSemantics`
 * rather than `semantics`, because a leaf with both a text and a description is
 * read twice on that TalkBack; and it comes *after* [celebrated], because a
 * clearing modifier discards every semantics modifier later in the chain, and
 * the milestone tag is one.
 */
@Composable
internal fun StreakBadge(streak: StreakUi, modifier: Modifier = Modifier, pulse: (() -> Float)? = null) {
    val spoken = spokenStreak(streak)
    when (streak) {
        // Nothing has ever run, so there is nothing to say. §5's rule about
        // never reading zero is about a live streak, which this is not.
        StreakUi.None -> Unit

        is StreakUi.Days -> LiveStreak(
            drawn = stringResource(R.string.today_streak_days, streak.count),
            spoken = checkNotNull(spoken),
            weekly = false,
            modifier = modifier,
            pulse = pulse,
        )

        is StreakUi.Weeks -> LiveStreak(
            drawn = stringResource(R.string.today_streak_weeks, streak.count),
            spoken = checkNotNull(spoken),
            weekly = true,
            modifier = modifier,
            pulse = pulse,
        )

        // On the outer Column, so its three texts are descendants and go with
        // the clearing.
        is StreakUi.Broken -> BrokenStreak(streak, modifier.clearAndSetSemantics { contentDescription = checkNotNull(spoken) })
    }
}

/**
 * A live streak's number in its unit's colour role, on its pill while
 * celebrating, spoken in words. One composable for days and weeks so the
 * order that matters — [celebrated] *before* the clearing modifier, or the
 * milestone tag goes with the text — is written once.
 */
@Composable
private fun LiveStreak(drawn: String, spoken: String, weekly: Boolean, modifier: Modifier, pulse: (() -> Float)?) {
    Text(
        text = drawn,
        style = MaterialTheme.typography.labelLarge,
        color = if (weekly) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        modifier = modifier
            .celebrated(if (weekly) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer, pulse)
            .clearAndSetSemantics { contentDescription = spoken },
    )
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
