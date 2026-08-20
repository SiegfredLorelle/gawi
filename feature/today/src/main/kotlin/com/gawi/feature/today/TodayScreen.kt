package com.gawi.feature.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.component.Notice
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

            TodayUiState.Unavailable -> Notice(
                title = stringResource(R.string.today_unavailable_title),
                body = stringResource(R.string.today_unavailable_body),
                modifier = Modifier.fillMaxSize().padding(insets),
            )

            is TodayUiState.Empty -> Column(Modifier.fillMaxSize().padding(insets)) {
                MascotPanel(mood = state.mood, remaining = 0, total = 0)
                Notice(
                    title = stringResource(R.string.today_empty_title),
                    body = stringResource(R.string.today_empty_body),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is TodayUiState.Habits -> Column(Modifier.fillMaxSize().padding(insets)) {
                // Above the list rather than over it. §1 keeps habit rows on
                // plain surface, so row contrast is never a function of the
                // mood; the collapse into an app-bar chip is what is deferred.
                MascotPanel(mood = state.mood, remaining = state.remaining, total = state.rows.size)
                // weight(1f) states the intent rather than fixing a bug: Column
                // already measures a non-weighted child against the space its
                // siblings left, so the list scrolls correctly either way. What
                // it does buy is a guarantee that stays true if anything is ever
                // placed below the list.
                //
                // The panel above is the unbounded one — non-weighted, measured
                // first, floored but not capped — so at a large font scale it
                // takes what it needs and the list gets the rest. Capping it
                // would bring back the clipping heightIn(min) was added to fix;
                // §1's collapse into the app bar is where this gets revisited.
                HabitList(state, onToggle, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HabitList(state: TodayUiState.Habits, onToggle: (HabitId, Boolean, LocalDate) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier) {
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
