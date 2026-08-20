package com.gawi.feature.habits

import app.cash.turbine.test
import com.gawi.core.domain.command.CommandError
import com.gawi.core.domain.command.CommandResult
import com.gawi.feature.habits.testsupport.FakeHabitRepository
import com.gawi.feature.habits.testsupport.MainDispatcherRule
import com.gawi.feature.habits.testsupport.habitId
import com.gawi.feature.habits.testsupport.habitState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Which state the list emits, and which command a row's action sends. */
class HabitListViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeHabitRepository()

    // by lazy, not a field initialiser. JUnit runs field initialisers before it
    // applies rules, so an eager ViewModel would bind viewModelScope to the real
    // main dispatcher before MainDispatcherRule installs the test one.
    private val viewModel by lazy { HabitListViewModel(repository) }

    @Test
    fun `it starts loading, before it has looked`() = runTest {
        viewModel.uiState.test {
            assertEquals(HabitListUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no habits becomes the empty state`() = runTest {
        viewModel.uiState.test {
            assertEquals(HabitListUiState.Loading, awaitItem())
            repository.emit(emptyList())
            assertEquals(HabitListUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `habits arrive split into active and archived`() = runTest {
        viewModel.uiState.test {
            assertEquals(HabitListUiState.Loading, awaitItem())
            repository.emit(
                listOf(
                    habitState(id = habitId(1), name = "read"),
                    habitState(id = habitId(2), name = "swim", archived = true),
                ),
            )

            val state = awaitItem() as HabitListUiState.Habits
            assertEquals(listOf("read"), state.active.map { it.name })
            assertEquals(listOf("swim"), state.archived.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A failing read is a state, not a crash.
     *
     * Without it the exception leaves the sharing coroutine and takes the
     * process down — the same reason Today has this state. A fresh install
     * repairs the projection on its first read, and that repair asks the
     * settings store for an answer it refuses to guess at.
     */
    @Test
    fun `a failing read becomes unavailable rather than an exception`() = runTest {
        repository.listFailure = IllegalStateException("the settings file cannot be read")

        viewModel.uiState.test {
            // No Loading first: the upstream fails on subscription, so stateIn
            // has replaced its initial value before anything can observe it.
            assertEquals(HabitListUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archiving an active habit archives it`() = runTest {
        viewModel.onArchiveToggle(habitId(1), archived = false)

        assertEquals(listOf(habitId(1)), repository.archived)
        assertTrue(repository.unarchived.isEmpty())
    }

    /**
     * The state travels with the tap.
     *
     * The row reports what it was drawn as, rather than the ViewModel reading
     * the current value back — so what is transmitted is the intent the user
     * expressed, which is the same rule Today's toggle follows.
     */
    @Test
    fun `an archived habit is brought back rather than archived again`() = runTest {
        viewModel.onArchiveToggle(habitId(2), archived = true)

        assertEquals(listOf(habitId(2)), repository.unarchived)
        assertTrue(repository.archived.isEmpty())
    }

    @Test
    fun `a rejected archive is reported once, with copy`() = runTest {
        repository.result = CommandResult.Rejected(CommandError.HabitNotFound)

        viewModel.events.test {
            viewModel.onArchiveToggle(habitId(1), archived = false)
            assertEquals(HabitsMessage(R.string.habits_error_habit_missing), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an accepted archive says nothing`() = runTest {
        viewModel.events.test {
            viewModel.onArchiveToggle(habitId(1), archived = false)
            expectNoEvents()
        }
    }
}
