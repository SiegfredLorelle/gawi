package com.gawi.feature.today

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The Today view, wired up. This is what `:app` shows.
 *
 * Takes no modifier: it is a whole screen, `:app` has nothing to pass it, and
 * leaving it off is what keeps every Compose type out of this module's public
 * surface — which is why `:core:ui` can be an implementation dependency here.
 *
 * `viewModel()` rather than `hiltViewModel()`. Both resolve the same factory:
 * the store owner is the `@AndroidEntryPoint` activity, whose generated
 * superclass supplies Hilt's default factory, and `hiltViewModel` only differs
 * when the owner is a navigation back-stack entry. Since there is no nav graph
 * yet, using it would mean putting the whole navigation library on the
 * classpath to reach a factory that is already there.
 */
@Composable
fun TodayRoute() {
    val viewModel: TodayViewModel = viewModel()
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

    TodayScreen(state = state, onToggle = viewModel::onToggle, snackbarHostState = snackbarHostState)
}
