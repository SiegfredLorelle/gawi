package com.gawi.feature.insights

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.domain.model.HabitId
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
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
 * Injects [SettingsSource] as well as the repository, which no other screen
 * that draws habits does. It has to: the grid's columns start on the user's week
 * start, and `HabitDetail` carries the logical date but not that setting.
 * `:feature:settings` is the precedent for a feature reading the store directly.
 *
 * Reads only — docs/ux/insights.md §3. So there is no message channel, no
 * snackbar and no command path here, which is most of what habit detail's
 * ViewModel is.
 */
@HiltViewModel(assistedFactory = HistoryViewModel.Factory::class)
internal class HistoryViewModel @AssistedInject constructor(
    @Assisted private val rawHabitId: String,
    private val habits: HabitRepository,
    private val settings: SettingsSource,
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
            // Keyed on the two fields this screen actually reads, because
            // HabitDetail also carries the streak and the strip's five cells:
            // without this, completing anything at all in the last few days
            // would re-subscribe the month query below for an unchanged header.
            // The cells stay live regardless — observeCompletedDates is its own
            // flow and is what the grid is drawn from.
            habits.observeHabitDetail(habitId).distinctUntilChangedBy { detail ->
                detail?.let { it.habit.habit.name to it.today }
            },
            settings.observe().map { it.weekStart }.distinctUntilChanged(),
            monthOffset,
        ) { detail, weekStart, offset -> Triple(detail, weekStart, offset) }
            .flatMapLatest { (detail, weekStart, offset) ->
                if (detail == null) {
                    flowOf(HistoryUiState.Unavailable)
                } else {
                    val month = YearMonth.from(detail.today).plusMonths(offset)
                    habits
                        .observeCompletedDates(habitId, month.atDay(1), month.atEndOfMonth())
                        .map { completedDates -> detail.toMonthUiState(month, weekStart, completedDates) }
                }
            }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "HistoryViewModel"
    }
}
