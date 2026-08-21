package com.gawi.feature.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.backup.CompletionCsvArchive
import com.gawi.core.data.backup.EventArchive
import com.gawi.core.data.backup.ExportStatus
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
 * Injects the settings source and the two archives: no repository and no clock. What
 * this screen shows is not a function of the habits or of the date — it edits
 * the three values that decide how both are interpreted, which is a different
 * job. Even the last-export age arrives already counted, from the class that
 * does own a clock, so there is still nothing here that has to be told what time
 * it is. (This paragraph used to claim the settings source was the only
 * injection, which stopped being true when export landed.)
 *
 * The store is the single source of truth for what is drawn. There is no local
 * copy of a committed setting here, which is what stops the screen and the file
 * disagreeing after a write that failed. A half-picked time does need somewhere
 * to live, but that somewhere is the dialog rather than this class: it is not a
 * setting until it is confirmed, and abandoning a dialog must leave nothing
 * behind.
 */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val settings: SettingsSource,
    private val archive: EventArchive,
    private val completions: CompletionCsvArchive,
) : ViewModel() {

    /**
     * Whether a file is being written or read.
     *
     * The one thing this class holds that the store does not, and it is not a
     * copy of anything stored — see [SettingsUiState]. It lives here rather
     * than in the screen because the work is `viewModelScope`'s: what ends it
     * is a coroutine finishing, which a `rememberSaveable` cannot hear.
     */
    private val dataTask = MutableStateFlow(DataTask.Idle)

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.observe(), exportStatus(), dataTask) { stored, status, task ->
            stored.toUiState(task, recencyOf(status))
        }.catch { cause ->
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

    /**
     * The last-export status, guarded so that it can never take the screen down.
     *
     * `ExportJournal` absorbs both failures it can name — an unreadable
     * preferences file and a log it cannot count — and decides what each one
     * *means* there, so reaching this catch is a bug rather than a bad disk. It
     * is still caught, because the alternative is specific and bad: the two flows
     * this is combined with go to [SettingsUiState.Unavailable] on failure,
     * correctly, since settings you cannot read cannot be drawn. This one is a
     * caption on a row, and one shared catch cannot tell them apart — so without
     * this, a broken caption takes the only disaster-recovery path on the device
     * off the screen with it (docs/ux/settings.md §7).
     *
     * **`hasEvents = true`, so even the bug resolves towards nudging.** A wrong
     * warning costs an export nobody needed; a wrong silence costs the warning
     * PRD §5 asked for, on a device that may have no backup. A bug is exactly the
     * situation in which there is nothing left to argue the choice from, so it
     * takes the same direction the rest of the feature does. This claimed to be a
     * bug-only path before it was one: `SQLiteException` from the log count is a
     * `RuntimeException`, so it slipped past the journal's `IOException` guard and
     * landed here, where the fallback was silence. A PR reviewer found it.
     */
    private fun exportStatus(): Flow<ExportStatus> = archive.observeExportStatus()
        .catch { cause ->
            Log.e(TAG, "the last-export status is unreadable", cause)
            emit(ExportStatus(daysSinceExport = null, hasEvents = true))
        }

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
    fun onDayCutoffChange(cutoff: LocalTime) = writeUnlessDegenerate { current ->
        current.takeIf { cutoff != it.reminderTime }?.copy(dayCutoff = cutoff)
    }

    /** The day a week is counted from, for weekly habits' progress and streaks. */
    fun onWeekStartChange(weekStart: DayOfWeek) = write { it.copy(weekStart = weekStart) }

    /** When the day is treated as nearly over. */
    fun onReminderTimeChange(reminderTime: LocalTime) = writeUnlessDegenerate { current ->
        current.takeIf { reminderTime != it.dayCutoff }?.copy(reminderTime = reminderTime)
    }

    /**
     * A settings write that can be **refused**, which these two are and the week
     * start is not.
     *
     * The refusal is one specific combination: the reminder time equal to the day
     * cutoff. `reminderOn`'s KDoc has always said that combination resolves to the
     * logical day's *start* rather than its end, and that *"a settings screen
     * offering the two as one control is where the combination should be
     * prevented"*. Nothing prevented it, and until the reminder was built nothing
     * visibly suffered — the cost was Momo looking worried all day, which reads as
     * a mood rather than a bug. With a notification behind the same threshold it
     * became a *"N of N left today"* posted at the top of every logical day, which
     * also consumed that day's one reminder, so the evening was silent too.
     * `ReminderCheck` refuses to act on such a value; this is what stops one being
     * stored. Found by `/code-review`.
     *
     * Guarded from **both** rows, because either can create the collision, and a
     * screen that refused it from one side and allowed it from the other would be
     * the more confusing of the two.
     *
     * This is the first refusable settings write, which `SettingsMessage`'s KDoc
     * used to argue could not exist: *"a fixed picker cannot express an invalid
     * time"*. That reasoning was sound and incomplete — a picker cannot express an
     * invalid time, but it can express a valid time that is invalid *against
     * another setting*, which is a validation the store cannot do because it sees
     * one field at a time.
     */
    private fun writeUnlessDegenerate(transform: (UserSettings) -> UserSettings?) {
        viewModelScope.launch {
            // `refused` is set inside `update`, which is where the current value is
            // visible — the collision is between the picked value and a stored one,
            // so it cannot be decided before the read-modify-write begins.
            var refused = false
            val written = commandOrNull(TAG, "the settings write") {
                settings.update { current -> transform(current) ?: current.also { refused = true } }
            }

            when {
                written == null -> messages.send(SettingsMessage(R.string.settings_error_unexpected))
                refused -> messages.send(SettingsMessage(R.string.settings_error_reminder_equals_cutoff))
            }
        }
    }

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

    /**
     * Writes the completions to the document the picker returned.
     *
     * Separate from [onExportTo] all the way down: a different archive, a
     * different task and a different message. The one thing it deliberately
     * does *not* do is touch the last-export stamp — a CSV holds no events, so
     * recording it as a backup would silence the 30-day nudge over a file that
     * cannot restore anything. That is enforced in `:core:data`, where the CSV
     * archive is not given the journal at all.
     */
    fun onExportCompletionsTo(destination: Uri) = runDataTask(DataTask.ExportingCsv) {
        csvMessageFor(completions.exportTo(destination))
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
