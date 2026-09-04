package com.gawi.feature.habits

import app.cash.turbine.test
import com.gawi.core.domain.command.CommandError
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import com.gawi.core.testing.FakeHabitRepository
import com.gawi.core.testing.MainDispatcherRule
import com.gawi.core.testing.habitState
import com.gawi.core.testing.todayHabit
import kotlinx.coroutines.test.TestScope
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
     * Detail resolves the habit it was asked for, not whichever one is around.
     *
     * A well-formed id for a *different* habit is as unavailable as an unknown
     * one. Worth its own case because the fake used to answer every request
     * with its single fixture, which would let a screen reading the wrong habit
     * pass this suite unchallenged.
     */
    @Test
    fun `a well-formed id for another habit is unavailable`() = runTest {
        repository.habit = todayHabit(habitState(id = habitId(1)))

        detailFor(habitId(2).value).uiState.test {
            assertEquals(HabitDetailUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(habitId(2)), repository.observedIds)
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

    // ---- writing from the strip ----

    private suspend fun TestScope.detailWriting(): HabitDetailViewModel {
        repository.habit = todayHabit(habitState(id = habitId(1)))
        return detailFor(habitId(1).value)
    }

    /**
     * A tap writes to the date the cell carried, not to today.
     *
     * The 3-day window *accepts* a date one day stale rather than refusing it,
     * so a ViewModel that re-derived "now" would log the wrong day and report
     * success. That is why the date travels with the tap.
     */
    @Test
    fun `completing a past day writes to that day`() = runTest {
        val detail = detailWriting()

        detail.onToggle(habitId(1), FIXED_DATE.minusDays(2), completed = false)

        assertEquals(listOf(Triple(habitId(1), FIXED_DATE.minusDays(2), null)), repository.completed)
        assertTrue(repository.undone.isEmpty())
    }

    /** And the state the cell was drawn in decides the direction. */
    @Test
    fun `un-completing a day undoes rather than adding again`() = runTest {
        val detail = detailWriting()

        detail.onToggle(habitId(1), FIXED_DATE.minusDays(2), completed = true)

        assertEquals(listOf(habitId(1) to FIXED_DATE.minusDays(2)), repository.undone)
        assertTrue(repository.completed.isEmpty())
    }

    /**
     * A rejection reaches the user as its own message.
     *
     * All six CommandErrors are reachable from this module now. Before the
     * strip, the four completion errors were mapped to one "that did not work",
     * which would have said nothing about a day gone out of range.
     */
    @Test
    fun `a rejected write is reported with the error's own copy`() = runTest {
        val detail = detailWriting()
        repository.result = CommandResult.Rejected(CommandError.RetroWindowExceeded)

        detail.events.test {
            detail.onToggle(habitId(1), FIXED_DATE.minusDays(2), completed = false)
            assertEquals(HabitsMessage(R.string.habits_error_retro_window), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A throwing write is reported, not fatal.
     *
     * appendLocked consults SettingsSource.current() on every write, and that
     * refuses to guess when the preferences file cannot be read. Uncaught, that
     * is process death on tapping a day.
     */
    @Test
    fun `a throwing write is reported rather than crashing`() = runTest {
        val detail = detailWriting()
        repository.commandFailure = IllegalStateException("settings unreadable")

        detail.events.test {
            detail.onToggle(habitId(1), FIXED_DATE, completed = false)
            assertEquals(HabitsMessage(R.string.habits_error_unexpected), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A double tap on a ticked cell says nothing.
     *
     * `CompletionNotFound` on an undo means the day is already in the state the
     * tap asked for — a second tap, or a cell the widget cleared first. Saying
     * "that day is no longer logged" would be a message about a non-event, and
     * `:feature:today` drops it on the same path for the same reason.
     */
    @Test
    fun `an undo that finds nothing is not reported`() = runTest {
        val detail = detailWriting()
        repository.result = CommandResult.Rejected(CommandError.CompletionNotFound)

        detail.events.test {
            detail.onToggle(habitId(1), FIXED_DATE, completed = true)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** But every other rejection on that path still is. */
    @Test
    fun `other rejections on a toggle are still reported`() = runTest {
        val detail = detailWriting()
        repository.result = CommandResult.Rejected(CommandError.HabitIsArchived)

        detail.events.test {
            detail.onToggle(habitId(1), FIXED_DATE, completed = false)
            assertEquals(HabitsMessage(R.string.habits_error_archived), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- the note ----

    @Test
    fun `a note is written against the day it belongs to`() = runTest {
        val detail = detailWriting()

        detail.onNote(habitId(1), FIXED_DATE.minusDays(2), "went far")

        assertEquals(listOf(Triple(habitId(1), FIXED_DATE.minusDays(2), "went far")), repository.notes)
    }

    /**
     * Clearing writes an empty note rather than writing nothing.
     *
     * architecture §4: an empty note is a real write that clears the note and
     * wins last-write-wins like any other. Skipping the write as an
     * optimisation would leave an older note winning and the clear undone.
     */
    @Test
    fun `clearing a note is a write, not a skipped one`() = runTest {
        val detail = detailWriting()

        detail.onNote(habitId(1), FIXED_DATE, "")

        assertEquals(listOf(Triple(habitId(1), FIXED_DATE, "")), repository.notes)
    }

    /** A note on a day whose completion has gone says so rather than staying silent. */
    @Test
    fun `a note against a vanished completion is reported`() = runTest {
        val detail = detailWriting()
        repository.result = CommandResult.Rejected(CommandError.CompletionNotFound)

        detail.events.test {
            detail.onNote(habitId(1), FIXED_DATE, "went far")
            assertEquals(HabitsMessage(R.string.habits_error_completion_missing), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
