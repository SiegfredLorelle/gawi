package com.gawi.feature.insights

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gawi.core.data.repository.HabitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Insights screen's state holder.
 *
 * Plain `@Inject`, not assisted — unlike every other ViewModel in this module
 * and in `:feature:habits`, this screen is about no particular habit. That is
 * the whole reason it exists as its own destination: docs/ux/insights.md §5's
 * tag metric is one number per tag across every habit, so it could not live on
 * a screen that had to be handed one.
 *
 * Takes the date and the week start from `observeReadContext`, which is what
 * that read was added for: the alternative, `observeToday`, sweeps every habit's
 * streak when subscribed, and a screen asking what day it is must not write.
 *
 * Reads only. No message channel and no command path, like the history screen.
 */
@HiltViewModel
internal class InsightsViewModel @Inject constructor(private val habits: HabitRepository) : ViewModel() {

    private val period = MutableStateFlow(Period.MONTH)

    /**
     * Which breakdown is drawn, and it is **not** a query parameter.
     *
     * Collected inside the period's `flatMapLatest` rather than beside it, so
     * flipping the toggle re-renders from values already in hand. Both
     * breakdowns come out of the same reads; making the toggle re-query would be
     * paying for the same rows twice to show the other half of them.
     */
    private val breakdown = MutableStateFlow(Breakdown.HABITS)

    val uiState: StateFlow<InsightsUiState> = overviewFlow()
        // Mandatory, not defensive: a throw out of a read flow reaches
        // viewModelScope's SupervisorJob, which has no handler, and takes the
        // process with it.
        .catch { cause ->
            Log.e(TAG, "the insights read failed", cause)
            emit(InsightsUiState.Unavailable)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = InsightsUiState.Loading,
        )

    /** Month first: the finest period, and what "how am I doing" usually means. */
    fun onPeriod(period: Period) {
        this.period.value = period
    }

    fun onBreakdown(breakdown: Breakdown) {
        this.breakdown.value = breakdown
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun overviewFlow(): Flow<InsightsUiState> =
        combine(habits.observeReadContext(), period) { context, period -> context to period }
            .flatMapLatest { (context, period) ->
                val window = period.window(context.today)
                combine(
                    habits.observeAllHabits(),
                    habits.observeCompletionDatesByHabit(window.start, window.endInclusive),
                    habits.observeTagEffort(window.start, window.endInclusive),
                    breakdown,
                ) { all, completions, tagEffort, mode ->
                    overviewOf(period, mode, context, PeriodReads(window, all, completions, tagEffort))
                }
            }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "InsightsViewModel"
    }
}
