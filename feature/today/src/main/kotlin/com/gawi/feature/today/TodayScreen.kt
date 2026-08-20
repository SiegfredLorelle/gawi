package com.gawi.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
internal fun TodayScreen(state: TodayUiState, actions: TodayActions, snackbarHostState: SnackbarHostState, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.today_title)) },
                // The two ways off this screen. Deliberately no FAB as well:
                // Today is for ticking habits off, and a third affordance
                // competing with the rows would crowd the one thing PRD §6.1
                // wants to take a single tap.
                actions = {
                    ManageHabitsButton(actions.onManageHabits)
                    SettingsButton(actions.onOpenSettings)
                },
            )
        },
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
                EmptyToday(onAddHabit = actions.onAddHabit, modifier = Modifier.fillMaxSize())
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
                HabitList(state, actions.onToggle, Modifier.weight(1f))
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

/**
 * The empty state, with the thing it tells you to do.
 *
 * `today_empty_body` has said "Add a habit and it starts here" since 4b, with
 * nothing to tap. The button is what makes that sentence true — and it is the
 * shortest path from a fresh install to a first habit, which is what the
 * 30-day trial in PRD §5 cannot start without.
 */
@Composable
private fun EmptyToday(onAddHabit: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Notice(
            title = stringResource(R.string.today_empty_title),
            body = stringResource(R.string.today_empty_body),
        )
        Button(onClick = onAddHabit) {
            Text(stringResource(R.string.today_add_habit))
        }
    }
}

/**
 * Named for assistive technology; the glyph carries no meaning on its own.
 *
 * A list glyph rather than the gear this used to carry. The gear was fine while
 * it was the only way off this screen, but it is the one symbol a reader will
 * take to mean settings — and now that settings is a real destination beside
 * it, leaving it here would point at the wrong one.
 */
@Composable
private fun ManageHabitsButton(onManageHabits: () -> Unit) {
    val label = stringResource(R.string.today_manage_habits)
    IconButton(onClick = onManageHabits, modifier = Modifier.semantics { contentDescription = label }) {
        Text(text = "\u2630", style = MaterialTheme.typography.titleLarge)
    }
}

/** The gear, which now means what it looks like. */
@Composable
private fun SettingsButton(onOpenSettings: () -> Unit) {
    val label = stringResource(R.string.today_settings)
    IconButton(onClick = onOpenSettings, modifier = Modifier.semantics { contentDescription = label }) {
        Text(text = "\u2699", style = MaterialTheme.typography.titleLarge)
    }
}
