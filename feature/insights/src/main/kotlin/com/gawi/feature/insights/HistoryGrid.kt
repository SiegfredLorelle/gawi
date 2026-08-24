package com.gawi.feature.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gawi.core.ui.date.weekdayName

/**
 * One month of one habit, as a calendar of two-state days.
 *
 * PRD §5's *"per-habit heatmap/calendar history"*, and a calendar rather than a
 * bare heatmap on purpose: the day number is a second channel for the state
 * (`onPrimary` on a filled cell, `onSurfaceVariant` on an empty one) and it is
 * what makes an unfilled cell readable against the page at all, since the two
 * grounds sit close to `surface` by design.
 *
 * **Two states, both schedules** — docs/ux/insights.md §4. A cell says completed
 * or not completed and never "missed": `Schedule.Weekly` is *n times per week on
 * any days*, so there is no day a weekly habit was supposed to be done on, and
 * a three-state cell would be honest for `Daily` and a lie for `Weekly`.
 *
 * **A day that has not happened is drawn as nothing**, not as a day that was not
 * done. That is the liveness rule `Rates` applies to a completion rate, in
 * pixels: an unfinished day is not a miss.
 *
 * Not tappable, anywhere. §3: the domain refuses a completion write outside the
 * retro window, so a writable month grid would draw twenty-odd cells that look
 * identical to the four that work.
 */
@Composable
internal fun HistoryGrid(month: HistoryUiState.Month, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
        WeekdayHeader(month.weekdayLetters)
        month.weeks().forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
            ) {
                week.forEach { cell -> WeekSlot(cell) }
            }
        }
    }
}

/**
 * The seven column letters, and the one place in this app that hides content
 * from a screen reader.
 *
 * A one-letter header cannot be read aloud: `T` and `S` each name two days, so
 * seven stops of "M T W T F S S" would cost a reader something and tell them
 * almost nothing. **What replaces it is not nothing** — every cell speaks its
 * own weekday spelled out, which is strictly more than the columns could say,
 * and is why hiding these is a trade rather than a loss.
 *
 * `clearAndSetSemantics` on the row rather than on each letter, because the
 * merged row is the node a reader would otherwise land on.
 */
@Composable
private fun WeekdayHeader(labels: List<Int>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
    ) {
        labels.forEach { label ->
            Text(
                text = stringResource(label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One of seven slots in a week row.
 *
 * A null cell is padding — before the 1st or after the last — and a future day
 * is drawn the same way, so both take the column's width and none of its ink. A
 * trailing row that is entirely future therefore collapses to nothing, which is
 * what should happen to a week that has not started.
 */
@Composable
private fun RowScope.WeekSlot(cell: DayCellUi?) {
    if (cell == null || cell.future) {
        Spacer(Modifier.weight(1f))
    } else {
        DayCell(cell, Modifier.weight(1f))
    }
}

/**
 * One day.
 *
 * The grounds are measured rather than chosen by eye: `primary` against
 * `surfaceContainerHighest` is 4.41 in light and 6.94 in dark, and
 * `GawiColorSchemeTest` holds the pair to 1.4.11's 3:1 so the done/not-done
 * distinction cannot drift below it. The numbers on them are `onPrimary on
 * primary` and `onSurfaceVariant on surfaceContainerHighest`, both already in
 * that file.
 *
 * **Today is a ring, not a different fill.** `RetroStrip` marks today by
 * filling its cell with `secondaryContainer`, and that does not transfer:
 * against this grid's not-done ground it measures 1.04 in light and 1.05 in
 * dark, so a not-done today would be indistinguishable from every other
 * not-done day — the same failure docs/ux/visual-identity.md §3's published
 * `tertiary` had at 1.02. The ring takes the one colour already proven against
 * whichever ground it lands on.
 */
@Composable
private fun DayCell(cell: DayCellUi, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(CELL_CORNER)
    val completed = cell.completed
    val ground = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val ink = if (completed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val ring = if (completed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val label = stringResource(labelFor(cell), stringResource(weekdayName(cell.date.dayOfWeek)), cell.dayOfMonth)
    Box(
        modifier = modifier
            // A minimum rather than a fixed height, so a cell grows with the
            // font scale instead of clipping its own number.
            .defaultMinSize(minHeight = CELL_MIN_HEIGHT)
            .background(ground, shape)
            .then(if (cell.isToday) Modifier.border(BorderStroke(CELL_RING, ring), shape) else Modifier)
            // Merged and described by hand, like the strip's shut cell: without
            // it the number is its own stop and says "14" with no month, no
            // weekday and no state.
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(vertical = CELL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = ink,
        )
    }
}

/**
 * Which of the four spoken labels one day takes.
 *
 * Four whole sentences rather than two plus an appended suffix, so a translator
 * sees each one entire. "Not done yet" only on today: the day is still open, and
 * the flat "not done" a finished day gets would be an accusation about a day the
 * user is still inside — the same distinction `Rates` draws by refusing to count
 * an unfinished unit.
 */
private fun labelFor(cell: DayCellUi): Int = when {
    cell.isToday && cell.completed -> R.string.insights_day_today_done
    cell.isToday -> R.string.insights_day_today_not_done
    cell.completed -> R.string.insights_day_done
    else -> R.string.insights_day_not_done
}

/**
 * The month's days laid into week rows, padded at both ends.
 *
 * Leading nulls put the 1st under its own weekday column; trailing nulls keep
 * the last row seven slots wide, so its cells stay the same width as every
 * other row's rather than stretching to fill it.
 */
private fun HistoryUiState.Month.weeks(): List<List<DayCellUi?>> {
    val slots: List<DayCellUi?> = List(leadingBlanks) { null } + days
    val trailing = (DAYS_IN_WEEK - slots.size % DAYS_IN_WEEK) % DAYS_IN_WEEK
    return (slots + List(trailing) { null }).chunked(DAYS_IN_WEEK)
}

/**
 * Between cells, and between rows.
 *
 * Tighter than `GawiSpacing.Gap`: seven columns and up to six rows multiply a
 * gutter by six, and 12dp of it would take a third of the grid's width. Local
 * rather than named in `GawiSpacing`, which holds the measurements more than one
 * place has to agree on — nothing else draws a seven-column grid.
 */
private val GRID_GAP = 4.dp
private val CELL_MIN_HEIGHT = 40.dp
private val CELL_CORNER = 8.dp
private val CELL_RING = 1.dp

/** Breathing room under the number once the font scale has grown it. */
private val CELL_PADDING = 2.dp
