package com.gawi.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.gawi.core.ui.component.GawiIconButton
import com.gawi.core.ui.component.GawiIcons
import com.gawi.core.ui.component.Notice
import com.gawi.core.ui.theme.GawiSpacing

/**
 * Every habit at once, over one period — stateless.
 *
 * The app's only app-wide report, and the answer to the gap the per-habit
 * surfaces left: the history grid and the rate trend are about one habit, and
 * nothing said how things were going overall. docs/ux/insights.md §7's "the tag
 * distribution has no obvious door" is closed by this being a destination of its
 * own, reached from Today's app bar.
 *
 * One time control, at the top, scoping everything under it — not a picker per
 * card — and, since Phase 1.5, a stepper under the chips that walks that period
 * back through the calendar: the retrospective is this screen one period back
 * (docs/ux/insights.md §9), not a screen of its own. The per-habit history screen keeps its own month steppers and is
 * deliberately not governed from here: a month grid can only show a month.
 *
 * No `SnackbarHostState`: nothing here writes, so there is no rejection to
 * report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InsightsScreen(state: InsightsUiState, actions: InsightsActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
                navigationIcon = { GawiIconButton(GawiIcons.ArrowLeft, R.string.insights_back, onClick = actions.onBack) },
            )
        },
    ) { insets ->
        // targetSdk 37 draws edge to edge with no opt-out, so every branch has
        // to honour the insets or its first row sits under the status bar.
        when (state) {
            // Blank rather than a spinner: the first emission is a handful of
            // Room queries.
            InsightsUiState.Loading -> Box(Modifier.fillMaxSize().padding(insets))

            // This screen's own copy, not the history screen's. It borrowed that
            // one until review caught it, and the borrowed title says "Can't
            // show this history" — a thing the reader did not open.
            InsightsUiState.Unavailable -> Notice(
                title = stringResource(R.string.insights_read_failed_title),
                body = stringResource(R.string.insights_read_failed_body),
                modifier = Modifier.fillMaxSize().padding(insets),
            )

            is InsightsUiState.Overview -> Overview(
                state = state,
                actions = actions,
                modifier = Modifier.fillMaxSize().padding(insets),
            )
        }
    }
}

@Composable
private fun Overview(state: InsightsUiState.Overview, actions: InsightsActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(GawiSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Row),
    ) {
        PeriodPicker(state.period, actions.onPeriod)
        PeriodStepper(state.label, state.canStepLater, actions)
        Headline(state)
        state.focus?.let { FocusLine(it) }
        // Only when there is something to bucket: a single month is one point,
        // and a period that has not begun draws nothing (insights.md §9).
        if (state.trend.isNotEmpty()) TrendCard(state.trend)
        BreakdownPicker(state.breakdown, actions.onBreakdown)

        when {
            // **Three empty states, because a list can be empty for three
            // different reasons and each deserves different advice.**
            //
            // No habit at all: a fresh install, and the commonest way to reach
            // this screen empty, since the app bar offers it before a first
            // habit exists. This branch was missing until review caught it, and
            // the archived copy below was being shown instead — telling a new
            // user "every habit is archived", which was untrue.
            state.breakdown == Breakdown.HABITS && !state.hasAnyHabit -> Notice(
                title = stringResource(R.string.insights_no_habits_yet_title),
                body = stringResource(R.string.insights_no_habits_yet_body),
                modifier = Modifier.fillMaxWidth(),
            )

            // Habits exist and every one is archived. The headline above still
            // counts their completions — deliberately, since effort spent is
            // history — so this cannot say "nothing logged" without
            // contradicting the two rows above it.
            state.breakdown == Breakdown.HABITS && state.habits.isEmpty() -> Notice(
                title = stringResource(R.string.insights_no_habits_title),
                body = stringResource(R.string.insights_no_habits_body),
                modifier = Modifier.fillMaxWidth(),
            )

            // And the period simply holds no completions, which is what the
            // Tags breakdown runs out of.
            state.breakdown == Breakdown.TAGS && state.tags.isEmpty() -> Notice(
                title = stringResource(R.string.insights_empty_title),
                body = stringResource(R.string.insights_empty_body),
                modifier = Modifier.fillMaxWidth(),
            )

            state.breakdown == Breakdown.HABITS -> HabitRates(state.habits)

            else -> TagBars(state.tags)
        }
    }
}

/**
 * Two exact numbers about the period.
 *
 * Neither needs a denominator, which is why these two and not a "perfect days"
 * count: whether *every* habit was done on a past day is not answerable — a
 * weekly habit has no due day (docs/ux/insights.md §4) — and nothing records
 * which habits existed then. Plurals, so "1 active day" reads.
 */
