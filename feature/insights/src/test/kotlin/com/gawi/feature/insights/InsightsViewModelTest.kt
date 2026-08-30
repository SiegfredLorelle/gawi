package com.gawi.feature.insights

import app.cash.turbine.test
import com.gawi.core.data.model.TagEffort
import com.gawi.feature.insights.testsupport.FakeHabitRepository
import com.gawi.feature.insights.testsupport.MainDispatcherRule
import com.gawi.feature.insights.testsupport.habitId
import com.gawi.feature.insights.testsupport.habitState
import com.gawi.feature.insights.testsupport.thisMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Which window the app-wide screen asks for, and what it does not re-ask.
 *
 * The window is the subject, the same way it is for the history grid: every
 * number on this screen comes from reads over one range, so a picker that
 * changed the label without changing the range would look right and report the
 * wrong period.
 */
class InsightsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeHabitRepository()

    private fun insights() = InsightsViewModel(repository)

    private val august = thisMonth(1)..thisMonth(31)
    private val july = LocalDate.parse("2026-07-01")..LocalDate.parse("2026-07-31")
    private val june = LocalDate.parse("2026-06-01")..LocalDate.parse("2026-06-30")
    private val quarter = LocalDate.parse("2026-07-01")..LocalDate.parse("2026-09-30")
    private val year = LocalDate.parse("2026-01-01")..LocalDate.parse("2026-12-31")

    /** How many reads asked for one window — two per period, one per query. */
    private fun readsOf(window: ClosedRange<LocalDate>) = repository.ranges.count { it == window }

    @Test
    fun `insights starts blank rather than on a period it has not read`() {
        assertEquals(InsightsUiState.Loading, insights().uiState.value)
    }

    @Test
    fun `it opens on this month, and reports it`() = runTest {
        repository.allHabits = listOf(habitState(id = habitId(1), name = "read"))
        repository.completionsByHabit = mapOf(habitId(1) to setOf(thisMonth(3), thisMonth(4)))
        repository.tagEffort = listOf(TagEffort("mind", 2))

        insights().uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()

            val state = awaitItem() as InsightsUiState.Overview
            assertEquals(Period.MONTH, state.period)
            // Habits first: "how am I doing" is the question this screen was
            // added to answer.
            assertEquals(Breakdown.HABITS, state.breakdown)
            assertEquals(2, state.activeDays)
            assertEquals(listOf("read"), state.habits.map { it.name })
            assertEquals(listOf("mind"), state.tags.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }

        // The completions and the tags over August, and the tags once more over
        // the month before it for the focus sentence.
        assertEquals(listOf(august, august, july), repository.ranges)
    }

    @Test
    fun `each period reads its own window`() = runTest {
        val viewModel = insights()
        viewModel.uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()
            awaitItem()

            viewModel.onPeriod(Period.QUARTER)
            assertEquals(Period.QUARTER, (awaitItem() as InsightsUiState.Overview).period)

            viewModel.onPeriod(Period.YEAR)
            assertEquals(Period.YEAR, (awaitItem() as InsightsUiState.Overview).period)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, readsOf(august))
        assertEquals(2, readsOf(quarter))
        assertEquals(2, readsOf(year))
    }

    /**
     * The toggle re-renders and re-reads **nothing**.
     *
     * Both breakdowns come out of the same reads, so flipping is a different
     * view of rows already in hand. If this ever starts re-querying, the screen
     * is paying twice for the same rows to show the other half of them.
     */
    @Test
    fun `flipping the breakdown queries nothing new`() = runTest {
        val viewModel = insights()
        viewModel.uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()
            awaitItem()
            val before = repository.ranges.size

            viewModel.onBreakdown(Breakdown.TAGS)
            assertEquals(Breakdown.TAGS, (awaitItem() as InsightsUiState.Overview).breakdown)

            viewModel.onBreakdown(Breakdown.HABITS)
            assertEquals(Breakdown.HABITS, (awaitItem() as InsightsUiState.Overview).breakdown)

            assertEquals("the toggle re-read the log", before, repository.ranges.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `re-picking the period already shown does nothing`() = runTest {
        val viewModel = insights()
        viewModel.uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()
            awaitItem()

            viewModel.onPeriod(Period.MONTH)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, readsOf(august))
    }

    /**
     * Each read, because a throw out of any of them reaches `viewModelScope`'s
     * `SupervisorJob` — which has no handler and would take the process with it.
     */
    @Test
    fun `a failing context read is unavailable rather than a crash`() = runTest {
        repository.contextFailure = IllegalStateException("the preferences file is unreadable")

        insights().uiState.test {
            assertEquals(InsightsUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(repository.ranges.isEmpty())
    }

    @Test
    fun `a failing habit list is unavailable rather than a crash`() = runTest {
        repository.listFailure = IllegalStateException("the database is gone")

        insights().uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()

            assertEquals(InsightsUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing tag read is unavailable rather than a crash`() = runTest {
        repository.effortFailure = IllegalStateException("the query is malformed")

        insights().uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()

            assertEquals(InsightsUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the logical date decides the window, not a date resolved here`() = runTest {
        val viewModel = insights()
        viewModel.uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            // A day in a different month from the fixture's, so a screen that
            // resolved its own date would read the wrong window.
            repository.emitContext(today = LocalDate.parse("2026-11-09"))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, readsOf(LocalDate.parse("2026-11-01")..LocalDate.parse("2026-11-30")))
        assertEquals(0, readsOf(august))
    }

    // ---- the retrospective's stepper ----

    @Test
    fun `stepping back reads the earlier window and the one before it`() = runTest {
        repository.allHabits = listOf(habitState(id = habitId(1), name = "read"))
        val viewModel = insights()
        viewModel.uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()
            val now = awaitItem() as InsightsUiState.Overview
            assertEquals(false, now.canStepLater)

            viewModel.onEarlier()
            val earlier = awaitItem() as InsightsUiState.Overview
            assertEquals(PeriodLabelUi.Month(R.string.insights_month_july, 2026), earlier.label)
            assertEquals(true, earlier.canStepLater)
            cancelAndIgnoreRemainingEvents()
        }

        // Once as August's previous period, then twice as the window itself.
        assertEquals(3, readsOf(july))
        assertEquals(1, readsOf(june))
    }

    @Test
    fun `stepping later stops at the current period`() = runTest {
        repository.allHabits = listOf(habitState(id = habitId(1), name = "read"))
        val viewModel = insights()
        viewModel.uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()
            awaitItem()

            viewModel.onLater()
            expectNoEvents()

            viewModel.onEarlier()
            assertEquals(true, (awaitItem() as InsightsUiState.Overview).canStepLater)
            viewModel.onLater()
            assertEquals(false, (awaitItem() as InsightsUiState.Overview).canStepLater)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `picking a period returns to the current one`() = runTest {
        repository.allHabits = listOf(habitState(id = habitId(1), name = "read"))
        val viewModel = insights()
        viewModel.uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()
            awaitItem()

            viewModel.onEarlier()
            awaitItem()
            viewModel.onPeriod(Period.QUARTER)
            val state = awaitItem() as InsightsUiState.Overview
            assertEquals(PeriodLabelUi.Quarter(3, 2026), state.label)
            assertEquals(false, state.canStepLater)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, readsOf(quarter))
    }

    @Test
    fun `the focus sentence hedges the current period and compares a complete one`() = runTest {
        repository.allHabits = listOf(habitState(id = habitId(1), name = "read"))
        repository.tagEffort = listOf(TagEffort("career", 9), TagEffort("health", 4))
        repository.tagEffortByWindow[june] = listOf(TagEffort("health", 8))

        val viewModel = insights()
        viewModel.uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()
            assertEquals(FocusShiftUi.SoFar("career"), (awaitItem() as InsightsUiState.Overview).focus)

            viewModel.onEarlier()
            // July's own totals are the default fixture; June's are health.
            assertEquals(FocusShiftUi.Shifted(from = "health", to = "career"), (awaitItem() as InsightsUiState.Overview).focus)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stepping back stops once nothing earlier can hold a habit`() = runTest {
        repository.allHabits = listOf(habitState(id = habitId(1), name = "read", createdOn = LocalDate.parse("2026-07-20")))

        val viewModel = insights()
        viewModel.uiState.test {
            assertEquals(InsightsUiState.Loading, awaitItem())
            repository.emitContext()
            assertEquals(true, (awaitItem() as InsightsUiState.Overview).canStepEarlier)

            viewModel.onEarlier()
            val july = awaitItem() as InsightsUiState.Overview
            assertEquals(false, july.canStepEarlier)

            viewModel.onEarlier()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        // June was read once, as July's previous period for the sentence — never
        // as a window of its own.
        assertEquals(1, readsOf(june))
    }
}
