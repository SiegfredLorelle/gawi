package com.gawi.feature.today

import app.cash.turbine.test
import com.gawi.core.domain.command.CommandError
import com.gawi.core.domain.command.CommandResult
import com.gawi.feature.today.testsupport.FakeHabitRepository
import com.gawi.feature.today.testsupport.MainDispatcherRule
import com.gawi.feature.today.testsupport.TODAY
import com.gawi.feature.today.testsupport.Toggle
import com.gawi.feature.today.testsupport.habitId
import com.gawi.feature.today.testsupport.todayHabit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The ViewModel's own decisions: what it shows before it has looked, which
 * command a tap becomes, and which rejections are worth telling the user about.
 */
class TodayViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeHabitRepository()

    /**
     * Lazily, and this matters. `viewModelScope` resolves its dispatcher when
     * the ViewModel is constructed, and JUnit runs field initialisers before it
     * applies rules — so building it eagerly would bind the scope before
     * [MainDispatcherRule] has installed a test dispatcher, and every command
     * would run on a real thread and race the assertion about it.
     */
    private val viewModel by lazy { TodayViewModel(repository) }

    @Test
    fun `the screen does not claim there are no habits before it has looked`() = runTest {
        // WhileSubscribed means the upstream starts on collection, so reading
        // uiState.value alone would sit on Loading for ever. Every assertion
        // about state has to go through a collection.
        viewModel.uiState.test {
            assertEquals(TodayUiState.Loading, awaitItem())

            repository.emit(listOf(todayHabit(name = "read")))

            val state = awaitItem() as TodayUiState.Habits
            assertEquals("read", state.rows.single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing read path leaves the screen standing`() = runTest {
        // Reachable on a first launch: the projection repair calls
        // SettingsSource.current, which refuses rather than guessing when the
        // preferences file cannot be read. Uncaught, that would escape stateIn's
        // sharing coroutine and take the process down on the only screen.
        repository.failure = IOException("settings unreadable")

        viewModel.uiState.test {
            // No Loading first: the upstream fails on subscription, so stateIn
            // has replaced its initial value before anything can observe it.
            assertEquals(TodayUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping an unticked row completes it for the day it was drawn for`() = runTest {
        viewModel.uiState.test {
            skipItems(1)
            repository.emit(listOf(todayHabit(id = habitId(1))))
            awaitItem()

            viewModel.onToggle(habitId(1), completed = false, logicalDate = TODAY)

            assertEquals(listOf(Toggle(habitId(1), TODAY, undo = false)), repository.toggles)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a ticked row undoes it`() = runTest {
        viewModel.onToggle(habitId(1), completed = true, logicalDate = TODAY)

        assertEquals(listOf(Toggle(habitId(1), TODAY, undo = true)), repository.toggles)
    }

    @Test
    fun `a rejected tap is reported once`() = runTest {
        repository.result = CommandResult.Rejected(CommandError.RetroWindowExceeded)

        viewModel.events.test {
            viewModel.onToggle(habitId(1), completed = false, logicalDate = TODAY)

            assertEquals(TodayMessage(R.string.today_error_retro_window), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `two rejected taps are reported twice, not conflated`() = runTest {
        repository.result = CommandResult.Rejected(CommandError.RetroWindowExceeded)

        viewModel.events.test {
            viewModel.onToggle(habitId(1), completed = false, logicalDate = TODAY)
            viewModel.onToggle(habitId(1), completed = false, logicalDate = TODAY)

            // Held as state, the second would be swallowed as a duplicate. Two
            // taps refused is two things the user needs told.
            assertEquals(TodayMessage(R.string.today_error_retro_window), awaitItem())
            assertEquals(TodayMessage(R.string.today_error_retro_window), awaitItem())
        }
    }

    @Test
    fun `undoing something already undone says nothing`() = runTest {
        // What a double tap produces. The cell is already as the tap asked, so
        // there is no event to report.
        repository.result = CommandResult.Rejected(CommandError.CompletionNotFound)

        viewModel.events.test {
            viewModel.onToggle(habitId(1), completed = true, logicalDate = TODAY)

            expectNoEvents()
        }
        assertTrue(repository.toggles.isNotEmpty())
    }
}
