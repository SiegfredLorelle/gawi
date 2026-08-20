package com.gawi.feature.habits

import app.cash.turbine.test
import com.gawi.core.domain.command.CommandError
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.Schedule
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

/** What the editor loads, what it submits, and which command it submits it as. */
class HabitEditorViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeHabitRepository()

    // by lazy for the reason HabitListViewModelTest gives: field initialisers
    // run before rules, so an eager ViewModel binds the wrong dispatcher.
    private fun editorFor(rawHabitId: String?) = HabitEditorViewModel(rawHabitId, repository)

    @Test
    fun `creating opens straight onto an empty form`() = runTest {
        val state = editorFor(null).uiState.value as HabitEditorUiState.Form

        assertEquals(false, state.editing)
        assertEquals("", state.name)
    }

    @Test
    fun `editing loads the habit into the form`() = runTest {
        val habit = habitState(id = habitId(1), name = "read", schedule = Schedule.Weekly(4), tag = "growth")
        repository.habit = todayHabit(habit)

        val state = editorFor(habitId(1).value).uiState.value as HabitEditorUiState.Form

        assertTrue(state.editing)
        assertEquals("read", state.name)
        assertEquals(ScheduleUi.Weekly(4), state.schedule)
        assertEquals("growth", state.tag)
    }

    @Test
    fun `an id that resolves to nothing is unavailable, not an empty form`() = runTest {
        repository.habit = null

        assertEquals(HabitEditorUiState.Unavailable, editorFor(habitId(1).value).uiState.value)
    }

    /**
     * A malformed id never reaches [com.gawi.core.domain.model.HabitId].
     *
     * That constructor rejects anything that is not a canonical UUIDv7 by
     * throwing, so validating it in the ViewModel is what turns a bad route into
     * a state the screen can draw instead of a crash on the way to it. The read
     * is skipped entirely, which is what the empty observedIds asserts.
     */
    @Test
    fun `a malformed id is unavailable rather than a thrown constructor`() = runTest {
        assertEquals(HabitEditorUiState.Unavailable, editorFor("not-a-uuid").uiState.value)
        assertTrue(repository.observedIds.isEmpty())
    }

    @Test
    fun `a failing read is unavailable rather than an exception`() = runTest {
        repository.habitFailure = IllegalStateException("the log is corrupt")

        assertEquals(HabitEditorUiState.Unavailable, editorFor(habitId(1).value).uiState.value)
    }

    @Test
    fun `an edit replaces the form and nothing else`() = runTest {
        val editor = editorFor(null)
        val before = editor.uiState.value as HabitEditorUiState.Form

        editor.onEdit(before.copy(name = "read"))

        assertEquals("read", (editor.uiState.value as HabitEditorUiState.Form).name)
    }

    @Test
    fun `saving a new habit creates it and says so`() = runTest {
        val editor = editorFor(null)
        editor.onEdit((editor.uiState.value as HabitEditorUiState.Form).copy(name = "read"))

        editor.events.test {
            editor.onSave()
            assertEquals(HabitEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("read", repository.created.single().name)
        assertTrue(repository.updated.isEmpty())
    }

    /**
     * How the editor was opened decides the command, not how the form looks.
     *
     * A create that fell through to `updateHabit` would be rejected with
     * `HabitNotFound`, and an update that fell through to `createHabit` would
     * silently make a second habit — so this is the one branch worth pinning
     * from both sides.
     */
    @Test
    fun `saving an existing habit updates it rather than creating a second`() = runTest {
        repository.habit = todayHabit(habitState(id = habitId(1), name = "read"))
        val editor = editorFor(habitId(1).value)
        editor.onEdit((editor.uiState.value as HabitEditorUiState.Form).copy(name = "read more"))

        editor.events.test {
            editor.onSave()
            assertEquals(HabitEditorEvent.Saved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        val (id, metadata) = repository.updated.single()
        assertEquals(habitId(1), id)
        assertEquals("read more", metadata.name)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `an update submits every field, because it is not a patch`() = runTest {
        repository.habit = todayHabit(habitState(id = habitId(1), name = "read", tag = "growth"))
        val editor = editorFor(habitId(1).value)
        val loaded = editor.uiState.value as HabitEditorUiState.Form

        editor.onEdit(loaded.copy(name = "read more"))
        editor.onSave()

        // Only the name was touched, but the whole record goes, so the tag and
        // the icon survive rather than being cleared by an absent field.
        val (_, metadata) = repository.updated.single()
        assertEquals("read more", metadata.name)
        assertEquals("growth", metadata.tag)
        assertEquals("📖", metadata.icon)
    }

    /**
     * The blank-name guard is enforced, not just displayed.
     *
     * A disabled Save button and the domain's `isBlank` are two statements of
     * one rule, and only one of them is enforcement. This is the ViewModel
     * refusing rather than relying on the screen having got it right.
     */
    @Test
    fun `saving a blank name writes nothing and says why`() = runTest {
        val editor = editorFor(null)

        editor.events.test {
            editor.onSave()
            assertEquals(HabitEditorEvent.Rejected(HabitsMessage(R.string.habits_error_blank_name)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `a whitespace name is blank too`() = runTest {
        val editor = editorFor(null)
        editor.onEdit((editor.uiState.value as HabitEditorUiState.Form).copy(name = "   "))

        editor.events.test {
            editor.onSave()
            assertEquals(HabitEditorEvent.Rejected(HabitsMessage(R.string.habits_error_blank_name)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `a rejected save reports the domain's reason and does not close`() = runTest {
        repository.result = CommandResult.Rejected(CommandError.HabitNotFound)
        repository.habit = todayHabit(habitState(id = habitId(1)))
        val editor = editorFor(habitId(1).value)
        editor.onEdit((editor.uiState.value as HabitEditorUiState.Form).copy(name = "read"))

        editor.events.test {
            editor.onSave()
            assertEquals(HabitEditorEvent.Rejected(HabitsMessage(R.string.habits_error_habit_missing)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saving while unavailable does nothing at all`() = runTest {
        repository.habit = null
        val editor = editorFor(habitId(1).value)

        editor.onSave()

        assertTrue(repository.created.isEmpty())
        assertTrue(repository.updated.isEmpty())
    }

    /**
     * The weekly clamp, from the ViewModel's side.
     *
     * The stepper stops at seven, so this is the case where something else set
     * the number: it must be saved coerced rather than throwing out of
     * `Schedule.Weekly`'s `require` on the way to the repository.
     */
    @Test
    fun `an out-of-range weekly target is saved clamped, not thrown`() = runTest {
        val editor = editorFor(null)
        val form = editor.uiState.value as HabitEditorUiState.Form

        editor.onEdit(form.copy(name = "swim", schedule = ScheduleUi.Weekly(99)))
        editor.onSave()

        assertEquals(Schedule.Weekly(7), repository.created.single().schedule)
    }
}
