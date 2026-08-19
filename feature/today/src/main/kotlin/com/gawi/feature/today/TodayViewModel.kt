package com.gawi.feature.today

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.command.CommandError
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** A rejection worth telling the user about, once. */
data class TodayMessage(@StringRes val text: Int)

/**
 * The Today view's state and its two commands.
 *
 * Injects the repository and nothing else. A clock is available in the graph
 * and is deliberately not taken: a clock without the day cutoff cannot produce
 * a logical date, so injecting one would drag the settings in behind it and
 * rebuild the repository's own boundary handling under every screen. The date
 * this writes to arrives with the rows that were drawn for it.
 */
@HiltViewModel
internal class TodayViewModel @Inject constructor(private val habits: HabitRepository) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = habits
        .observeToday()
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            // Load-bearing rather than a default. Collecting observeToday takes
            // the projection mutex, starts a day-boundary timer, sweeps streaks
            // on every context change and holds a Room subscription, so Eagerly
            // would keep all of that alive process-wide with nothing watching.
            // The timeout is what survives a rotation without re-querying, and
            // re-subscription is also how a mood recovers after a spell in the
            // background, where a pending delay cannot be relied on to fire.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TodayUiState.Loading,
        )

    private val messages = Channel<TodayMessage>(Channel.BUFFERED)

    /**
     * Rejections, as events rather than state.
     *
     * A rejection is a fact about a tap that already happened, not a property
     * of the screen. Held in state it would need clearing, and two identical
     * rejections would conflate into one showing. A buffered channel keeps them
     * while nothing is collecting — through a rotation, or the pause above —
     * and hands each to exactly one collector.
     */
    val events: Flow<TodayMessage> = messages.receiveAsFlow()

    /**
     * Completes or un-completes [habitId] for [logicalDate].
     *
     * [completed] and [logicalDate] both come from the row that was tapped
     * rather than from the current state, so what is transmitted is the intent
     * the user actually expressed, against the day they were looking at.
     */
    fun onToggle(habitId: HabitId, completed: Boolean, logicalDate: LocalDate) {
        viewModelScope.launch {
            // Plainly in viewModelScope, with no NonCancellable here. The
            // repository already guards the commit itself, and a second guard
            // would keep work alive past this ViewModel and make the first one
            // impossible to test.
            val result =
                if (completed) habits.undoCompletion(habitId, logicalDate) else habits.addCompletion(habitId, logicalDate)
            if (result is CommandResult.Rejected) report(result.error)
        }
    }

    private suspend fun report(error: CommandError) {
        messageFor(error)?.let { messages.send(TodayMessage(it)) }
    }

    /**
     * The copy for a rejection, or null for one the user should not hear about.
     *
     * `CompletionNotFound` on an undo is what a double tap or a row that has
     * already moved produces. The cell is already in the state the tap asked
     * for, so saying "there was nothing to undo" would be a message about a
     * non-event.
     */
    @StringRes
    private fun messageFor(error: CommandError): Int? = when (error) {
        CommandError.CompletionNotFound -> null
        CommandError.RetroWindowExceeded -> R.string.today_error_retro_window
        CommandError.FutureLogicalDate -> R.string.today_error_future_date
        CommandError.HabitNotFound -> R.string.today_error_habit_missing
        CommandError.HabitIsArchived -> R.string.today_error_habit_archived
        CommandError.BlankName -> R.string.today_error_unexpected
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
