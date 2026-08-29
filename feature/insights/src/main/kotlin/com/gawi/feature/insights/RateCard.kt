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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import com.gawi.core.ui.theme.GawiSpacing

/**
 * How much of this habit's target the last few months held.
 *
 * The subtitle is the schedule, and it is not decoration: docs/ux/insights.md §4
 * says a daily habit's rate and a weekly habit's are different fractions, so a
 * column of bare percentages invites being compared with a column it is not
 * comparable to. Naming the denominator once is the cheapest fix.
 *
 * **The row of labels is the chart's table view, not decoration on top of it.**
 * A dataviz rule this deliberately trades against is "never a number on every
 * point" — which is right where a tooltip and a y-axis can carry the values
 * instead. Neither exists here: this is a touch surface with no hover, so a
 * label is the *only* path to a value, and the same rule allows exactly this
 * trade ("keep the y-axis ticks unless every value is labeled"). Five points is
 * small enough for it to read.
 *
 * So the labels carry the data and the line carries only the direction — which
 * is why the line is hidden from assistive technology and the labels are not.
 */
@Composable
internal fun RateCard(rate: RateTrendUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line)) {
        Text(
            text = stringResource(R.string.insights_rate_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = scheduleText(rate.schedule),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rate.plottable) {
            Sparkline(
                fills = rate.points.map { point -> point.percent?.let { it / PERCENT_SCALE } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SPARKLINE_HEIGHT)
                    // The line says nothing a reader cannot get from the labels
                    // below it, and "a line went up" is not a fact a screen
                    // reader can act on. Cleared rather than left to announce a
                    // Canvas.
                    .clearAndSetSemantics { },
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            rate.points.forEach { point ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        // A dash, not "0%". Nothing in this month had finished,
                        // and zero would be the screen accusing the user on no
                        // evidence — CompletionRate.fraction's own reasoning.
                        text = point.percent
                            ?.let { stringResource(R.string.insights_rate_percent, it) }
                            ?: stringResource(R.string.insights_rate_none),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(point.monthName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private const val PERCENT_SCALE = 100f
