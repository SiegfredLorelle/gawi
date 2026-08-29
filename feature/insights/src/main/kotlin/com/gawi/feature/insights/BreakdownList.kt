package com.gawi.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gawi.core.ui.streak.StreakUi
import com.gawi.core.ui.theme.GawiSpacing

/**
 * Where the effort went: a bar per tag, longest first, untagged last.
 *
 * **Every bar is `primary`.** The redesign artboard drew the untagged bar grey
 * to set it apart, and that measures 1.07 in light and 1.97 in dark against
 * `primary` — a reader cannot tell the two apart, which is the same failure the
 * history grid's today-marker had at 1.04. So the distinction moves to channels
 * that survive a greyscale reading: the label is drawn in the recessive role
 * where a tag's is not, and untagged is always last.
 *
 * The number is a **total**, and there is no percentage on the row. A total
 * cannot become wrong the day OQ-1 lets a completion carry two tags
 * (docs/ux/insights.md §5).
 */
@Composable
internal fun TagBars(tags: List<TagShareUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
        tags.forEach { tag ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
            ) {
                Text(
                    text = tag.name ?: stringResource(R.string.insights_untagged),
                    modifier = Modifier.width(LABEL_WIDTH),
                    style = MaterialTheme.typography.bodySmall,
                    // The recessive role for untagged, and the plain one for a
                    // tag. This is the distinction the bar cannot carry.
                    color = if (tag.name == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Bar(fill = tag.fill, modifier = Modifier.weight(1f))
                Text(
                    text = tag.completions.toString(),
                    modifier = Modifier.width(TOTAL_WIDTH),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * How each habit is doing over the period — the answer to "overall".
 *
 * Each row carries its own schedule, because docs/ux/insights.md §4 says a daily
 * habit's rate and a weekly habit's are different fractions. That is also why
 * there is no total row: an average of the two would be the one number this
 * module has refused from the start.
 */
@Composable
internal fun HabitRates(habits: List<HabitRateUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
        habits.forEach { habit ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line)) {
                    Text(text = habit.name, style = MaterialTheme.typography.bodySmall)
                    Text(
                        // The best run rides on the schedule line rather than
                        // taking a third, because the unit it is counted in is
                        // the schedule's, and the two read as one fact there:
                        // "3× a week · best 9 weeks".
                        text = listOfNotNull(scheduleText(habit.schedule), bestText(habit)).joinToString(SEPARATOR),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    // A dash where the period offered nothing. Zero would be the
                    // screen telling someone they failed a period they were not
                    // in — CompletionRate.fraction's own reasoning.
                    text = habit.percent
                        ?.let { stringResource(R.string.insights_rate_percent, it) }
                        ?: stringResource(R.string.insights_rate_none),
                    modifier = Modifier.width(TOTAL_WIDTH),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/**
 * "best 9 weeks" or "best 31 days", in the unit the row's schedule is counted
 * in, or nothing when the period held no run (docs/ux/insights.md §9).
 */
@Composable
private fun bestText(habit: HabitRateUi): String? = when (val best = habit.best) {
    is StreakUi.Days -> pluralStringResource(R.plurals.insights_best_days, best.count, best.count)

    is StreakUi.Weeks -> pluralStringResource(R.plurals.insights_best_weeks, best.count, best.count)

    // A best run is a length or nothing; the live and broken states are Today's.
    is StreakUi.None, is StreakUi.Broken, null -> null
}

/**
 * One bar on its track.
 *
 * `primary` on `surfaceContainerHighest`, the pair `GawiColorSchemeTest` already
 * holds to 1.4.11's 3:1 for the history grid — 4.41 in light, 6.94 in dark. No
 * new pairing was needed for this screen, which is worth knowing before someone
 * adds one for completeness.
 *
 * Thin, and rounded at the data end only in the sense that the whole bar is
 * rounded: a track and a fill sharing one shape reads as a level rather than as
 * two rectangles.
 */
@Composable
private fun Bar(fill: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BAR_CORNER)
    Box(
        modifier = modifier
            .height(BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill.coerceIn(0f, 1f))
                .height(BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.primary, shape),
        )
    }
}

/** Wide enough for a short tag, narrow enough to leave the bar most of the row. */
private val LABEL_WIDTH = 88.dp

/** Fits "100%" and a four-figure total without the bars ending ragged. */
private val TOTAL_WIDTH = 44.dp

private val BAR_HEIGHT = 12.dp
private val BAR_CORNER = 6.dp

/** A middle dot between two facts on one line. */
private const val SEPARATOR = " \u00B7 "
