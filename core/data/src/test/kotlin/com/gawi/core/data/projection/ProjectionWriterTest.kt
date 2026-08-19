package com.gawi.core.data.projection

import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.data.testsupport.metadata
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The delta claim: an append moves the rows it must and leaves the rest byte
 * for byte alone.
 *
 * This is the class that fails if someone replaces the writer with a full
 * re-projection, or drops the compare-before-write that keeps an idempotent
 * command from waking every observer of a table.
 */
@RunWith(RobolectricTestRunner::class)
class ProjectionWriterTest {

    private lateinit var store: TestStore

    @Before
    fun setUp() {
        store = TestStore.create()
    }

    @After
    fun tearDown() = store.close()

    private suspend fun createHabit(name: String, schedule: Schedule = Schedule.Daily): HabitId {
        val result = store.repository.createHabit(metadata(name, schedule))
        return (result as CommandResult.Accepted).payload
    }

    @Test
    fun `completing one habit leaves another habit's rows untouched`() = runTest {
        val a = createHabit("a")
        val b = createHabit("b")
        val before = store.snapshot()

        store.repository.addCompletion(a, store.today())

        val after = store.snapshot()
        assertEquals(
            before.habits.filter { it.habitId == b.value },
            after.habits.filter { it.habitId == b.value },
        )
        assertEquals(
            before.streaks.filter { it.habitId == b.value },
            after.streaks.filter { it.habitId == b.value },
        )
        assertEquals(
            before.completions.filter { it.habitId == b.value },
            after.completions.filter { it.habitId == b.value },
        )
    }

    @Test
    fun `completing a habit writes exactly its own cell and streak`() = runTest {
        val habit = createHabit("a")

        store.repository.addCompletion(habit, store.today())

        val after = store.snapshot()
        assertEquals(1, after.completions.size)
        assertEquals(habit.value, after.completions.single().habitId)
        assertEquals(store.today().toString(), after.completions.single().logicalDate)
        assertEquals(1, after.streaks.single { it.habitId == habit.value }.currentStreak)
    }

    @Test
    fun `re-completing an already completed cell changes nothing at all`() = runTest {
        val habit = createHabit("a")
        store.repository.addCompletion(habit, store.today())
        val before = store.snapshot()

        // The projector accepts a duplicate add silently; the tables must too.
        // A blind upsert here would rewrite the same values and, because Room
        // invalidates per table, wake every observer for nothing.
        store.repository.addCompletion(habit, store.today())

        assertEquals(before, store.snapshot())
    }

    @Test
    fun `undo deletes the completion row rather than flagging it`() = runTest {
        val habit = createHabit("a")
        store.repository.addCompletion(habit, store.today())

        store.repository.undoCompletion(habit, store.today())

        assertEquals(emptyList<Any>(), store.snapshot().completions)
    }

    @Test
    fun `changing the schedule re-denominates the streak with no completion moving`() = runTest {
        val habit = createHabit("a", Schedule.Daily)
        store.repository.addCompletion(habit, store.today())
        val before = store.snapshot()
        assertEquals(1, before.streaks.single().currentStreak)

        // One completion is a 1-day streak but not yet a 1-week streak at 3/wk.
        store.repository.updateHabit(habit, metadata("a", Schedule.Weekly(3)))

        val after = store.snapshot()
        assertEquals(before.completions, after.completions)
        assertNotEquals(before.streaks, after.streaks)
        assertEquals(0, after.streaks.single().currentStreak)
    }

    @Test
    fun `a habit's row carries its schedule in two columns`() = runTest {
        val daily = createHabit("d", Schedule.Daily)
        val weekly = createHabit("w", Schedule.Weekly(3))

        val rows = store.snapshot().habits.associateBy { it.habitId }
        assertEquals("daily", rows.getValue(daily.value).scheduleKind)
        assertNull(rows.getValue(daily.value).timesPerWeek)
        assertEquals("weekly", rows.getValue(weekly.value).scheduleKind)
        assertEquals(3, rows.getValue(weekly.value).timesPerWeek)
    }

    @Test
    fun `a note write moves only the cell, and clearing it is a real write`() = runTest {
        val habit = createHabit("a")
        store.repository.addCompletion(habit, store.today(), note = "first")
        val withNote = store.snapshot()
        assertEquals("first", withNote.completions.single().note)

        store.repository.updateNote(habit, store.today(), "")

        val cleared = store.snapshot()
        assertEquals(withNote.habits, cleared.habits)
        assertEquals(withNote.streaks, cleared.streaks)
        assertNull(cleared.completions.single().note)
    }

    @Test
    fun `archiving writes the habit row and nothing else`() = runTest {
        val habit = createHabit("a")
        store.repository.addCompletion(habit, store.today())
        val before = store.snapshot()

        store.repository.archiveHabit(habit)

        val after = store.snapshot()
        assertEquals(before.completions, after.completions)
        assertEquals(before.streaks, after.streaks)
        assertEquals(true, after.habits.single().archived)
    }
}
