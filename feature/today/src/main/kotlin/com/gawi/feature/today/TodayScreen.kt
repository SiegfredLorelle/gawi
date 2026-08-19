package com.gawi.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.theme.GawiSpacing
import java.time.LocalDate

/**
 * The Today view, stateless.
 *
 * A plain list on purpose. docs/ux/today-view.md §1 fixes a fixed-height Momo
 * panel that collapses into an app-bar chip on scroll; that is deferred until
 * the data path underneath this has run on a device, because a scroll animation
 * and a mood state machine on the same unproven screen is the wrong thing to
 * debug first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayScreen(
    state: TodayUiState,
    onToggle: (HabitId, Boolean, LocalDate) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.today_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        // Honouring these is not optional: targetSdk 37 draws edge to edge with
        // no way to opt out, so a list that ignored them would put its first row
        // under the status bar.
        when (state) {
            // Deliberately blank rather than a spinner. The first emission is
            // one Room query, so anything animated would be a flash; this state
            // exists so the screen does not claim there are no habits before it
            // has looked.
            TodayUiState.Loading -> Box(Modifier.fillMaxSize().padding(insets))

            is TodayUiState.Empty -> EmptyToday(Modifier.fillMaxSize().padding(insets))

            is TodayUiState.Habits -> HabitList(state, onToggle, insets)
        }
    }
}

@Composable
private fun HabitList(state: TodayUiState.Habits, onToggle: (HabitId, Boolean, LocalDate) -> Unit, insets: PaddingValues) {
    LazyColumn(contentPadding = insets) {
        items(state.rows, key = { it.id.value }) { row ->
            HabitRow(
                row = row,
                // The date travels with the row, so a tap writes to the day it
                // was drawn for rather than to one resolved a moment later.
                onToggle = { onToggle(row.id, row.completed, state.logicalDate) },
            )
        }
    }
}

@Composable
private fun EmptyToday(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(GawiSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.today_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.today_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
