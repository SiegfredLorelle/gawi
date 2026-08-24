package com.gawi.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.gawi.core.ui.component.GlyphButton
import com.gawi.core.ui.component.Notice
import com.gawi.core.ui.theme.GawiSpacing

/**
 * One habit's full history, stateless.
 *
 * docs/ux/insights.md §2's first surface. Reached from habit detail's "see full
 * history", which architecture §2 uses as its own worked example of why this
 * lives in `:feature:insights` and not in a corner of `:feature:habits`: the
 * door is a lambda, `:app` decides where it leads, and being reached from
 * another feature costs a cross-module dependency of exactly zero.
 *
 * The app bar is titled generically and the habit is named in the content, the
 * same way habit detail does it — the screen is about a habit, but which habit
 * is a fact about the data rather than about the destination.
 *
 * No `SnackbarHostState`, unlike every other screen in this app that takes one.
 * There is nothing to report: the screen only reads (§3), so there is no
 * rejection it could be told about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(state: HistoryUiState, actions: HistoryActions, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_history_title)) },
                navigationIcon = { GlyphButton("←", R.string.insights_back, actions.onBack) },
            )
        },
    ) { insets ->
        // targetSdk 37 draws edge to edge with no opt-out, so every branch has
        // to honour the insets or its first row sits under the status bar.
        when (state) {
            // Blank rather than a spinner: the first emission is one Room query.
            HistoryUiState.Loading -> Box(Modifier.fillMaxSize().padding(insets))

            HistoryUiState.Unavailable -> Notice(
                title = stringResource(R.string.insights_unavailable_title),
                body = stringResource(R.string.insights_unavailable_body),
                modifier = Modifier.fillMaxSize().padding(insets),
            )

            is HistoryUiState.Month -> MonthHistory(
                state = state,
                actions = actions,
                modifier = Modifier.fillMaxSize().padding(insets),
            )
        }
    }
}

/**
 * The habit, the month being shown, and the grid.
 *
 * Scrolls, like habit detail and the editor do: six week rows at a large system
 * font scale overflow a short screen, and a grid you cannot reach the bottom of
 * is a history with months missing from it.
 */
@Composable
private fun MonthHistory(state: HistoryUiState.Month, actions: HistoryActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(GawiSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Row),
    ) {
        Text(text = state.habitName, style = MaterialTheme.typography.titleMedium)
        MonthHeader(state, actions)
        HistoryGrid(state)
    }
}

/**
 * Which month, and the two steps either side of it.
 *
 * The later stepper is replaced by a spacer of its own size rather than simply
 * dropped, so the month label stays centred on the month containing today
 * instead of sliding right on the one month the user opens first.
 */
@Composable
private fun MonthHeader(state: HistoryUiState.Month, actions: HistoryActions) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton("‹", R.string.insights_month_previous, actions.onEarlier)
        Text(
            text = stringResource(R.string.insights_month_title, stringResource(state.monthName), state.year),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        if (state.canGoLater) {
            GlyphButton("›", R.string.insights_month_next, actions.onLater)
        } else {
            StepperFootprint()
        }
    }
}

/**
 * An absent stepper's footprint — see [MonthHeader].
 *
 * Named rather than an inline `Spacer`, which would read as the layout one this
 * file does not import and the grid does.
 */
@Composable
private fun StepperFootprint() = Box(Modifier.size(GawiSpacing.TouchTarget))
