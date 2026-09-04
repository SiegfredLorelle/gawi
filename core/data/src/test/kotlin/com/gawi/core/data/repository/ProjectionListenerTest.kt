package com.gawi.core.data.repository

import com.gawi.core.data.testsupport.RecordingProjectionListener
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.testing.metadata
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * That a committed write is announced.
 *
 * The seam exists because Glance cannot observe Room (architecture §4), and the
 * failure it guards against is the quietest kind there is: a widget that never
 * hears about a commit is indistinguishable from a widget nobody has placed. So
 * this is asserted on the JVM rather than left to a device observation — a
 * deleted `onProjectionChanged()` call reddens here, where on a launcher it
 * would just look like a widget that had not been added.
 *
 * It asserts *counts*, not merely "at least once": a listener called twice per
 * command would redraw the home screen twice per tap.
 */
@RunWith(RobolectricTestRunner::class)
class ProjectionListenerTest {

    private lateinit var store: TestStore

    @Before
    fun setUp() {
        store = TestStore.create()
    }

    @After
    fun tearDown() = store.close()

    private suspend fun createHabit(name: String = "read"): HabitId =
        (store.repository.createHabit(metadata(name)) as CommandResult.Accepted).payload

    @Test
    fun `nothing is announced before anything is written`() = runTest {
        assertEquals(0, store.listener.calls)
    }

    @Test
    fun `each command announces exactly once`() = runTest {
        createHabit()
        assertEquals(1, store.listener.calls)
    }

    @Test
    fun `completing a habit announces`() = runTest {
        val habit = createHabit()
        val before = store.listener.calls

        store.repository.addCompletion(habit, store.today())

        assertEquals(before + 1, store.listener.calls)
    }

    @Test
    fun `undoing a completion announces too`() = runTest {
        val habit = createHabit()
        store.repository.addCompletion(habit, store.today())
        val before = store.listener.calls

        store.repository.undoCompletion(habit, store.today())

        assertEquals(before + 1, store.listener.calls)
    }

    /**
     * A rejected command writes no event, so there is nothing for a widget to
     * redraw. Blank names are the reachable rejection here — the fixed habit
     * palette makes every stored colour valid by construction.
     */
    @Test
    fun `a rejected command announces nothing`() = runTest {
        val result = store.repository.createHabit(metadata(""))

        assertTrue(result is CommandResult.Rejected)
        assertEquals(0, store.listener.calls)
    }

    /**
     * The invariant the call-site guard exists for.
     *
     * The listener runs after the commit and inside `NonCancellable`, so a throw
     * would come back out of a command that had already written its event — the
     * measured incident behind this guard reported a saved habit as a failed save
     * (docs/ux/widget.md §5). `NoClassDefFoundError` is the actual escapee, and
     * an `Error` rather than an `Exception` on purpose: that is what walked past
     * the first, narrower guard.
     */
    @Test
    fun `a listener that throws cannot fail the command that committed`() = runTest {
        val exploding = TestStore.create(listener = RecordingProjectionListener(NoClassDefFoundError("glance")))
        try {
            val result = exploding.repository.createHabit(metadata("read"))

            assertTrue("the command must still succeed", result is CommandResult.Accepted)
            assertEquals("and the listener must really have been called", 1, exploding.listener.calls)
            assertEquals("and the event must really be in the log", 1, exploding.log().size)
        } finally {
            exploding.close()
        }
    }

    /**
     * An import is the other way the read model moves, and it announces even
     * when it inserted nothing. The guard in `mergeLocked` is about whether the
     * *fold* can be reused; a widget that is already right costs a redraw, one
     * left stale costs the user a wrong home screen.
     */
    @Test
    fun `an import announces`() = runTest {
        createHabit("read")
        val file = store.exportText()
        val fresh = TestStore.create()
        try {
            fresh.import(file)
            assertEquals(1, fresh.listener.calls)

            fresh.import(file)
            assertEquals(2, fresh.listener.calls)
        } finally {
            fresh.close()
        }
    }
}
