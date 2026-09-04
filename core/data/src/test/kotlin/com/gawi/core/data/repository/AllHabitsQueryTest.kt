package com.gawi.core.data.repository

import app.cash.turbine.test
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.testing.metadata
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the habit management list receives.
 *
 * The one read that does not filter archived habits, because unarchiving has
 * to be reachable from somewhere.
 */
@RunWith(RobolectricTestRunner::class)
class AllHabitsQueryTest {

    private lateinit var store: TestStore

    @Before
    fun setUp() {
        store = TestStore.create()
    }

    @After
    fun tearDown() = store.close()

    private suspend fun createHabit(name: String = "read", schedule: Schedule = Schedule.Daily): HabitId =
        (store.repository.createHabit(metadata(name, schedule)) as CommandResult.Accepted).payload

    @Test
    fun `an archived habit is listed here and not on today`() = runTest {
        val kept = createHabit("read")
        val putAway = createHabit("exercise")
        store.repository.archiveHabit(putAway)

        store.repository.observeToday().test {
            assertEquals(listOf(kept), awaitItem().habits.map { it.habit.id })
            cancelAndIgnoreRemainingEvents()
        }
        store.repository.observeAllHabits().test {
            val habits = awaitItem()
            assertEquals(setOf(kept, putAway), habits.map { it.id }.toSet())
            assertTrue(habits.single { it.id == putAway }.archived)
            assertFalse(habits.single { it.id == kept }.archived)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a row carries the habit as it was configured`() = runTest {
        val habit = createHabit("read", Schedule.Weekly(3))

        store.repository.observeAllHabits().test {
            val row = awaitItem().single()
            assertEquals(habit, row.id)
            assertEquals("read", row.name)
            assertEquals(Schedule.Weekly(3), row.schedule)
            assertEquals("book", row.icon)
            assertEquals("#aabbcc", row.color)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `habits are ordered by name regardless of case or creation order`() = runTest {
        // Created deliberately out of order, and with a capital, so ordering by
        // habit_id or by a case-sensitive name would both give a different answer.
        createHabit("swim")
        createHabit("Read")
        createHabit("exercise")

        store.repository.observeAllHabits().test {
            assertEquals(listOf("exercise", "Read", "swim"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the list re-emits when a habit is archived and again when it returns`() = runTest {
        val habit = createHabit()

        store.repository.observeAllHabits().test {
            assertFalse(awaitItem().single().archived)

            store.repository.archiveHabit(habit)
            assertTrue(awaitItem().single().archived)

            store.repository.unarchiveHabit(habit)
            assertFalse(awaitItem().single().archived)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a rename is reflected without re-subscribing`() = runTest {
        val habit = createHabit("read")

        store.repository.observeAllHabits().test {
            assertEquals("read", awaitItem().single().name)

            store.repository.updateHabit(habit, metadata(name = "read more"))

            assertEquals("read more", awaitItem().single().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no habits reads as an empty list rather than nothing at all`() = runTest {
        store.repository.observeAllHabits().test {
            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
