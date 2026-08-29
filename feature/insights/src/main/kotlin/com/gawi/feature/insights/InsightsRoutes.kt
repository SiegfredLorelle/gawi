package com.gawi.feature.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The two screens `:app` shows from this module, wired up.
 *
 * Takes a `String` and a plain lambda, and puts neither a compose nor a
 * `:core:data` type in its signature — the rule `:feature:habits`' and
 * `:feature:today`'s Routes follow, and what keeps `:core:data` and `:core:ui`
 * on implementation scope in this module's build file. Navigation stays entirely
 * in `:app`: nothing here knows what a `NavController` is, and no test here
 * needs one.
 *
 * The id arrives as a `String` because that is what a navigation argument is,
 * and because `HabitId` throws on anything that is not a canonical UUIDv7 —
 * validating inside the ViewModel turns a malformed route into a state the
 * screen can draw rather than a crash on the way to it.
 *
 * `hiltViewModel()` rather than `viewModel()`: this is a back-stack destination,
 * so the store owner is the entry rather than the activity. Two habits' histories
 * are therefore two ViewModels, and the month one of them was scrolled to cannot
 * show up under the other.
 */
@Composable
fun HistoryRoute(habitId: String, onBack: () -> Unit) {
    val viewModel: HistoryViewModel =
        hiltViewModel<HistoryViewModel, HistoryViewModel.Factory>(
            creationCallback = { factory -> factory.create(habitId) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryScreen(
        state = state,
        actions = HistoryActions(
            onEarlier = viewModel::onEarlier,
            onLater = viewModel::onLater,
            onBack = onBack,
        ),
    )
}

/**
 * Every habit at once, over one period.
 *
 * Takes one plain lambda and nothing else — no id, because this screen is about
 * no particular habit, which is the whole reason it is its own destination
 * rather than a section of habit detail.
 *
 * Plain `hiltViewModel()` with no `creationCallback`: there is nothing to hand
 * it. Still a back-stack destination, so the store owner is the entry and the
 * period a user picked does not outlive the screen.
 */
@Composable
fun InsightsRoute(onBack: () -> Unit) {
    val viewModel: InsightsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    InsightsScreen(
        state = state,
        actions = InsightsActions(
            onPeriod = viewModel::onPeriod,
            onBreakdown = viewModel::onBreakdown,
            onEarlier = viewModel::onEarlier,
            onLater = viewModel::onLater,
            onBack = onBack,
        ),
    )
}
