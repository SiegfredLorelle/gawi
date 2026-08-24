package com.gawi.feature.insights

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                points = rate.points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PLOT_HEIGHT)
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

/**
 * The line, and nothing else — no axis, no gridlines, no legend.
 *
 * No y-axis because every value is labelled beneath it, which is the one case
 * the ticks are not needed for. No legend because there is one series, so the
 * title already names it.
 *
 * **The y scale is fixed at 0–100%, never scaled to the data.** Five points
 * spanning 71–90% auto-scaled would fill the plot's whole height and read as a
 * collapse and a recovery; against a fixed scale it reads as what it is, a flat
 * four months.
 *
 * **A null point breaks the line rather than being skipped over.** Joining the
 * months either side of a gap would draw a segment through a month that has no
 * value — a line the data does not support.
 */
@Composable
private fun Sparkline(points: List<RatePointUi>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val inset = (DOT_RADIUS + RING_WIDTH).toPx()
        val plotHeight = size.height - inset * 2

        // **A point sits above the centre of its own label's column**, which is
        // a coupling to the row below rather than a property of the plot. Those
        // labels are `points.size` equal `weight(1f)` columns, so their centres
        // are at `(index + 0.5) / size`. Spreading the points edge to edge
        // instead — the obvious reading of "fill the width" — put the outer two
        // about 27dp from the months they belong to on a phone, which made this
        // card's own claim that the labels are the chart's table view false.
        // Only a device shows it; no test here can see it.
        val column = size.width / points.size

        fun offsetOf(index: Int, percent: Int): Offset = Offset(
            x = column * (index + 0.5f),
            y = inset + plotHeight * (1f - percent / PERCENT_SCALE),
        )

        val marks = points.mapIndexed { index, point -> point.percent?.let { offsetOf(index, it) } }

        // Segments rather than one path: a run of consecutive non-null points is
        // one stroke, and a gap ends the run.
        marks.zipWithNext { from, to ->
            if (from != null && to != null) {
                drawPath(
                    path = Path().apply {
                        moveTo(from.x, from.y)
                        lineTo(to.x, to.y)
                    },
                    color = line,
                    style = Stroke(width = LINE_WIDTH.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        // Dots last, so the ring cuts the line rather than being drawn under it.
        marks.filterNotNull().forEach { at ->
            drawCircle(ring, radius = (DOT_RADIUS + RING_WIDTH).toPx(), center = at)
            drawCircle(line, radius = DOT_RADIUS.toPx(), center = at)
        }
    }
}

/** Tall enough for a rate to have somewhere to move, short enough to stay a sparkline. */
private val PLOT_HEIGHT = 56.dp

/** The dataviz mark specs: a 2dp line, a dot of at least 8dp across, and a 2dp surface ring. */
private val LINE_WIDTH = 2.dp
private val DOT_RADIUS = 4.dp
private val RING_WIDTH = 2.dp

private const val PERCENT_SCALE = 100f
