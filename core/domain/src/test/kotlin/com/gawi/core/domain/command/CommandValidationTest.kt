package com.gawi.core.domain.command

import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitMetadata
import com.gawi.core.domain.projection.ProjectedState
import com.gawi.core.domain.projection.Projector
import com.gawi.core.domain.testsupport.completionAdded
import com.gawi.core.domain.testsupport.event
import com.gawi.core.domain.testsupport.eventId
import com.gawi.core.domain.testsupport.habitCreated
import com.gawi.core.domain.testsupport.habitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CommandValidationTest {

    private val habit = habitId(1)
    private val today = LocalDate.parse("2026-08-17")
    private val metadata = HabitMetadata("Read", "book", "#aabbcc", Schedule.Daily, tag = null)

    private val stateWithHabit = Projector.rebuild(listOf(event(1, 1000, habitCreated(habit))))

    private fun rejectedWith(result: CommandResult<*>, error: CommandError) {
        assertEquals(CommandResult.Rejected(error), result)
    }

    @Test
    fun `create habit rejects a blank name`() {
        rejectedWith(
            Commands.createHabit(habit, metadata.copy(name = "   ")),
            CommandError.BlankName,
        )
    }

    @Test
    fun `create habit accepts and returns the payload`() {
        val result = Commands.createHabit(habit, metadata)

        val accepted = result as CommandResult.Accepted
        assertEquals("Read", accepted.payload.name)
        assertEquals(habit, accepted.payload.habitId)
    }

    @Test
    fun `update and archive and unarchive reject unknown habits`() {
        rejectedWith(
            Commands.updateHabit(ProjectedState.EMPTY, habit, metadata),
            CommandError.HabitNotFound,
        )
        rejectedWith(Commands.archiveHabit(ProjectedState.EMPTY, habit), CommandError.HabitNotFound)
        rejectedWith(Commands.unarchiveHabit(ProjectedState.EMPTY, habit), CommandError.HabitNotFound)
    }

    @Test
    fun `add completion rejects an unknown habit`() {
        rejectedWith(
            Commands.addCompletion(ProjectedState.EMPTY, habit, today, today),
            CommandError.HabitNotFound,
        )
    }

    @Test
    fun `add completion rejects an archived habit`() {
        val archived = Projector.apply(stateWithHabit, event(2, 2000, HabitArchived(habit)))

        rejectedWith(
            Commands.addCompletion(archived, habit, today, today),
            CommandError.HabitIsArchived,
        )
    }

    @Test
    fun `the retro window allows today back through three days ago`() {
        for (daysBack in 0L..3L) {
            val result = Commands.addCompletion(stateWithHabit, habit, today.minusDays(daysBack), today)
            assertTrue("$daysBack days back must be allowed", result is CommandResult.Accepted)
        }
    }

    @Test
    fun `the retro window rejects four days back`() {
        rejectedWith(
            Commands.addCompletion(stateWithHabit, habit, today.minusDays(4), today),
            CommandError.RetroWindowExceeded,
        )
    }

    @Test
    fun `future logical dates are rejected`() {
        rejectedWith(
            Commands.addCompletion(stateWithHabit, habit, today.plusDays(1), today),
            CommandError.FutureLogicalDate,
        )
    }

    @Test
    fun `completing an already-completed cell is accepted`() {
        val completed = Projector.apply(
            stateWithHabit,
            event(2, 2000, completionAdded(habit, "2026-08-17")),
        )

        val result = Commands.addCompletion(completed, habit, today, today)

        assertTrue(result is CommandResult.Accepted)
    }

    @Test
    fun `replay accepts exactly what the command rejects`() {
        val monthsOld = completionAdded(habit, "2026-01-01")

        val commandResult = Commands.addCompletion(stateWithHabit, habit, monthsOld.logicalDate, today)
        val replayed = Projector.apply(stateWithHabit, event(2, 2000, monthsOld))

        rejectedWith(commandResult, CommandError.RetroWindowExceeded)
        assertTrue(replayed.completedDates(habit).contains(monthsOld.logicalDate))
    }

    @Test
    fun `undo tombstones every live add in the cell`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, habitCreated(habit)),
                event(2, 2000, completionAdded(habit, "2026-08-17")),
                event(3, 3000, completionAdded(habit, "2026-08-17")),
                event(4, 4000, completionAdded(habit, "2026-08-17")),
            ),
        )

        val result = Commands.undoCompletion(state, habit, today) as CommandResult.Accepted

        assertEquals(
            listOf(eventId(2), eventId(3), eventId(4)).map(::CompletionTombstoned),
            result.payload,
        )
    }

    @Test
    fun `undo skips adds that are already tombstoned`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, habitCreated(habit)),
                event(2, 2000, completionAdded(habit, "2026-08-17")),
                event(3, 3000, completionAdded(habit, "2026-08-17")),
                event(4, 4000, CompletionTombstoned(eventId(2))),
            ),
        )

        val result = Commands.undoCompletion(state, habit, today) as CommandResult.Accepted

        assertEquals(listOf(CompletionTombstoned(eventId(3))), result.payload)
    }

    @Test
    fun `undo and note updates on an archived habit are rejected like adds`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, habitCreated(habit)),
                event(2, 2000, completionAdded(habit, "2026-08-17")),
                event(3, 3000, HabitArchived(habit)),
            ),
        )

        rejectedWith(Commands.undoCompletion(state, habit, today), CommandError.HabitIsArchived)
        rejectedWith(
            Commands.updateCompletionNote(state, eventId(2), "frozen"),
            CommandError.HabitIsArchived,
        )
    }

    @Test
    fun `undo on an empty cell is rejected`() {
        rejectedWith(Commands.undoCompletion(stateWithHabit, habit, today), CommandError.CompletionNotFound)
    }

    @Test
    fun `a note update on a live completion is accepted even when empty`() {
        val state = Projector.apply(stateWithHabit, event(2, 2000, completionAdded(habit, "2026-08-17")))

        val result = Commands.updateCompletionNote(state, eventId(2), text = "")

        assertTrue(result is CommandResult.Accepted)
    }

    @Test
    fun `a note update on a tombstoned completion is rejected`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, habitCreated(habit)),
                event(2, 2000, completionAdded(habit, "2026-08-17")),
                event(3, 3000, CompletionTombstoned(eventId(2))),
            ),
        )

        rejectedWith(
            Commands.updateCompletionNote(state, eventId(2), "too late"),
            CommandError.CompletionNotFound,
        )
    }

    @Test
    fun `a note update on an unknown completion is rejected`() {
        rejectedWith(
            Commands.updateCompletionNote(stateWithHabit, eventId(99), "nowhere"),
            CommandError.CompletionNotFound,
        )
    }
}
