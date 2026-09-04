package com.gawi.feature.insights

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.model.ReadContext
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.projection.HabitState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.YearMonth

/**
 * The history grid's state holder.
 *
 * Assisted injection and a raw `String` id, for the reasons
 * `HabitDetailViewModel` sets out: which habit this is becomes a typed
 * constructor parameter rather than a key agreed with `:app` by convention, and
 * a malformed route argument lands on [HistoryUiState.Unavailable] instead of
 * throwing out of the constructor, since `HabitId` rejects anything that is not
 * a canonical UUIDv7.
 *
 * Takes the logical date and the week start from
 * `HabitRepository.observeReadContext`, which hands both over together.
 *
 * Reads only — docs/ux/insights.md §3. So there is no message channel, no
 * snackbar and no command path here, which is most of what habit detail's
 * ViewModel is.
 */
@HiltViewModel(assistedFactory = HistoryViewModel.Factory::class)
internal class HistoryViewModel @AssistedInject constructor(
    @Assisted private val rawHabitId: String,
    private val habits: HabitRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(rawHabitId: String): HistoryViewModel
    }

    private val habitId: HabitId? = runCatching { HabitId(rawHabitId) }.getOrNull()

    /**
     * Months from the one containing today, and never above zero.
     *
     * An offset rather than an absolute `YearMonth`, so that nothing here holds
     * a date. `observeHabitDetail` re-emits when the day rolls over, which means
     * zero keeps meaning "the current month" across a month boundary without
     * this class ever asking what month it is. An absolute value would go stale
     * on the one night it matters.
     */
    private val monthOffset = MutableStateFlow(0L)

    val uiState: StateFlow<HistoryUiState> = monthFlow()
        // Mandatory, not defensive. A throw out of a read flow reaches
        // viewModelScope's SupervisorJob, which has no CoroutineExceptionHandler,
        // and takes the process with it — the same guard every other read in
        // this app carries.
        .catch { cause ->
            Log.e(TAG, "the habit history read failed", cause)
            emit(HistoryUiState.Unavailable)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = HistoryUiState.Loading,
        )

    /** One month earlier. Unbounded — see [HistoryUiState.Month.canGoLater]. */
    fun onEarlier() = monthOffset.update { offset -> offset - 1 }

    /**
     * One month later, and no later than the month containing today.
     *
     * Clamped here as well as hidden in the screen. The stepper is not drawn at
     * zero, so this cannot normally be reached — but a state holder that would
     * happily open a grid of nothing but future days if it were called anyway is
     * relying on the screen to hold a rule that is not the screen's.
     */
    fun onLater() = monthOffset.update { offset -> (offset + 1).coerceAtMost(0) }

    /**
     * A malformed id never reaches the repository, and resolves to the same
     * state an unknown one does — one "cannot show you this" branch rather than
     * two that would read alike.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun monthFlow(): Flow<HistoryUiState> = when (habitId) {
        null -> flowOf(HistoryUiState.Unavailable)

        else -> combine(
            // observeHabit, not observeHabitDetail. Detail runs a completions
            // query of its own for the retro strip's five cells and carries a
            // streak, none of which this screen draws — and it re-emits on every
            // write to any of them. Its own KDoc names this case: the lean read
            // exists so a caller does not wait on rows it discards.
            habits.observeHabit(habitId).distinctUntilChanged(),
            // Both dated values from one flow. Taking the date from the habit
            // read and the week start from the settings would be two
            // independently deduped copies of a pair the read model keeps in
            // step, which is the disagreement ReadContext exists to prevent.
            habits.observeReadContext(),
        ) { habit, context -> habit to context }
            .flatMapLatest { (habit, context) -> screenFor(habitId, habit?.habit, context) }
    }

    /**
     * Two completion reads, and where the offset sits between them is the point.
     *
     * The grid's window moves when the user steps; the trend's never does. So
     * `monthOffset` is collected **inside** this function, around the grid query
     * only — the trend query is subscribed once per habit-and-context and
     * survives every step. Putting the offset in the outer `combine` instead
     * would tear both queries down on every tap of a stepper, and stepping back
     * a year would re-read a year and a half of rows to redraw one month.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun screenFor(habitId: HabitId, habit: HabitState?, context: ReadContext): Flow<HistoryUiState> {
        if (habit == null) return flowOf(HistoryUiState.Unavailable)
        // Asked of the mapper rather than computed here, so the window read and
        // the months drawn cannot disagree.
        val trendFrom = trendWindowStart(context.today)
        val grid = monthOffset.flatMapLatest { offset ->
            val month = YearMonth.from(context.today).plusMonths(offset)
            habits
                .observeCompletedDates(habitId, month.atDay(1), month.atEndOfMonth())
                .map { cells -> month to cells }
        }
        return combine(grid, habits.observeCompletedDates(habitId, trendFrom, context.today)) { (month, cells), trend ->
            habit.toMonthUiState(
                month = month,
                today = context.today,
                weekStart = context.weekStart,
                completedDates = cells,
                rate = habit.toRateTrend(context.today, context.weekStart, trend.keys),
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "HistoryViewModel"
    }
}
