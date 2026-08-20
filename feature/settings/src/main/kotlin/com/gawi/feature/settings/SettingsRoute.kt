package com.gawi.feature.settings

import android.text.format.DateFormat
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The settings screen, wired up.
 *
 * Takes no modifier and puts no Compose or `:core:data` type in its signature —
 * the same rule the other Routes follow, and what keeps `:core:ui` and
 * `:core:data` on implementation scope here. One plain lambda goes out, so
 * navigation stays entirely in `:app`.
 *
 * The one thing read off the platform here is the device's 12- or 24-hour
 * preference, which is a fact about the phone rather than about the app. It is
 * resolved here and passed down so the screen stays renderable, in both
 * conventions, without a device to set it on.
 */
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // LocalResources rather than LocalContext: reading strings off the context
    // is not configuration-aware, so the copy would go stale on a locale change.
    val resources = LocalResources.current
    val context = LocalContext.current
    // Keyed on the configuration rather than remembered once: the clock
    // preference is a system setting, and changing it is a configuration change.
    val configuration = LocalConfiguration.current
    val is24Hour = remember(configuration) { DateFormat.is24HourFormat(context) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(resources.getString(message.text))
        }
    }

    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onDayCutoffChange = viewModel::onDayCutoffChange,
            onWeekStartChange = viewModel::onWeekStartChange,
            onReminderTimeChange = viewModel::onReminderTimeChange,
            onBack = onBack,
        ),
        snackbarHostState = snackbarHostState,
        is24Hour = is24Hour,
    )
}
