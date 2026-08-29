package com.gawi.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.gawi.core.ui.theme.GawiSpacing

/**
 * How the months of the period went: active days per month, the headline's own
 * number bucketed (docs/ux/insights.md §9).
 *
 * The same shape as [RateCard] — a line over a row of labelled columns, where
 * the labels are the chart's table view — and it shares that card's
 * [Sparkline] for the same reason it shares its reasoning: a touch surface has
 * no hover, so the label is the only path to a value.
 *
 * **Twelve columns cannot carry twelve month names on a phone.** Under a year
 * the label is the month's initial, and J, M and A each name two months — the
 * weekday-letters problem the history grid met (§8.4), solved the same way:
 * the letters are hidden from assistive technology and every column announces
 * itself in full, *"March, 15 active days"*. Under three columns the short
 * name is used and nothing is hidden.
 */
@Composable
internal fun TrendCard(points: List<TrendPointUi>, modifier: Modifier = Modifier) {
    val initialsOnly = points.size > SHORT_NAMES_FIT
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line)) {
        Text(
            text = stringResource(R.string.insights_trend_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.insights_trend_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Sparkline(
            fills = points.map { it.fill },
            modifier = Modifier
                .fillMaxWidth()
                .height(SPARKLINE_HEIGHT)
                // The line says nothing a reader cannot get from the labels
                // below it, and "a line went up" is not a fact a screen
                // reader can act on.
                .clearAndSetSemantics { },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                val monthName = stringResource(point.monthName)
                val spoken = stringResource(
                    R.string.insights_trend_point,
                    monthName,
                    pluralStringResource(R.plurals.insights_active_days, point.activeDays, point.activeDays),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // One stop per column, saying the month in full whatever
                        // the label under it shows.
                        .semantics(mergeDescendants = true) { contentDescription = spoken },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = point.activeDays.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (initialsOnly) monthName.take(1) else monthName.take(SHORT_NAME_LENGTH),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** A quarter's three columns take a short name; anything wider takes an initial. */
private const val SHORT_NAMES_FIT = 3

/** "Sep", not "September": the rate card's five labels set the width a column has. */
private const val SHORT_NAME_LENGTH = 3
