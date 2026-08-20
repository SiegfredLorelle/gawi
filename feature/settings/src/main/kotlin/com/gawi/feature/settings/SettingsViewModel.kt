package com.gawi.feature.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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
internal class SettingsViewModel @Inject constructor(private val settings: SettingsSource) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settings
        .observe()
        .map { it.toUiState() }
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
     * One write path for all three, because there is only one.
     *
     * A transform rather than three setters is the shape [SettingsSource] asks
     * for: a preferences file is read-modify-write, and per-field setters would
     * be three chances to lose a concurrent edit to a different field.
     */
    private fun write(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            // Threw rather than rejected — there is no rejection here — and
            // uncaught that is a crash on tapping a time rather than a snackbar.
            if (commandOrNull(TAG) { settings.update(transform) } == null) {
                messages.send(SettingsMessage(R.string.settings_error_unexpected))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "SettingsViewModel"
    }
}
