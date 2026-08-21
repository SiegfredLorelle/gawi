package com.gawi.feature.habits

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gawi.core.domain.model.HabitId

/**
 * The three screens `:app` shows, wired up.
 *
 * Neither takes a `Modifier` and neither puts a Compose or `:core:data` type in
 * its signature — the same rule `TodayRoute` follows, and what keeps `:core:ui`
 * and `:core:data` on implementation scope here. What they do take is plain
 * lambdas and a `String?`, so navigation stays entirely in `:app`: nothing in
 * this module knows what a `NavController` is, and no test here needs one.
 *
 * `hiltViewModel()` rather than `viewModel()`, because these are back-stack
 * destinations. The store owner is the entry rather than the activity, which is
 * what stops the editor's half-typed form outliving the screen it belongs to.
 */
@Composable
fun HabitListRoute(onAddHabit: () -> Unit, onEditHabit: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: HabitListViewModel = hiltViewModel()
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

    HabitListScreen(
        state = state,
        actions = HabitListActions(
            onAdd = onAddHabit,
            // Unwrapped at the boundary, so :app's route argument stays a plain
            // String and HabitId does not leak into the navigation graph.
            onEdit = { habitId -> onEditHabit(habitId.value) },
            onArchiveToggle = viewModel::onArchiveToggle,
            onBack = onBack,
        ),
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The editor, for a new habit when [habitId] is null and an existing one
 * otherwise.
 *
 * The id arrives as a `String` because that is what a navigation argument is,
 * and because [HabitId] throws on anything that is not a canonical UUIDv7 —
 * validating inside the ViewModel turns a malformed route into a state the
 * screen can draw rather than a crash on the way to it.
 *
 * [onSaved] and [onCancel] are separate even though both pop today, because
 * they are different events and a caller should be free to treat them so.
 *
 * No explicit ViewModel key. Each `NavBackStackEntry` owns its own
 * `ViewModelStore`, so two editors are already two ViewModels — opening one
 * habit and then another cannot show the first one's form.
 */
@Composable
fun HabitEditorRoute(habitId: String?, onSaved: () -> Unit, onCancel: () -> Unit) {
    val viewModel: HabitEditorViewModel =
        hiltViewModel<HabitEditorViewModel, HabitEditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(habitId) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HabitEditorEvent.Saved -> onSaved()

                is HabitEditorEvent.Rejected ->
                    snackbarHostState.showSnackbar(resources.getString(event.message.text))
            }
        }
    }

    HabitEditorScreen(
        state = state,
        actions = HabitEditorActions(
            onEdit = viewModel::onEdit,
            onSave = viewModel::onSave,
            onCancel = onCancel,
        ),
        snackbarHostState = snackbarHostState,
    )
}

/**
 * One habit, read-only.
 *
 * The id is non-null here, unlike the editor's: there is no "new habit" detail,
 * and a route that could not name its habit would have nothing to draw. It is
 * still a `String` for the same reason — [HabitId] throws on anything that is
 * not a canonical UUIDv7, and the ViewModel is where that is turned into a
 * state rather than a crash.
 *
 * [onEdit] hands the id back rather than closing over the one passed in, so
 * what is edited is the habit the screen is actually showing.
 *
 * No `SnackbarHostState` collection loop: nothing on this screen writes yet, so
 * there are no rejections to report. The host is still created and passed, so
 * the retro strip and the note sheet have somewhere to put theirs.
 */
@Composable
fun HabitDetailRoute(habitId: String, onEdit: (String) -> Unit, onBack: () -> Unit) {
    val viewModel: HabitDetailViewModel =
        hiltViewModel<HabitDetailViewModel, HabitDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(habitId) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    HabitDetailScreen(
        state = state,
        actions = HabitDetailActions(
            onEdit = { id -> onEdit(id.value) },
            onBack = onBack,
        ),
        snackbarHostState = snackbarHostState,
    )
}
