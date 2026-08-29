package com.gawi.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * the label is the month's initial — a resource, not the name's first character,
 * for the reason the weekday letters are — and J, M and A each name two months,
 * the weekday-letters problem the history grid met (§8.4), solved the same way:
 * every column announces itself in full, *"March, 15 active days"*. Under three
 * columns the full name is used, as on the rate card's five.
 */
@Composable
internal fun TrendCard(points: List<TrendPointUi>, modifier: Modifier = Modifier) {
    val initialsOnly = points.size > NAMES_FIT
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
        // Resolved here rather than in the lambdas: stringResource is a
        // composable call and the column row takes plain functions.
        val names = points.map { stringResource(it.monthName) }
        val labels = points.map { stringResource(if (initialsOnly) it.monthInitial else it.monthName) }
        val spoken = points.mapIndexed { index, point ->
            stringResource(
                R.string.insights_trend_point,
                names[index],
                pluralStringResource(R.plurals.insights_active_days, point.activeDays, point.activeDays),
            )
        }
        LabelledColumns(
            items = points.indices.toList(),
            value = { points[it].activeDays.toString() },
            label = { labels[it] },
            // One stop per column, saying the month in full whatever the label
            // under it shows.
            spoken = { spoken[it] },
        )
    }
}

/** A quarter's three columns take the month's name, as the rate card's five do; anything wider takes an initial. */
private const val NAMES_FIT = 3
