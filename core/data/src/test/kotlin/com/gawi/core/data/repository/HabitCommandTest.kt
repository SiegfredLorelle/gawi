package com.gawi.core.data.repository

import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.domain.command.CommandError
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.testing.habitId
import com.gawi.core.domain.testing.metadata
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Commands, end to end: what lands in the log, what lands in the derived
 * tables, and what a rejection leaves behind — which must be nothing.
 */
@RunWith(RobolectricTestRunner::class)
class HabitCommandTest {

    private lateinit var store: TestStore

    @Before
    fun setUp() {
        store = TestStore.create()
    }

    @After
    fun tearDown() = store.close()

    private suspend fun createHabit(name: String = "read", schedule: Schedule = Schedule.Daily): HabitId =
        (store.repository.createHabit(metadata(name, schedule)) as CommandResult.Accepted).payload

    private suspend fun eventCount(): Int = store.database.eventDao().count()

    private fun rejectedWith(result: CommandResult<*>, error: CommandError) {
        assertEquals(CommandResult.Rejected(error), result)
    }

    @Test
    fun `creating a habit mints a canonical id and writes one event and one row`() = runTest {
        val habitId = createHabit()

        assertTrue(HabitId(habitId.value).value.isNotEmpty())
        assertEquals(1, eventCount())
        assertEquals(habitId.value, store.snapshot().habits.single().habitId)
    }

    @Test
    fun `a blank name is rejected and appends nothing`() = runTest {
        val result = store.repository.createHabit(metadata(name = "  "))

        rejectedWith(result, CommandError.BlankName)
        assertEquals(0, eventCount())
        assertEquals(emptyList<Any>(), store.snapshot().habits)
    }

    @Test
    fun `a completion outside the retro window is rejected and appends nothing`() = runTest {
        val habit = createHabit()
        val before = eventCount()

        val result = store.repository.addCompletion(habit, store.today().minusDays(4))

        rejectedWith(result, CommandError.RetroWindowExceeded)
        assertEquals(before, eventCount())
        assertEquals(emptyList<Any>(), store.snapshot().completions)
    }

    @Test
    fun `the edge of the retro window is accepted`() = runTest {
        val habit = createHabit()

        val result = store.repository.addCompletion(habit, store.today().minusDays(3))

        assertTrue(result is CommandResult.Accepted)
        assertEquals(1, store.snapshot().completions.size)
    }

    @Test
    fun `a future logical date is rejected`() = runTest {
        val habit = createHabit()

        val result = store.repository.addCompletion(habit, store.today().plusDays(1))

        rejectedWith(result, CommandError.FutureLogicalDate)
        assertEquals(emptyList<Any>(), store.snapshot().completions)
    }

    @Test
    fun `completing an archived habit is rejected`() = runTest {
        val habit = createHabit()
        store.repository.archiveHabit(habit)
        val before = eventCount()

        val result = store.repository.addCompletion(habit, store.today())

        rejectedWith(result, CommandError.HabitIsArchived)
        assertEquals(before, eventCount())
    }

    @Test
    fun `undoing an empty cell is rejected and touches nothing`() = runTest {
        val habit = createHabit()
        val before = eventCount()

        val result = store.repository.undoCompletion(habit, store.today())

        rejectedWith(result, CommandError.CompletionNotFound)
        assertEquals(before, eventCount())
    }

    @Test
    fun `annotating a cell with no live completion is rejected without touching the log`() = runTest {
        val habit = createHabit()
        val before = eventCount()

        val result = store.repository.updateNote(habit, store.today(), "thought")

        rejectedWith(result, CommandError.CompletionNotFound)
        assertEquals(before, eventCount())
    }

    @Test
    fun `an archived habit reports archived whether or not the cell is completed`() = runTest {
        // The domain checks archived before liveness precisely so the error
        // does not depend on completion state. This layer has to resolve an
        // event id before it can call the domain at all, so it is the one that
        // can get the precedence wrong.
        val completed = createHabit("completed")
        val empty = createHabit("empty")
        store.repository.addCompletion(completed, store.today())
        store.repository.archiveHabit(completed)
        store.repository.archiveHabit(empty)

        rejectedWith(store.repository.updateNote(completed, store.today(), "x"), CommandError.HabitIsArchived)
        rejectedWith(store.repository.updateNote(empty, store.today(), "x"), CommandError.HabitIsArchived)
        rejectedWith(store.repository.undoCompletion(empty, store.today()), CommandError.HabitIsArchived)
    }

