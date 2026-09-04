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
