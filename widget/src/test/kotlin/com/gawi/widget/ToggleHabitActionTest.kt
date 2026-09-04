package com.gawi.widget

import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import com.gawi.core.testing.FakeHabitRepository
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * What one tap on a widget row decides.
 *
 * The load-bearing assertions here are the two about *where* the write lands:
 * the habit and the logical date both come from a read taken at tap time, not
 * from anything the render carried. See `toggleHabit`'s KDoc for why a widget
 * cannot be trusted to have drawn the current day.
 */
class ToggleHabitActionTest {

    @Test
    fun `an incomplete habit is completed`() = runTest {
        val habits = FakeHabitRepository(todaySnapshot(habits = listOf(todayHabit(id = habitId(1), completedToday = false))))

        toggleHabit(habits, habitId(1).value)

        assertEquals(1, habits.completions.size)
        assertEquals(false, habits.completions.single().undo)
        assertEquals(habitId(1), habits.completions.single().habitId)
    }

    @Test
    fun `a completed habit is undone`() = runTest {
        val habits = FakeHabitRepository(todaySnapshot(habits = listOf(todayHabit(id = habitId(1), completedToday = true))))

        toggleHabit(habits, habitId(1).value)

        assertEquals(true, habits.completions.single().undo)
    }

    /**
     * The date written is the one the fresh read reported, and the fixture date
     * is deliberately not today's — so replacing `snapshot.today` with
     * `LocalDate.now()` reddens this rather than passing by coincidence.
     */
    @Test
    fun `the write lands on the logical date the tap-time read reported`() = runTest {
        val habits = FakeHabitRepository(todaySnapshot(habits = listOf(todayHabit(id = habitId(1))), today = FIXED_DATE))

        toggleHabit(habits, habitId(1).value)

        assertEquals(FIXED_DATE, habits.completions.single().logicalDate)
    }

    @Test
    fun `a habit that is no longer in the snapshot is not written to`() = runTest {
        val habits = FakeHabitRepository(todaySnapshot(habits = listOf(todayHabit(id = habitId(1)))))

        toggleHabit(habits, habitId(2).value)

        assertTrue(habits.completions.isEmpty())
    }

    /**
     * Pins the reason the id travels as a `String`: `HabitId`'s constructor
     * *requires* a canonical UUIDv7 and throws otherwise, so constructing one
     * from an action parameter would put that throw inside a broadcast receiver.
     * Matching the string instead makes a malformed parameter the same harmless
     * no-op as an unknown one.
     */
    @Test
    fun `a malformed habit id is a no-op rather than a crash`() = runTest {
        val habits = FakeHabitRepository(todaySnapshot(habits = listOf(todayHabit(id = habitId(1)))))

        toggleHabit(habits, "not-a-uuid")

        assertTrue(habits.completions.isEmpty())
    }

    @Test
    fun `a missing parameter is a no-op rather than a crash`() = runTest {
        val habits = FakeHabitRepository(todaySnapshot(habits = listOf(todayHabit(id = habitId(1)))))

        toggleHabit(habits, null)

        assertTrue(habits.completions.isEmpty())
    }

    /**
     * A widget has no snackbar, so the only thing a failure can do is leave the
     * log alone and let the re-render tell the truth. What must not happen is
     * the throw escaping: it would reach the default handler from a broadcast,
     * which is the process-death shape PR review found in three ViewModels.
     */
    @Test
    fun `a failing read is absorbed`() = runTest {
        val habits = FakeHabitRepository(failWith = IOException("disk"))

        toggleHabit(habits, habitId(1).value)

        assertTrue(habits.completions.isEmpty())
    }
}
