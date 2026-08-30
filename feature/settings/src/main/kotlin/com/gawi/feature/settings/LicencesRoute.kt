package com.gawi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The third-party notices, verbatim. This module's second screen, reached from
 * the Settings screen's About section — by whatever `:app` decides that row
 * leads to (docs/ux/settings.md §9).
 */
@Composable
fun LicencesRoute(onBack: () -> Unit) {
    val viewModel: LicencesViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LicencesScreen(state = state, onBack = onBack)
}
