package com.gawi.feature.habits

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
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
import javax.inject.Inject

/**
 * The habit list's state holder.
 *
 * Injects only the repository, like Today's: no clock, because nothing on this
 * screen is a function of the date. Archiving is not dated — it is a
 * last-write-wins register on the habit itself (architecture §3).
 */
@HiltViewModel
internal class HabitListViewModel @Inject constructor(private val habits: HabitRepository) : ViewModel() {

    val uiState: StateFlow<HabitListUiState> = habits
        .observeAllHabits()
        .map { it.toListUiState() }
        .catch { cause ->
            Log.e(TAG, "the habit list read failed", cause)
            emit(HabitListUiState.Unavailable)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HabitListUiState.Loading,
        )

    private val messages = Channel<HabitsMessage>(Channel.BUFFERED)

    val events: Flow<HabitsMessage> = messages.receiveAsFlow()

    /**
     * Archive or bring back, from the state the row was drawn in.
     *
     * The current value travels with the tap rather than being read back here,
     * so what is transmitted is the intent the row expressed — the same rule
     * Today's toggle follows.
     */
    fun onArchiveToggle(habitId: HabitId, archived: Boolean) {
        viewModelScope.launch {
            val result = commandOrNull(TAG) {
                if (archived) habits.unarchiveHabit(habitId) else habits.archiveHabit(habitId)
            }
            when {
                // Threw rather than rejected, and uncaught that is a crash on an
                // Archive tap. The read above is guarded for the same reason.
                result == null -> messages.send(HabitsMessage(R.string.habits_error_unexpected))

                result is CommandResult.Rejected -> messages.send(HabitsMessage(messageFor(result.error)))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "HabitListViewModel"
    }
}
