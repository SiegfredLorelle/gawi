package com.gawi.core.data.repository

import app.cash.turbine.test
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.data.testsupport.metadata
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate

/** What the Today view actually receives. */
@RunWith(RobolectricTestRunner::class)
class TodayQueryTest {

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
    fun `a row carries the habit, its completion, its week count and its streak`() = runTest {
        val habit = createHabit("read", Schedule.Weekly(3))
        store.repository.addCompletion(habit, store.today(), note = "chapter one")

        store.repository.observeToday().test {
            val row = awaitItem().single()
            assertEquals(habit, row.habit.id)
            assertEquals("read", row.habit.name)
            assertEquals(Schedule.Weekly(3), row.habit.schedule)
            assertTrue(row.completedToday)
            assertEquals("chapter one", row.note)
            assertEquals(1, row.weekCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an untouched habit reads as not completed with no note`() = runTest {
        createHabit()

        store.repository.observeToday().test {
            val row = awaitItem().single()
            assertFalse(row.completedToday)
            assertNull(row.note)
            assertEquals(0, row.weekCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archived habits leave the list`() = runTest {
        val habit = createHabit()

        store.repository.observeToday().test {
            assertEquals(1, awaitItem().size)

            store.repository.archiveHabit(habit)

            assertEquals(emptyList<Any>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the week count follows the configured week start`() = runTest {
        // Today is Wednesday 19 August, and the completion is the Sunday
        // before it. With a Monday week start this week runs Mon 17 - Sun 23,
        // so the Sunday is last week's. With a Thursday week start it runs
        // Thu 13 - Wed 19, and the same completion is now inside it.
        store.clock.moveTo(LocalDate.parse("2026-08-19"))
        val habit = createHabit("read", Schedule.Weekly(3))
        store.repository.addCompletion(habit, LocalDate.parse("2026-08-16"))

        store.repository.observeToday().test {
            assertEquals(0, awaitItem().single().weekCount)
            cancelAndIgnoreRemainingEvents()
        }

        store.settings.settings = store.settings.settings.copy(weekStart = DayOfWeek.THURSDAY)

        store.repository.observeToday().test {
            assertEquals(1, awaitItem().single().weekCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a repeated completion produces no further emission`() = runTest {
        val habit = createHabit()

        store.repository.observeToday().test {
            assertFalse(awaitItem().single().completedToday)

            store.repository.addCompletion(habit, store.today())
            assertTrue(awaitItem().single().completedToday)

            // Nothing changed, so nothing should reach the UI — this is the
            // pairing of skip-if-equal in the writer with distinctUntilChanged
            // on the way out.
            store.repository.addCompletion(habit, store.today())
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `touching one habit does not re-emit another habit's own flow`() = runTest {
        val a = createHabit("a")
        val b = createHabit("b")

        store.repository.observeHabit(b).test {
            assertEquals(b, awaitItem()?.habit?.id)

            // Room invalidates per table, so habit b's query does wake up here.
            // distinctUntilChanged is what keeps that off the UI.
            store.repository.addCompletion(a, store.today())
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observing an unknown habit emits null rather than failing`() = runTest {
        store.repository.observeHabit(com.gawi.core.data.testsupport.habitId(404)).test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completed dates come back with the notes showing on them`() = runTest {
        val habit = createHabit()
        val today = store.today()
        store.repository.addCompletion(habit, today, note = "a")
        store.repository.addCompletion(habit, today.minusDays(1))

        store.repository.observeCompletedDates(habit, today.minusDays(3), today).test {
            assertEquals(mapOf(today to "a", today.minusDays(1) to null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
