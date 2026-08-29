package com.gawi.feature.insights

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The line, and nothing else — no axis, no gridlines, no legend.
 *
 * Shared by the per-habit rate card and the period's trend card, which is why
 * it takes bare fractions rather than either card's point type: both draw one
 * series over equal label columns, and a second copy would be a second set of
 * mark specs to keep in step.
 *
 * No y-axis because every value is labelled beneath it, which is the one case
 * the ticks are not needed for. No legend because there is one series, so the
 * title already names it.
 *
 * **The y scale is fixed at 0–1, never scaled to the data.** Five points
 * spanning 71–90% auto-scaled would fill the plot's whole height and read as a
 * collapse and a recovery; against a fixed scale it reads as what it is, a flat
 * four months.
 *
 * **A null point breaks the line rather than being skipped over.** Joining the
 * months either side of a gap would draw a segment through a month that has no
 * value — a line the data does not support.
 */
@Composable
internal fun Sparkline(fills: List<Float?>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        if (fills.isEmpty()) return@Canvas
        val inset = (DOT_RADIUS + RING_WIDTH).toPx()
        val plotHeight = size.height - inset * 2

        // **A point sits above the centre of its own label's column**, which is
        // a coupling to the row below rather than a property of the plot. Those
        // labels are `fills.size` equal `weight(1f)` columns, so their centres
        // are at `(index + 0.5) / size`. Spreading the points edge to edge
        // instead — the obvious reading of "fill the width" — put the outer two
        // about 27dp from the months they belong to on a phone, which made the
        // rate card's own claim that the labels are the chart's table view
        // false. Only a device shows it; no test here can see it.
        val column = size.width / fills.size

        fun offsetOf(index: Int, fill: Float): Offset = Offset(
            x = column * (index + 0.5f),
            y = inset + plotHeight * (1f - fill.coerceIn(0f, 1f)),
        )

        val marks = fills.mapIndexed { index, fill -> fill?.let { offsetOf(index, it) } }

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

/** Tall enough for a value to have somewhere to move, short enough to stay a sparkline. */
internal val SPARKLINE_HEIGHT = 56.dp

/** The dataviz mark specs: a 2dp line, a dot of at least 8dp across, and a 2dp surface ring. */
private val LINE_WIDTH = 2.dp
private val DOT_RADIUS = 4.dp
private val RING_WIDTH = 2.dp
