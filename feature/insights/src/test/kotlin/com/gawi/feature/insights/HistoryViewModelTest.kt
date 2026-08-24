package com.gawi.feature.insights

import app.cash.turbine.test
import com.gawi.core.data.settings.UserSettings
import com.gawi.feature.insights.testsupport.FakeHabitRepository
import com.gawi.feature.insights.testsupport.FakeSettingsSource
import com.gawi.feature.insights.testsupport.MainDispatcherRule
import com.gawi.feature.insights.testsupport.habitId
import com.gawi.feature.insights.testsupport.habitState
import com.gawi.feature.insights.testsupport.thisMonth
import com.gawi.feature.insights.testsupport.todayHabit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Which month the grid asks the log for, and which state it lands in.
 *
 * The range is the subject. Everything the screen draws comes from one query
 * over one month, so a stepper that changed the label without changing the
 * window would look right in a screenshot and show August's cells under July's
 * heading — which is why the fake records every range and filters by it.
 */
class HistoryViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeHabitRepository()
    private val settings = FakeSettingsSource()

    private fun historyFor(rawHabitId: String) = HistoryViewModel(rawHabitId, repository, settings)

    private fun theHabit() = habitId(1).also { repository.habit = todayHabit(habitState(id = it)) }

    private val august = thisMonth(1)..thisMonth(31)
    private val july = LocalDate.parse("2026-07-01")..LocalDate.parse("2026-07-31")

    /**
     * Blank before the first read comes back.
     *
     * Read off `value` rather than collected, which is the honest way to ask
     * what `stateIn` starts at — the same reason `HabitDetailViewModelTest`
     * gives for its own Loading case.
     */
    @Test
    fun `history starts blank rather than on a month it has not read`() = runTest {
        theHabit()

        assertEquals(HistoryUiState.Loading, historyFor(habitId(1).value).uiState.value)
    }

    @Test
    fun `a habit opens on the month containing today`() = runTest {
        theHabit()
        repository.completions = mapOf(thisMonth(3) to null)

        historyFor(habitId(1).value).uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())
            settings.emit()

            val month = awaitItem() as HistoryUiState.Month
            assertEquals("read", month.habitName)
            assertEquals(R.string.insights_month_august, month.monthName)
            assertEquals(2026, month.year)
            assertTrue(month.days.single { it.dayOfMonth == 3 }.completed)
            // The month it opens on is the month it can go no later than.
            assertFalse(month.canGoLater)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(august), repository.ranges)
    }

    /**
     * A malformed id never reaches the repository.
     *
     * `HabitId` rejects anything that is not a canonical UUIDv7 by throwing, and
     * a route argument is exactly where one arrives. Landing on `Unavailable` is
     * what turns a bad route into a screen rather than a crash in the
     * constructor.
     */
    @Test
    fun `a malformed id is unavailable and is never looked up`() = runTest {
        historyFor("not-a-uuid").uiState.test {
            assertEquals(HistoryUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(repository.observedIds.isEmpty())
        assertTrue(repository.ranges.isEmpty())
    }

    /** A well-formed id for a habit that is not there lands in the same place. */
    @Test
    fun `an unknown habit is unavailable, and its months are never read`() = runTest {
        repository.habit = null

        historyFor(habitId(1).value).uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())
            settings.emit()

            assertEquals(HistoryUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(habitId(1)), repository.observedIds)
        assertTrue(repository.ranges.isEmpty())
    }

    @Test
    fun `stepping earlier reads the month before, and offers the way back`() = runTest {
        theHabit()
        repository.completions = mapOf(thisMonth(3) to null, LocalDate.parse("2026-07-09") to null)

        val viewModel = historyFor(habitId(1).value)
        viewModel.uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())
            settings.emit()
            awaitItem()

            viewModel.onEarlier()
            val month = awaitItem() as HistoryUiState.Month
            assertEquals(R.string.insights_month_july, month.monthName)
            assertTrue(month.canGoLater)
            // July's cells, not August's re-labelled: the 9th is completed here
            // and the 3rd is not, which is the other way round from the month
            // this screen opened on.
            assertTrue(month.days.single { it.dayOfMonth == 9 }.completed)
            assertFalse(month.days.single { it.dayOfMonth == 3 }.completed)
            // Nothing in July is today, and nothing in it has yet to happen.
            assertTrue(month.days.none { it.isToday })
            assertTrue(month.days.none { it.future })
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(august, july), repository.ranges)
    }

    @Test
    fun `stepping later from this month reads nothing new`() = runTest {
        theHabit()

        val viewModel = historyFor(habitId(1).value)
        viewModel.uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())
            settings.emit()
            awaitItem()

            viewModel.onLater()
            // Clamped, so the offset does not change and nothing downstream runs.
            // A grid of days that have not happened is not a month to show.
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(august), repository.ranges)
    }

    @Test
    fun `stepping earlier and back again returns to this month`() = runTest {
        theHabit()

        val viewModel = historyFor(habitId(1).value)
        viewModel.uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())
            settings.emit()
            awaitItem()

            viewModel.onEarlier()
            awaitItem()
            viewModel.onLater()

            val month = awaitItem() as HistoryUiState.Month
            assertEquals(R.string.insights_month_august, month.monthName)
            assertFalse(month.canGoLater)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(august, july, august), repository.ranges)
    }

    /**
     * The columns follow the setting, not the calendar's default.
     *
     * August 2026 opens on a Saturday: five columns after Monday, six after
     * Sunday. A screen that ignored the store would pass one of these.
     */
    @Test
    fun `the week start decides where the first of the month sits`() = runTest {
        theHabit()

        historyFor(habitId(1).value).uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())

            settings.emit(UserSettings(weekStart = DayOfWeek.SUNDAY))
            assertEquals(6, (awaitItem() as HistoryUiState.Month).leadingBlanks)

            settings.emit(UserSettings(weekStart = DayOfWeek.MONDAY))
            assertEquals(5, (awaitItem() as HistoryUiState.Month).leadingBlanks)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Both reads, because a throw out of either reaches `viewModelScope`'s
     * `SupervisorJob` — which has no handler and would take the process with it.
     */
    @Test
    fun `a failing habit read is unavailable rather than a crash`() = runTest {
        theHabit()
        repository.detailFailure = IllegalStateException("the database is gone")

        // Loading is not awaited here, and that is not an oversight: the throw
        // happens on subscription under the unconfined dispatcher, so the state
        // has already moved past `stateIn`'s initial value by the time a
        // collector sees anything. The cases that emit before failing do await
        // it.
        historyFor(habitId(1).value).uiState.test {
            assertEquals(HistoryUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing completions read is unavailable rather than a crash`() = runTest {
        theHabit()
        repository.completionsFailure = IllegalStateException("the query is malformed")

        historyFor(habitId(1).value).uiState.test {
            assertEquals(HistoryUiState.Loading, awaitItem())
            settings.emit()

            assertEquals(HistoryUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing settings read is unavailable rather than a crash`() = runTest {
        theHabit()
        settings.readFailure = IllegalStateException("the preferences file is unreadable")

        // Throws on subscription too, so Loading is gone before collection —
        // see the habit-read case above.
        historyFor(habitId(1).value).uiState.test {
            assertEquals(HistoryUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
