package com.gawi.core.testing

import com.gawi.core.domain.testing.habitId
import com.gawi.core.domain.testing.uuid
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The properties every module's tests lean on without saying so. */
class FixturesTest {

    @Test
    fun `a fixture uuid is canonical and ordered like its number`() {
        val ids = (1..300).map(::uuid)
        ids.forEach { assertTrue(it, Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-8[0-9a-f]{3}-[0-9a-f]{12}").matches(it)) }
        assertEquals(ids, ids.sorted())
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `the fake answers a single read only for the habit it was given`() = runTest {
        val repository = FakeHabitRepository().apply { habit = todayHabit(id = habitId(1), name = "read") }
        assertEquals("read", repository.observeHabit(habitId(1)).first()?.habit?.name)
        assertNull(repository.observeHabit(habitId(2)).first())
        assertEquals(listOf(habitId(1), habitId(2)), repository.observedIds)
    }

    /**
     * A ViewModel that maps `observeToday()` in a property initialiser builds
     * the flow before a test has configured the fake. Deciding hot-or-cold at
     * call time would freeze it on the hot path and leave the test waiting on
     * an emission that never comes, so the decision belongs at collection.
     */
    @Test
    fun `a failure set after the flow is built still reaches the collector`() = runTest {
        val repository = FakeHabitRepository()
        val flow = repository.observeToday()

        repository.failWith = IllegalStateException("settings unreadable")

        assertEquals("settings unreadable", runCatching { flow.first() }.exceptionOrNull()?.message)
    }

    /** The same for a snapshot: set it late and the read is still the cold one. */
    @Test
    fun `a snapshot set after the flow is built is what the collector sees`() = runTest {
        val repository = FakeHabitRepository()
        val flow = repository.observeToday()

        repository.snapshot = todaySnapshot(habits = listOf(todayHabit(name = "read")))

        assertEquals(listOf("read"), flow.first().habits.map { it.habit.name })
    }

    /**
     * The other half of the rule the two cases above pin: what a screen *asked
     * for* is recorded when it asks, so a flow built and never collected still
     * counts. `InsightsViewModelTest` and `HistoryViewModelTest` assert exact
     * call sequences and counts against that.
     */
    @Test
    fun `what was asked for is recorded at the call, not at the collection`() = runTest {
        val repository = FakeHabitRepository()

        repository.observeHabit(habitId(1))
        repository.observeTagEffort(FIXED_DATE, FIXED_DATE)

        assertEquals(listOf(habitId(1)), repository.observedIds)
        assertEquals(listOf(FIXED_DATE..FIXED_DATE), repository.ranges)
    }

    /** And the answer is still decided at collection, for every read and not just today's. */
    @Test
    fun `a ranged read set up after the flow is built still answers with it`() = runTest {
        val repository = FakeHabitRepository()
        val flow = repository.observeCompletedDates(habitId(1), FIXED_DATE, FIXED_DATE)

        repository.completedDates = mapOf(FIXED_DATE to "done")

        assertEquals(mapOf(FIXED_DATE to "done"), flow.first())
    }

    @Test
    fun `a member named unreachable that does not exist is refused at construction`() {
        val thrown = runCatching { FakeHabitRepository(unreachable = setOf("observeHabitdetail")) }.exceptionOrNull()

        assertEquals(true, thrown?.message?.startsWith("no such member: [observeHabitdetail]"))
    }

    @Test
    fun `a member named unreachable is loud rather than quietly answering`() = runTest {
        val repository = FakeHabitRepository(unreachable = setOf("observeHabitDetail"))

        val thrown = runCatching { repository.observeHabitDetail(habitId(1)).first() }.exceptionOrNull()

        assertEquals("observeHabitDetail is not this screen's to call", thrown?.message)
    }

    @Test
    fun `a completion and its undo are both recorded, in order and by direction`() = runTest {
        val repository = FakeHabitRepository()
        repository.addCompletion(habitId(1), FIXED_DATE, note = null)
        repository.undoCompletion(habitId(1), FIXED_DATE)
        assertEquals(listOf(false, true), repository.completions.map { it.undo })
        assertEquals(listOf(Triple(habitId(1), FIXED_DATE, null)), repository.completed)
        assertEquals(listOf(habitId(1) to FIXED_DATE), repository.undone)
    }
}
