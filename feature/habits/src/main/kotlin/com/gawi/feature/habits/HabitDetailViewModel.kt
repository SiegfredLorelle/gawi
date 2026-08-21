package com.gawi.feature.habits

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Habit detail's state holder.
 *
 * Assisted injection and a raw `String` id, for the reasons
 * `HabitEditorViewModel` sets out: which habit this is becomes a typed
 * constructor parameter rather than a key agreed with `:app` by convention, and
 * a malformed route argument lands on [HabitDetailUiState.Unavailable] instead
 * of throwing out of the constructor. `HabitId` rejects anything that is not a
 * canonical UUIDv7.
 *
 * Observed rather than read once, which is the opposite of the editor's choice
 * and for the opposite reason: there is no half-typed form here to overwrite,
 * and the streak has to follow the log. `observeHabitDetail` re-emits on a day
 * rollover and sweeps a stale streak on the way through, so this screen never
 * holds a clock and never shows yesterday's number against today's tick — and
 * the retro strip shifts by a day on its own when the cutoff passes.
 *
 * The id is non-null, unlike the editor's: there is no "new habit" detail.
 */
@HiltViewModel(assistedFactory = HabitDetailViewModel.Factory::class)
internal class HabitDetailViewModel @AssistedInject constructor(
    @Assisted private val rawHabitId: String,
    private val habits: HabitRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(rawHabitId: String): HabitDetailViewModel
    }

    private val habitId: HabitId? = runCatching { HabitId(rawHabitId) }.getOrNull()

    val uiState: StateFlow<HabitDetailUiState> = detailFlow()
        // Mandatory, not defensive. A throw out of a read flow reaches
        // viewModelScope's SupervisorJob, which has no CoroutineExceptionHandler,
        // and takes the process with it — the same guard both other screens here
        // put on their reads.
        .catch { cause ->
            Log.e(TAG, "the habit detail read failed", cause)
            emit(HabitDetailUiState.Unavailable)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HabitDetailUiState.Loading,
        )

    /**
     * A malformed id never reaches the repository.
     *
     * It resolves to the same state an unknown one does, so there is one
     * "cannot show you this" branch rather than two that would read alike.
     */
    private fun detailFlow() = when (habitId) {
        null -> flowOf(HabitDetailUiState.Unavailable)

        else ->
            habits
                .observeHabitDetail(habitId)
                .map { detail -> detail?.toDetailUiState() ?: HabitDetailUiState.Unavailable }
    }

    private val messages = Channel<HabitsMessage>(Channel.BUFFERED)

    val events: Flow<HabitsMessage> = messages.receiveAsFlow()

    /**
     * Complete or un-complete one day, from the state the cell was drawn in.
     *
     * [logicalDate] and [completed] both travel with the tap rather than being
     * read back here, so what is written is the intent the cell expressed. The
     * date matters most: the 3-day window *accepts* a date one day stale rather
     * than refusing it, so re-deriving one would write to the wrong day and
     * report success — the trap the widget's toggle carries its own note about.
     *
     * The honesty prompt has already happened by the time this is called, or the
     * day was today and needed none. Nothing is re-checked here: architecture §5
     * puts the window in the domain, which rejects a day out of range whatever
     * the UI believed.
     */
    fun onToggle(habitId: HabitId, logicalDate: LocalDate, completed: Boolean) {
        viewModelScope.launch {
            val result = commandOrNull(TAG) {
                if (completed) {
                    habits.undoCompletion(habitId, logicalDate)
                } else {
                    habits.addCompletion(habitId, logicalDate)
                }
            }
            when {
                // Threw rather than rejected, and uncaught that is a crash on a
                // cell tap. The read above is guarded for the same reason.
                result == null -> messages.send(HabitsMessage(R.string.habits_error_unexpected))

                result is CommandResult.Rejected -> messages.send(HabitsMessage(messageFor(result.error)))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "HabitDetailViewModel"
    }
}
