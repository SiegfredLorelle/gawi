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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** What the editor tells the Route about, once. */
internal sealed interface HabitEditorEvent {

    /** Saved. The Route pops; the list is already observing the write. */
    data object Saved : HabitEditorEvent

    data class Rejected(val message: HabitsMessage) : HabitEditorEvent
}

/**
 * The habit editor's state holder, for a new habit or an existing one.
 *
 * Assisted injection rather than a `SavedStateHandle` key, so which habit is
 * being edited is a typed constructor parameter instead of a string agreed with
 * `:app`'s route by convention. It is also what keeps navigation out of this
 * module entirely: the Route takes the id as an argument and never looks at a
 * back-stack entry.
 *
 * [rawHabitId] is a String rather than a [HabitId] because it arrives from a
 * navigation argument and `HabitId` rejects anything that is not a canonical
 * UUIDv7 by throwing. Validating it here turns a malformed route into the
 * `Unavailable` state instead of a crash in the constructor.
 */
@HiltViewModel(assistedFactory = HabitEditorViewModel.Factory::class)
internal class HabitEditorViewModel @AssistedInject constructor(
    @Assisted private val rawHabitId: String?,
    private val habits: HabitRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(rawHabitId: String?): HabitEditorViewModel
    }

    private val habitId: HabitId? = rawHabitId?.let { value ->
        runCatching { HabitId(value) }.getOrNull()
    }

    private val form = MutableStateFlow<HabitEditorUiState>(
        // Nothing to load for a new habit, so it opens straight onto the form.
        if (rawHabitId == null) newHabitForm() else HabitEditorUiState.Loading,
    )

    val uiState: StateFlow<HabitEditorUiState> = form.asStateFlow()

    private val messages = Channel<HabitEditorEvent>(Channel.BUFFERED)

    val events: Flow<HabitEditorEvent> = messages.receiveAsFlow()

    init {
        if (rawHabitId != null) loadExisting()
    }

    /**
     * Read once, not observed.
     *
     * `first()` deliberately: a form that kept following the habit would
     * overwrite whatever was being typed the moment anything else wrote to it.
     * A malformed id skips the read entirely and lands on the same state an
     * unknown one does.
     */
    private fun loadExisting() {
        viewModelScope.launch {
            form.value = when (habitId) {
                null -> HabitEditorUiState.Unavailable

                else ->
                    habits
                        .observeHabit(habitId)
                        .map { it?.habit?.toForm() ?: HabitEditorUiState.Unavailable }
                        .catch { cause ->
                            Log.e("HabitEditorViewModel", "reading the habit to edit failed", cause)
                            emit(HabitEditorUiState.Unavailable)
                        }
                        .first()
            }
        }
    }

    /** Every field edit, as one call: the form is the state, so it replaces it. */
    fun onEdit(edited: HabitEditorUiState.Form) {
        form.value = edited
    }

    fun onSave() {
        val current = form.value
        if (current !is HabitEditorUiState.Form) return
        viewModelScope.launch { submit(current) }
    }

    /**
     * Saved as a create or an update depending on how the editor was opened,
     * never on whether the form happens to look complete.
     *
     * The blank-name check is repeated here rather than trusted to a disabled
     * button, because the button's `canSave` and the domain's `isBlank` are two
     * statements of the same rule and only one of them is enforced.
     */
    private suspend fun submit(current: HabitEditorUiState.Form) {
        if (!current.canSave) {
            messages.send(HabitEditorEvent.Rejected(HabitsMessage(R.string.habits_error_blank_name)))
            return
        }
        val metadata = current.toMetadata()
        val result: CommandResult<*> = when (habitId) {
            null -> habits.createHabit(metadata)
            else -> habits.updateHabit(habitId, metadata)
        }
        val event = when (result) {
            is CommandResult.Rejected -> HabitEditorEvent.Rejected(HabitsMessage(messageFor(result.error)))
            is CommandResult.Accepted -> HabitEditorEvent.Saved
        }
        messages.send(event)
    }
}
