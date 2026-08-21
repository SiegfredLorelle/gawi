package com.gawi.feature.settings

import android.content.res.Resources
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate

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
    // Read on each composition rather than remembered. Deliberately NOT keyed on
    // LocalConfiguration, which would look like it refreshed and would not:
    // Configuration carries no 12/24-hour field, so flipping the system clock
    // format broadcasts ACTION_TIME_CHANGED and never triggers a configuration
    // change. The call is a settings lookup the framework caches, so reading it
    // is cheap; what it costs is that the format only catches up when something
    // else recomposes this. docs/ux/settings.md §5 records the gap.
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)

    // Both launchers are created unconditionally, never inside a branch.
    // rememberLauncherForActivityResult registers with the activity's result
    // registry and re-registers after process death — and the file picker is
    // exactly where a low-memory kill happens, so a launcher created only in
    // one state is a callback that never fires on the way back.
    //
    // A null Uri is a cancelled picker. Nothing happens and nothing is said:
    // docs/ux/settings.md §3's rule is that Cancel means nothing changed, and
    // the system's own dialog is this section's version of that dialog.
    val exportTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(EXPORT_MIME_TYPE)) { target ->
        if (target != null) viewModel.onExportTo(target)
    }
    val exportCompletionsTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME_TYPE)) { target ->
        if (target != null) viewModel.onExportCompletionsTo(target)
    }
    val importFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) viewModel.onImportFrom(source)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(resources.format(message))
        }
    }

    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onDayCutoffChange = viewModel::onDayCutoffChange,
            onWeekStartChange = viewModel::onWeekStartChange,
            onReminderTimeChange = viewModel::onReminderTimeChange,
            // The wall-clock date, not the logical one — see exportFileName.
            onExport = { exportTo.launch(exportFileName(LocalDate.now())) },
            onExportCompletions = { exportCompletionsTo.launch(csvFileName(LocalDate.now())) },
            onImport = { importFrom.launch(IMPORT_MIME_TYPES) },
            onBack = onBack,
        ),
        snackbarHostState = snackbarHostState,
        is24Hour = is24Hour,
    )
}

/**
 * Resolves a message and whatever counts it carries.
 *
 * The spread is over a list that is either empty or holds two ints, resolved
 * once when a snackbar is shown. detekt's warning is about copying large arrays
 * on a hot path, and `getString`'s vararg leaves no other way to pass them.
 */
@Suppress("SpreadOperator")
private fun Resources.format(message: SettingsMessage): String = getString(message.text, *message.args.toTypedArray())

/** What an export is written as. `CreateDocument` requires one. */
private const val EXPORT_MIME_TYPE = "application/json"

/**
 * What the CSV is written as.
 *
 * `text/csv` is the registered type (RFC 4180) and is what makes a spreadsheet
 * the default handler for the file afterwards, which is the whole point of the
 * row. Note this is only the *created* document's type; the import picker's
 * generous filter is a separate decision and is about reading.
 */
private const val CSV_MIME_TYPE = "text/csv"

/**
 * What the import picker offers to show.
 *
 * Three types rather than one, and the two extras are not sloppiness. An export
 * that has been round-tripped through a cloud drive, a messaging app or a USB
 * copy very often comes back typed `application/octet-stream`, and some
 * providers report any `.json` as `text/plain`. A filter that hides a user's
 * own backup from them is a worse failure than one that also shows a few text
 * files — and this is the only recovery path there is.
 *
 * The filter is a convenience and never the check. What makes a file an export
 * is decided by reading it.
 */
private val IMPORT_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")