@Composable
private fun Headline(state: InsightsUiState.Overview) {
    Column(verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line)) {
        Text(
            text = pluralStringResource(R.plurals.insights_active_days, state.activeDays, state.activeDays),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = pluralStringResource(R.plurals.insights_completions, state.completions, state.completions),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Which calendar period the numbers describe, between the two arrows that walk
 * it — Phase 1.5's retrospective is this row (docs/ux/insights.md §9).
 *
 * **The later arrow is disabled at the current period, not removed.** The
 * history grid drops its stepper at zero and leaves a footprint; here the label
 * sits between the two arrows and a vanishing one would shift it, so the button
 * stays, greyed to Material's disabled alpha, with its content description
 * kept. The clamp itself lives in the ViewModel, so a tap that got through
 * would still do nothing.
 */
@Composable
private fun PeriodStepper(label: PeriodLabelUi, canStepLater: Boolean, actions: InsightsActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GawiIconButton(GawiIcons.ChevronLeft, R.string.insights_period_earlier, onClick = actions.onEarlier)
        Text(
            text = periodTitle(label),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        GawiIconButton(GawiIcons.ChevronRight, R.string.insights_period_later, enabled = canStepLater, onClick = actions.onLater)
    }
}

@Composable
private fun periodTitle(label: PeriodLabelUi): String = when (label) {
    is PeriodLabelUi.Month -> stringResource(R.string.insights_period_month_title, stringResource(label.name), label.year)
    is PeriodLabelUi.Quarter -> stringResource(R.string.insights_period_quarter_title, label.quarter, label.year)
    is PeriodLabelUi.Year -> stringResource(R.string.insights_period_year_title, label.year)
}

/**
 * One sentence under the headline about where the effort moved.
 *
 * Body copy rather than a card: it is a remark about the two numbers above it,
 * not a third surface, and a card would promote a comparison of two tag totals
 * to the standing of the adherence list.
 */
@Composable
private fun FocusLine(focus: FocusShiftUi) {
    Text(
        text = when (focus) {
            is FocusShiftUi.Shifted -> stringResource(R.string.insights_focus_shifted, focus.from, focus.to)
            is FocusShiftUi.Held -> stringResource(R.string.insights_focus_held, focus.tag)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * Month, Quarter, Year — docs/ux/insights.md §7's settled answer.
 *
 * `FilterChip`s, the idiom `:feature:habits`' schedule picker already uses and
 * the shape the artboard drew. Not a `SegmentedButton`: it would be a second way
 * of doing the same thing in an app that has one.
 */
@Composable
private fun PeriodPicker(selected: Period, onPeriod: (Period) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
        Period.entries.forEach { period ->
            FilterChip(
                selected = period == selected,
                onClick = { onPeriod(period) },
                label = { Text(stringResource(period.label)) },
            )
        }
    }
}

/** Habits or tags, over the same period — one list, not two screens. */
@Composable
private fun BreakdownPicker(selected: Breakdown, onBreakdown: (Breakdown) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
        Breakdown.entries.forEach { breakdown ->
            FilterChip(
                selected = breakdown == selected,
                onClick = { onBreakdown(breakdown) },
                label = { Text(stringResource(breakdown.label())) },
            )
        }
    }
}

private fun Breakdown.label(): Int = when (this) {
    Breakdown.HABITS -> R.string.insights_breakdown_habits
    Breakdown.TAGS -> R.string.insights_breakdown_tags
}
