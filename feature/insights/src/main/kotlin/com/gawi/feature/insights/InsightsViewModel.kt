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
import kotlinx.coroutines.flow.update
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

    /**
     * The period's kind and how many of them back from today's — one value,
     * because the two only mean something together.
     *
     * An offset rather than a stored date, for the reason the history grid's
     * month is (docs/ux/insights.md §8.5): the window is recomputed from
     * `observeReadContext`'s today on every emission, so zero keeps meaning
     * "now" across a day rollover with nothing on this side holding a clock.
     * Picking a different kind resets the offset — "three quarters back" has no
     * meaning in years.
     */
    private val selection = MutableStateFlow(Selection(Period.MONTH, back = 0))

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
        selection.value = Selection(period, back = 0)
    }

    fun onBreakdown(breakdown: Breakdown) {
        this.breakdown.value = breakdown
    }

    /**
     * Guarded on the state already shown, as [onLater] is on the offset: once
     * the screen says there is nothing earlier, a tap that got through the
     * disabled arrow still does nothing rather than re-reading three flows
     * for an empty period.
     */
    fun onEarlier() {
        if ((uiState.value as? InsightsUiState.Overview)?.canStepEarlier == false) return
        selection.update { it.copy(back = it.back + 1) }
    }

    /** Clamped here, not only disabled on screen: a rule that lives only in a button is lost with it. */
    fun onLater() = selection.update { it.copy(back = (it.back - 1).coerceAtLeast(0)) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun overviewFlow(): Flow<InsightsUiState> =
        combine(habits.observeReadContext(), selection) { context, selection -> context to selection }
            .flatMapLatest { (context, selection) ->
                val period = selection.period
                val window = period.window(context.today, selection.back)
                // The period before, for the focus sentence: the same query over
                // one more window, not a second kind of read.
                val previous = period.window(context.today, selection.back + 1)
                combine(
                    habits.observeAllHabits(),
                    habits.observeCompletionDatesByHabit(window.start, window.endInclusive),
                    habits.observeTagEffort(window.start, window.endInclusive),
                    habits.observeTagEffort(previous.start, previous.endInclusive),
                    breakdown,
                ) { all, completions, tagEffort, previousTagEffort, mode ->
                    val reads = PeriodReads(window, all, completions, tagEffort, previousTagEffort)
                    overviewOf(period, selection.back, mode, context, reads)
                }
            }

    private data class Selection(val period: Period, val back: Int)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TAG = "InsightsViewModel"
    }
}
