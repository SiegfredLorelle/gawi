package com.gawi.feature.habits

import app.cash.turbine.test
import com.gawi.feature.habits.testsupport.FakeHabitRepository
import com.gawi.feature.habits.testsupport.MainDispatcherRule
import com.gawi.feature.habits.testsupport.habitId
import com.gawi.feature.habits.testsupport.habitState
import com.gawi.feature.habits.testsupport.todayHabit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Which state habit detail lands in, for each way the read can go.
 *
 * The three failure paths matter more than the happy one here: two of them are
 * reachable from a route argument alone, and the third would otherwise take the
 * process with it.
 */
class HabitDetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeHabitRepository()

    private fun detailFor(rawHabitId: String) = HabitDetailViewModel(rawHabitId, repository)

    /**
     * The screen draws blank before the first read comes back.
     *
     * Asserted off `value` rather than as the first collected item, because
     * `observeHabit` resolves synchronously on subscription under the unconfined
     * dispatcher — by the time a collector sees anything the state has already
     * moved on. `value` before anyone subscribes is the honest way to ask what
     * `stateIn` starts at, and every other test here skips Loading for the same
     * reason `HabitListViewModelTest` does on its failing-read case.
     */
    @Test
    fun `detail starts blank rather than on a habit it has not read`() = runTest {
        repository.habit = todayHabit(habitState(id = habitId(1)))

        assertEquals(HabitDetailUiState.Loading, detailFor(habitId(1).value).uiState.value)
    }

    @Test
    fun `a habit resolves to its detail`() = runTest {
        repository.habit = todayHabit(habitState(id = habitId(1), name = "read"))

        detailFor(habitId(1).value).uiState.test {
            assertEquals("read", (awaitItem() as HabitDetailUiState.Detail).name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A malformed id never reaches the repository.
     *
     * `HabitId` rejects anything that is not a canonical UUIDv7 by throwing, and
     * a route argument is exactly where one can arrive. Asking the repository
     * anyway would be harmless but dishonest; landing on `Unavailable` is what
     * turns the bad route into a screen rather than a crash in the constructor.
     */
    @Test
    fun `a malformed id is unavailable and is never looked up`() = runTest {
        detailFor("not-a-uuid").uiState.test {
            assertEquals(HabitDetailUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(repository.observedIds.isEmpty())
    }

    /** A well-formed id for a habit that is not there lands in the same place. */
    @Test
    fun `an unknown habit is unavailable`() = runTest {
        repository.habit = null

        detailFor(habitId(1).value).uiState.test {
            assertEquals(HabitDetailUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(habitId(1)), repository.observedIds)
    }

    /**
     * A throwing read is caught, not fatal.
     *
     * `viewModelScope` is a `SupervisorJob` with no `CoroutineExceptionHandler`,
     * so without the `.catch` this is process death on opening a habit rather
     * than a screen saying it cannot show one. The real read can genuinely
     * throw: a fresh install repairs the projection on its first read, and that
     * repair asks the settings store for an answer it refuses to guess at.
     */
    @Test
    fun `a failed read is unavailable rather than a crash`() = runTest {
        repository.habitFailure = IllegalStateException("settings unreadable")

        detailFor(habitId(1).value).uiState.test {
            assertEquals(HabitDetailUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Detail can see an archived habit, unlike the Today list.
     *
     * `observeHabit`'s KDoc is explicit that asking for one habit by id does not
     * filter archived ones, "since unarchiving has to be reachable". A detail
     * screen that showed `Unavailable` for an archived habit would make the one
     * you had put away the one you could not look at.
     */
    @Test
    fun `an archived habit is shown, and says so`() = runTest {
        repository.habit = todayHabit(habitState(id = habitId(1), archived = true))

        detailFor(habitId(1).value).uiState.test {
            assertTrue((awaitItem() as HabitDetailUiState.Detail).archived)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
