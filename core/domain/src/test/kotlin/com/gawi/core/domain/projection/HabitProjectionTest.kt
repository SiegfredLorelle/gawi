package com.gawi.core.domain.projection

import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.testsupport.event
import com.gawi.core.domain.testsupport.eventAt
import com.gawi.core.domain.testsupport.habitCreated
import com.gawi.core.domain.testsupport.habitId
import com.gawi.core.domain.testsupport.habitUpdated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitProjectionTest {

    private val habit = habitId(1)

    @Test
    fun `habit created materializes the habit with all fields`() {
        val state = Projector.rebuild(
            listOf(event(1, 1000, HabitCreated(habit, "Read", "book", "#aabbcc", Schedule.Weekly(3), "mind"))),
        )

        val habitState = state.habit(habit)!!
        assertEquals("Read", habitState.name)
        assertEquals("book", habitState.icon)
        assertEquals("#aabbcc", habitState.color)
        assertEquals(Schedule.Weekly(3), habitState.schedule)
        assertEquals("mind", habitState.tag)
        assertFalse(habitState.archived)
    }

    /**
     * The creation date comes from the creating event's own envelope, in the
     * offset it was written at — not in UTC. 01:30Z is still the 23rd for a
     * writer three hours behind, and a habit that reads as created a day early
     * would hand every rate window an extra day of opportunity the user was
     * never offered.
     */
    @Test
    fun `the creation date is read in the offset the event was written at`() {
        val utc = Projector.rebuild(listOf(eventAt(1, "2026-08-24T01:30:00Z", 0, habitCreated(habit))))
        val behind = Projector.rebuild(listOf(eventAt(1, "2026-08-24T01:30:00Z", -180, habitCreated(habit))))

        assertEquals(LocalDate.parse("2026-08-24"), utc.habit(habit)!!.createdOn)
        assertEquals(LocalDate.parse("2026-08-23"), behind.habit(habit)!!.createdOn)
    }

    /**
     * An update alone materializes a habit with no known start.
     *
     * Metadata and creation are separate registers, so this is reachable — a
     * sync that delivers an edit before the create. Null rather than a guess,
     * because every caller of `createdOn` uses it to *widen* what it will draw,
     * and a fabricated date would silently narrow it.
     */
    @Test
    fun `an update with no create leaves the creation date unknown`() {
        val state = Projector.rebuild(listOf(event(2, 2000, habitUpdated(habit, name = "read"))))

        assertNotNull(state.habit(habit))
        assertNull(state.habit(habit)!!.createdOn)
    }

    /**
     * Two creates resolve to the earlier one, whichever way round they arrive.
     *
     * The inverse of the metadata register's rule, and deliberately: a habit was
     * created once, so a second `HabitCreated` is a duplicate of a fact rather
     * than a newer version of it. Both orders are asserted because that is what
     * "commutative" means here.
     */
    @Test
    fun `two creates resolve to the earlier one in both arrival orders`() {
        val early = eventAt(1, "2026-08-20T10:00:00Z", 0, habitCreated(habit, name = "read"))
        val late = eventAt(2, "2026-08-24T10:00:00Z", 0, habitCreated(habit, name = "read"))
        val expected = LocalDate.parse("2026-08-20")

        assertEquals(expected, Projector.rebuild(listOf(early, late)).habit(habit)!!.createdOn)
        assertEquals(expected, Projector.rebuild(listOf(late, early)).habit(habit)!!.createdOn)
    }

    /** And a duplicate of the same create changes nothing. */
    @Test
    fun `a duplicated create is idempotent`() {
        val create = eventAt(1, "2026-08-20T10:00:00Z", 0, habitCreated(habit))

        assertEquals(
            Projector.rebuild(listOf(create)),
            Projector.rebuild(listOf(create, create)),
        )
    }

    @Test
    fun `a later update replaces the whole record`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, HabitCreated(habit, "Read", "book", "#aabbcc", Schedule.Daily, "mind")),
                event(2, 2000, HabitUpdated(habit, "Run", "shoe", "#001122", Schedule.Weekly(2), null)),
            ),
        )

        val habitState = state.habit(habit)!!
        assertEquals("Run", habitState.name)
        assertEquals("shoe", habitState.icon)
        assertEquals("#001122", habitState.color)
        assertEquals(Schedule.Weekly(2), habitState.schedule)
        assertNull("loser's tag must not survive whole-record LWW", habitState.tag)
    }

    @Test
    fun `an earlier update than the current record is ignored`() {
        val state = Projector.rebuild(
            listOf(
                event(2, 2000, habitCreated(habit, name = "current")),
                event(1, 1000, habitUpdated(habit, name = "stale")),
            ),
        )

        assertEquals("current", state.habit(habit)!!.name)
    }

    @Test
    fun `equal timestamps are broken by event id in both arrival orders`() {
        val create = event(1, 1000, habitCreated(habit, name = "loser"))
        val update = event(2, 1000, habitUpdated(habit, name = "winner"))

        val inOrder = listOf(create, update).fold(ProjectedState.EMPTY, Projector::apply)
        val reversed = listOf(update, create).fold(ProjectedState.EMPTY, Projector::apply)

        assertEquals("winner", inOrder.habit(habit)!!.name)
        assertEquals(inOrder, reversed)
    }

    @Test
    fun `an update arriving before its create converges to the same state`() {
        val create = event(1, 1000, habitCreated(habit, name = "created"))
        val update = event(2, 2000, habitUpdated(habit, name = "updated"))

        val inOrder = listOf(create, update).fold(ProjectedState.EMPTY, Projector::apply)
        val reversed = listOf(update, create).fold(ProjectedState.EMPTY, Projector::apply)

        assertEquals(inOrder, reversed)
        assertEquals("updated", reversed.habit(habit)!!.name)
    }

    @Test
    fun `an update without any create still materializes a habit`() {
        val state = Projector.rebuild(listOf(event(1, 1000, habitUpdated(habit, name = "orphan"))))

        assertNotNull(state.habit(habit))
        assertEquals("orphan", state.habit(habit)!!.name)
    }

    @Test
    fun `archive then later unarchive leaves the habit active`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, habitCreated(habit)),
                event(2, 2000, HabitArchived(habit)),
                event(3, 3000, HabitUnarchived(habit)),
            ),
        )

        assertFalse(state.habit(habit)!!.archived)
    }

    @Test
    fun `archive with the later timestamp wins regardless of arrival order`() {
        val archive = event(3, 3000, HabitArchived(habit))
        val unarchive = event(2, 2000, HabitUnarchived(habit))
        val create = event(1, 1000, habitCreated(habit))

        val oneOrder = listOf(create, archive, unarchive).fold(ProjectedState.EMPTY, Projector::apply)
        val otherOrder = listOf(unarchive, archive, create).fold(ProjectedState.EMPTY, Projector::apply)

        assertTrue(oneOrder.habit(habit)!!.archived)
        assertEquals(oneOrder, otherOrder)
    }

    @Test
    fun `archive events for an unknown habit are retained and converge`() {
        val archive = event(1, 1000, HabitArchived(habit))

        val state = Projector.rebuild(listOf(archive))

        assertNull("no metadata yet, so no visible habit", state.habit(habit))
        assertTrue(state.habitRecords.getValue(habit).archived)
    }

    @Test
    fun `applying the same event twice is a no-op`() {
        val create = event(1, 1000, habitCreated(habit))

        val once = Projector.apply(ProjectedState.EMPTY, create)
        val twice = Projector.apply(once, create)

        assertEquals(once, twice)
    }
}