    @Test
    fun `add then undo then add leaves one completion, the new note, and three events`() = runTest {
        val habit = createHabit()
        val today = store.today()

        store.repository.addCompletion(habit, today, note = "first")
        store.repository.undoCompletion(habit, today)
        store.repository.addCompletion(habit, today, note = "second")

        // create + add + tombstone + add
        assertEquals(4, eventCount())
        val completions = store.snapshot().completions
        assertEquals(1, completions.size)
        assertEquals("second", completions.single().note)
    }

    @Test
    fun `undo tombstones every live add in the cell in one transaction`() = runTest {
        val habit = createHabit()
        val today = store.today()
        store.repository.addCompletion(habit, today)
        // A second add for the same cell is a no-op for the projector, so the
        // interesting count here is the events, not the rows.
        store.repository.addCompletion(habit, today)
        val beforeUndo = eventCount()

        store.repository.undoCompletion(habit, today)

        assertTrue(eventCount() > beforeUndo)
        assertEquals(emptyList<Any>(), store.snapshot().completions)
    }

    @Test
    fun `commands against an unknown habit are rejected and append nothing`() = runTest {
        val unknown = habitId(404)

        rejectedWith(store.repository.updateHabit(unknown, metadata()), CommandError.HabitNotFound)
        rejectedWith(store.repository.archiveHabit(unknown), CommandError.HabitNotFound)
        rejectedWith(store.repository.unarchiveHabit(unknown), CommandError.HabitNotFound)
        rejectedWith(store.repository.addCompletion(unknown, store.today()), CommandError.HabitNotFound)
        assertEquals(0, eventCount())
    }

    @Test
    fun `an archived habit can still be edited`() = runTest {
        // Only completing is gated on archived; renaming something you have put
        // away is reasonable, and the domain allows it deliberately.
        val habit = createHabit()
        store.repository.archiveHabit(habit)

        val result = store.repository.updateHabit(habit, metadata(name = "renamed"))

        assertTrue(result is CommandResult.Accepted)
        assertEquals("renamed", store.snapshot().habits.single().name)
    }

    @Test
    fun `unarchiving restores the habit to the today list`() = runTest {
        val habit = createHabit()
        store.repository.archiveHabit(habit)
        assertEquals(true, store.snapshot().habits.single().archived)

        store.repository.unarchiveHabit(habit)

        assertEquals(false, store.snapshot().habits.single().archived)
    }

    @Test
    fun `a note clear stores null rather than an empty string`() = runTest {
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today(), note = "something")

        store.repository.updateNote(habit, store.today(), "")

        assertNull(store.snapshot().completions.single().note)
    }

    @Test
    fun `a non-midnight cutoff decides which day a tap belongs to`() = runTest {
        // 01:30 with a 03:00 cutoff is still the previous day (architecture §5).
        store.settings.settings = store.settings.settings.copy(dayCutoff = java.time.LocalTime.of(3, 0))
        store.clock.instant = java.time.Instant.parse("2026-08-17T01:30:00Z")
        val habit = createHabit()

        val today = store.today()

        assertEquals(java.time.LocalDate.parse("2026-08-16"), today)
        store.repository.addCompletion(habit, today)
        assertEquals("2026-08-16", store.snapshot().completions.single().logicalDate)
    }

    @Test
    fun `an append cancelled mid-commit still advances the command authority`() = runTest {
        // Its own store: the cancellation has to fire from inside the projection
        // transaction, which is the one place a test can reach the window
        // between the event committing and the in-memory state being published.
        var append: Job? = null
        val store = TestStore.create(onCompletionWrite = { append?.cancel() })
        try {
            val habit = (store.repository.createHabit(metadata()) as CommandResult.Accepted).payload

            append = launch { store.repository.addCompletion(habit, store.today()) }
            append.join()

            // The commit is NonCancellable, so the event landed.
            assertEquals(1, store.database.completionProjectionDao().all().size)

            // And the command authority went with it. This is the assertion that
            // matters: skip the publish and `state` stays a whole event behind
            // the log for the life of the process, so this undo would be
            // rejected against a row the user can still see.
            assertTrue(store.repository.undoCompletion(habit, store.today()) is CommandResult.Accepted)
        } finally {
            store.close()
        }
    }
}
