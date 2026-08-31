package com.gawi.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.component.GawiIconButton
import com.gawi.core.ui.component.GawiIcons
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
                // The three ways off this screen. Deliberately no FAB as well:
                // Today is for ticking habits off, and an affordance competing
                // with the rows would crowd the one thing PRD §6.1 wants to
                // take a single tap.
                actions = {
                    // Three icon buttons, all :core:ui's GawiIconButton. The two
                    // that were here were that composable written out by hand,
                    // and a third copy beside them was the moment to stop.
                    GawiIconButton(GawiIcons.ListChecks, R.string.today_manage_habits, onClick = actions.onManageHabits)
                    GawiIconButton(GawiIcons.ChartPie, R.string.today_insights, onClick = actions.onOpenInsights)
                    GawiIconButton(GawiIcons.Settings, R.string.today_settings, onClick = actions.onOpenSettings)
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

            // The panel scrolls with what is under it, in both states. It used to
            // sit above the list as a fixed header; a 250dp tank (docs/ux/momo.md
            // §4) above an unscrollable column leaves a small screen, or a large
            // font scale, with the button or the second row below the fold. §1
            // already accepted Momo leaving the screen on a long list; the
            // collapse into an app-bar chip is what is still deferred.
            is TodayUiState.Empty -> {
                val motion = rememberTodayMotion(state.mood, emptyList())
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(insets)
                        .verticalScroll(rememberScrollState()),
                ) {
                    MascotPanel(MascotUi(state.mood, remaining = 0, total = 0, regeneratingHabit = null), motion)
                    EmptyToday(onAddHabit = actions.onAddHabit, modifier = Modifier.fillMaxWidth())
                }
            }

            is TodayUiState.Habits -> HabitList(state, actions.onToggle, Modifier.fillMaxSize().padding(insets))
        }
    }
}

@Composable
private fun HabitList(state: TodayUiState.Habits, onToggle: (HabitId, Boolean, LocalDate) -> Unit, modifier: Modifier = Modifier) {
    // The gate and the celebration live here, above the list, because the
    // mascot item below is disposed when it scrolls off and the celebration's
    // memory of the last mood has to outlive that (rememberCelebration).
    val motion = rememberTodayMotion(state.mood, state.rows)
    // Read in composition: it changes twice per milestone, so only the rows
    // that crossed recompose; the per-frame scale is read inside a layer
    // lambda in StreakBadge and recomposes nothing — and reads the badge's
    // scale alone, not the tank's whole frame. With animations off the pill
    // still shows and the scale is simply 1.
    val pulsing = motion.milestone.pulsing
    val pulse: () -> Float = if (motion.animationsOn) ({ motion.milestone.badgeScale }) else ({ 1f })
    val mascot = MascotUi(state.mood, state.remaining, total = state.rows.size, regeneratingHabit = state.regeneratingHabit)
    LazyColumn(modifier) {
        // The habitat is the first item, not a header outside the list: §1 keeps
        // habit rows on plain surface, so row contrast is never a function of the
        // mood, and scrolling Momo away is §1's accepted cost.
        item(key = "mascot") {
            MascotPanel(mascot, motion)
        }
        items(state.rows, key = { it.id.value }) { row ->
            HabitRow(
                row = row,
                // The date travels with the row, so a tap writes to the day it
                // was drawn for rather than to one resolved a moment later.
                onToggle = { onToggle(row.id, row.completed, state.logicalDate) },
                pulse = if (row.id in pulsing) pulse else null,
            )
        }
    }
}

/**
 * The empty state, with the thing it tells you to do.
 *
 * `today_empty_body` has said "Add a habit and it starts here" since 4b, with
 * nothing to tap. The button is what makes that sentence true — and it is the
 * shortest path from a fresh install to a first habit, which is where any real
 * use of this app has to start.
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

/*
 * The three app-bar icons, and why each is what it is.
 *
 * Manage habits was a gear once, and the gear moved to settings when settings
 * became a destination — the one symbol a reader will take to mean settings
 * should point at it. It draws `list-checks` rather than a hamburger, because
 * the destination is a list of things you tick and `☰`, which is what it used
 * to be, implied a navigation drawer this app has never had.
 *
 * Insights draws `chart-pie`, which is what `◔` was reaching for, and is
 * distinct in silhouette from both the list and the gear — the whole
 * requirement of an icon in a row of three. None of the three has an
 * accessible name of its own, so GawiIconButton takes one.
 *
 * Settings draws `settings`, and means what it looks like.
 *
 * All three were characters until 2026-08-24, and the reason they are not is
 * that all three — `☰`, `◔`, `⚙` — are outside Outfit's cmap, so they fell
 * back to the platform face and this row rendered two typefaces at one size.
 * docs/ux/visual-identity.md §7.5 records the replacement. The by-hand check
 * in docs/running.md §4 is still owed, but what it is looking for has moved:
 * not a tofu box, which a vector cannot draw, but whether the strokes read at
 * a glance and hold their colour in both themes.
 */
