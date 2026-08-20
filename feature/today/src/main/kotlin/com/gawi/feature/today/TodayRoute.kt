package com.gawi.feature.today

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Today view, wired up. This is what `:app` shows first.
 *
 * Takes no modifier: it is a whole screen and `:app` has nothing to pass it.
 * The two callbacks are plain lambdas rather than anything navigational, which
 * is what keeps every Compose and navigation type out of this module's public
 * surface — and why `:core:ui` can still be an implementation dependency here.
 * What this screen reports is what happened to it; `:app` decides where it goes.
 *
 * `hiltViewModel()` rather than `viewModel()`. The two resolved the same factory
 * while this was the only screen and the store owner was the `@AndroidEntryPoint`
 * activity. With a back stack the owner is the destination instead, which is the
 * one case where they differ — and scoping the ViewModel to the destination is
 * what stops it outliving the screen it belongs to.
 */
@Composable
fun TodayRoute(onAddHabit: () -> Unit, onManageHabits: () -> Unit) {
    val viewModel: TodayViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalResources rather than LocalContext: reading strings off the context
    // is not configuration-aware, so the copy would go stale on a locale change.
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(resources.getString(message.text))
        }
    }

    TodayScreen(
        state = state,
        actions = TodayActions(
            onToggle = viewModel::onToggle,
            onAddHabit = onAddHabit,
            onManageHabits = onManageHabits,
        ),
        snackbarHostState = snackbarHostState,
    )
}
