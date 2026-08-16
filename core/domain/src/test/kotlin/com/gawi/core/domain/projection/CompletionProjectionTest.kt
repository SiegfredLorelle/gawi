package com.gawi.core.domain.projection

import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.testsupport.completionAdded
import com.gawi.core.domain.testsupport.event
import com.gawi.core.domain.testsupport.eventId
import com.gawi.core.domain.testsupport.habitCreated
import com.gawi.core.domain.testsupport.habitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CompletionProjectionTest {

    private val habit = habitId(1)
    private val otherHabit = habitId(2)
    private val date = LocalDate.parse("2026-08-17")

    private fun completed(state: ProjectedState) = state.liveAddIds(habit, date).isNotEmpty()

    @Test
    fun `two live adds for the same cell collapse to one completion`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17")),
                event(2, 2000, completionAdded(habit, "2026-08-17")),
            ),
        )

        assertTrue(completed(state))
        assertEquals(setOf(date), state.completedDates(habit))
        assertEquals(2, state.liveAddIds(habit, date).size)
    }

    @Test
    fun `different dates and different habits stay distinct cells`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17")),
                event(2, 2000, completionAdded(habit, "2026-08-16")),
                event(3, 3000, completionAdded(otherHabit, "2026-08-17")),
            ),
        )

        assertEquals(setOf(LocalDate.parse("2026-08-16"), date), state.completedDates(habit))
        assertEquals(setOf(date), state.completedDates(otherHabit))
    }

    @Test
    fun `add then tombstone then fresh add ends completed`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17")),
                event(2, 2000, CompletionTombstoned(eventId(1))),
                event(3, 3000, completionAdded(habit, "2026-08-17")),
            ),
        )

        assertTrue(completed(state))
        assertEquals(setOf(eventId(3)), state.liveAddIds(habit, date))
    }

    @Test
    fun `a tombstone kills only the add it references`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17")),
                event(2, 2000, completionAdded(habit, "2026-08-17")),
                event(3, 3000, CompletionTombstoned(eventId(1))),
            ),
        )

        assertTrue("the unreferenced duplicate must stay live", completed(state))
        assertEquals(setOf(eventId(2)), state.liveAddIds(habit, date))
    }

    @Test
    fun `tombstoning every add empties the cell`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17")),
                event(2, 2000, completionAdded(habit, "2026-08-17")),
                event(3, 3000, CompletionTombstoned(eventId(1))),
                event(4, 4000, CompletionTombstoned(eventId(2))),
            ),
        )

        assertFalse(completed(state))
        assertEquals(emptySet<LocalDate>(), state.completedDates(habit))
    }

    @Test
    fun `duplicate tombstones for the same add change nothing`() {
        val events = listOf(
            event(1, 1000, completionAdded(habit, "2026-08-17")),
            event(2, 2000, CompletionTombstoned(eventId(1))),
        )

        val once = Projector.rebuild(events)
        val again = Projector.apply(once, event(3, 3000, CompletionTombstoned(eventId(1))))

        assertEquals(once.completions, again.completions)
    }

    @Test
    fun `a dangling tombstone is parked and harmless`() {
        val state = Projector.rebuild(listOf(event(1, 1000, CompletionTombstoned(eventId(99)))))

        assertEquals(emptySet<LocalDate>(), state.completedDates(habit))
        assertTrue(eventId(99) in state.pendingTombstones)
    }

    @Test
    fun `a tombstone arriving before its add kills it on arrival`() {
        val add = event(1, 1000, completionAdded(habit, "2026-08-17"))
        val tombstone = event(2, 2000, CompletionTombstoned(eventId(1)))

        val inOrder = listOf(add, tombstone).fold(ProjectedState.EMPTY, Projector::apply)
        val reversed = listOf(tombstone, add).fold(ProjectedState.EMPTY, Projector::apply)

        assertFalse(completed(reversed))
        assertEquals(inOrder, reversed)
    }

    @Test
    fun `a tombstone older than its add still kills it`() {
        val state = Projector.rebuild(
            listOf(
                event(2, 5000, completionAdded(habit, "2026-08-17")),
                event(1, 1000, CompletionTombstoned(eventId(2))),
            ),
        )

        assertFalse("tombstones are absolute, not LWW against their add", completed(state))
    }

    @Test
    fun `replay accepts months-old and future logical dates`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2020-01-01")),
                event(2, 2000, completionAdded(habit, "2030-12-31")),
            ),
        )

        assertEquals(
            setOf(LocalDate.parse("2020-01-01"), LocalDate.parse("2030-12-31")),
            state.completedDates(habit),
        )
    }

    @Test
    fun `a completion for an unknown habit is held and attaches when the habit arrives`() {
        val add = event(1, 1000, completionAdded(habit, "2026-08-17"))

        val withoutHabit = Projector.rebuild(listOf(add))
        val withHabit = Projector.apply(withoutHabit, event(2, 2000, habitCreated(habit)))

        assertTrue(completed(withoutHabit))
        assertTrue(completed(withHabit))
        assertEquals("read", withHabit.habit(habit)!!.name)
    }

    @Test
    fun `completions for an archived habit are accepted and retained`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, habitCreated(habit)),
                event(2, 2000, HabitArchived(habit)),
                event(3, 3000, completionAdded(habit, "2026-08-17")),
            ),
        )

        assertTrue(completed(state))
        assertTrue(state.habit(habit)!!.archived)
    }

    @Test
    fun `the projection uses the stored logical date not the event timestamp`() {
        val occurredOnThe18th = LocalDate.parse("2026-08-18")
            .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

        val state = Projector.rebuild(
            listOf(event(1, occurredOnThe18th, completionAdded(habit, "2026-08-17"))),
        )

        assertEquals(setOf(date), state.completedDates(habit))
    }
}
