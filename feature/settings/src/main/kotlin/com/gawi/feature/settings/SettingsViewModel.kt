package com.gawi.feature.settings

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.backup.EventArchive
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

/**
 * The settings screen's state holder.
 *
 * Injects only the settings source: no repository and no clock. Nothing on this
 * screen is a function of the habits or of the date — it edits the three values
 * that decide how both are interpreted, which is a different job.
 *
 * The store is the single source of truth for what is drawn. There is no local
 * copy of a committed setting here, which is what stops the screen and the file
 * disagreeing after a write that failed. A half-picked time does need somewhere
 * to live, but that somewhere is the dialog rather than this class: it is not a
 * setting until it is confirmed, and abandoning a dialog must leave nothing
 * behind.
 */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(private val settings: SettingsSource, private val archive: EventArchive) :
    ViewModel() {

    /**
     * Whether a file is being written or read.
     *
     * The one thing this class holds that the store does not, and it is not a
     * copy of anything stored — see [SettingsUiState]. It lives here rather
     * than in the screen because the work is `viewModelScope`'s: what ends it
     * is a coroutine finishing, which a `rememberSaveable` cannot hear.
     */
    private val dataTask = MutableStateFlow(DataTask.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(settings.observe(), dataTask) { stored, task -> stored.toUiState(task) }
        .catch { cause ->
            // Not the unreadable-file case, which observe() absorbs into
            // defaults on purpose. Reaching here means something that is not IO
            // went wrong, and guessing a cutoff is the one thing this app
            // refuses to do.
            Log.e(TAG, "the settings read failed", cause)
            emit(SettingsUiState.Unavailable)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState.Loading,
        )

    private val messages = Channel<SettingsMessage>(Channel.BUFFERED)

    val events: Flow<SettingsMessage> = messages.receiveAsFlow()

    /**
     * The day boundary.
     *
     * Applies from now on. Events already in the log carry the logical date
     * they were written under and are never re-bucketed (architecture §5), so
     * moving this does not rewrite last week — which is what the copy under the
     * row has to say, because it is not what a reader would assume.
     */
    fun onDayCutoffChange(cutoff: LocalTime) = write { it.copy(dayCutoff = cutoff) }

    /** The day a week is counted from, for weekly habits' progress and streaks. */
    fun onWeekStartChange(weekStart: DayOfWeek) = write { it.copy(weekStart = weekStart) }

    /** When the day is treated as nearly over. */
    fun onReminderTimeChange(reminderTime: LocalTime) = write { it.copy(reminderTime = reminderTime) }

    /**
     * Writes the whole log to the document the picker returned.
     *
     * Takes the `Uri` and uses it now. The grant belongs to the activity that
     * asked for it, so there is nothing here worth keeping for later.
     */
    fun onExportTo(destination: Uri) = runDataTask(DataTask.Exporting) {
        archive.exportTo(destination)
        SettingsMessage(R.string.settings_export_done)
    }

    /** Merges the export at [source] into this device's log. */
    fun onImportFrom(source: Uri) = runDataTask(DataTask.Importing) { messageFor(archive.importFrom(source)) }

    /**
     * One at a time, and always says something.
     *
     * The idle guard is here as well as on the rows, because two taps can land
     * before the disabled state has been through `combine` and recomposed.
     * `finally` rather than a line at the end: a failure that skipped it would
     * leave both rows dead for the life of the screen.
     */
    private fun runDataTask(task: DataTask, work: suspend () -> SettingsMessage) {
        if (dataTask.value != DataTask.Idle) return
        dataTask.value = task
        viewModelScope.launch {
            try {
                val message = commandOrNull(TAG, task.describe(), work)
                messages.send(message ?: SettingsMessage(failureFor(task)))
            } finally {
                dataTask.value = DataTask.Idle
            }
        }
    }

    private fun DataTask.describe(): String = when (this) {
        DataTask.Exporting -> "the export"
        DataTask.Importing -> "the import"
        DataTask.Idle -> "the data task"
    }

    @StringRes
    private fun failureFor(task: DataTask): Int = when (task) {
        // Not "nothing was written": the picker created the document before the
        // write began, so what is on disk is a plausibly-named partial file.
        DataTask.Exporting -> R.string.settings_error_export

        // Import validates the whole file before it writes a row, so this one
        // can promise the log is untouched and mean it.
        DataTask.Importing -> R.string.settings_error_import

        DataTask.Idle -> R.string.settings_error_unexpected
    }

    /**
     * One write path for all three settings, because there is only one.
     *
     * A transform rather than three setters is the shape [SettingsSource] asks
     * for: a preferences file is read-modify-write, and per-field setters would
     * be three chances to lose a concurrent edit to a different field.
     */
    private fun write(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            // Threw rather than rejected — there is no rejection here — and
            // uncaught that is a crash on tapping a time rather than a snackbar.
            if (commandOrNull(TAG, "the settings write") { settings.update(transform) } == null) {
                messages.send(SettingsMessage(R.string.settings_error_unexpected))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "SettingsViewModel"
    }
}
