package com.gawi.core.domain.projection

import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.testsupport.completionAdded
import com.gawi.core.domain.testsupport.event
import com.gawi.core.domain.testsupport.eventId
import com.gawi.core.domain.testsupport.habitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NoteResolutionTest {

    private val habit = habitId(1)
    private val key = CompletionKey(habit, LocalDate.parse("2026-08-17"))

    private fun note(state: ProjectedState): String? = state.completions.getValue(key).displayedNote()

    @Test
    fun `an inline note on a single live add is displayed`() {
        val state = Projector.rebuild(
            listOf(event(1, 1000, completionAdded(habit, "2026-08-17", note = "morning run"))),
        )

        assertEquals("morning run", note(state))
    }

    @Test
    fun `a later note update beats the inline note`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17", note = "first")),
                event(2, 2000, CompletionNoteUpdated(eventId(1), "second")),
            ),
        )

        assertEquals("second", note(state))
    }

    @Test
    fun `note lww ties are broken by event id in both arrival orders`() {
        val add = event(1, 1000, completionAdded(habit, "2026-08-17"))
        val loser = event(2, 5000, CompletionNoteUpdated(eventId(1), "loser"))
        val winner = event(3, 5000, CompletionNoteUpdated(eventId(1), "winner"))

        val oneOrder = listOf(add, loser, winner).fold(ProjectedState.EMPTY, Projector::apply)
        val otherOrder = listOf(winner, add, loser).fold(ProjectedState.EMPTY, Projector::apply)

        assertEquals("winner", note(oneOrder))
        assertEquals(oneOrder, otherOrder)
    }

    @Test
    fun `a newer empty write clears the note`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17", note = "something")),
                event(2, 2000, CompletionNoteUpdated(eventId(1), "")),
            ),
        )

        assertNull("a clear wins LWW like any write", note(state))
    }

    @Test
    fun `an older empty write loses to a newer text`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17")),
                event(2, 2000, CompletionNoteUpdated(eventId(1), "")),
                event(3, 3000, CompletionNoteUpdated(eventId(1), "kept")),
            ),
        )

        assertEquals("kept", note(state))
    }

    @Test
    fun `the note dies with its parent and the next live write shows instead`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17", note = "older survivor")),
                event(2, 2000, completionAdded(habit, "2026-08-17")),
                event(3, 3000, CompletionNoteUpdated(eventId(2), "winner until undo")),
                event(4, 4000, CompletionTombstoned(eventId(2))),
            ),
        )

        assertEquals("older survivor", note(state))
    }

    @Test
    fun `re-adding after undo never resurrects the dead note`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17", note = "old note")),
                event(2, 2000, CompletionTombstoned(eventId(1))),
                event(3, 3000, completionAdded(habit, "2026-08-17")),
            ),
        )

        assertNull(note(state))
    }

    @Test
    fun `liveness follows the referenced add not the cell`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17")),
                event(2, 2000, completionAdded(habit, "2026-08-17")),
                event(3, 3000, CompletionNoteUpdated(eventId(1), "attached to the dead add")),
                event(4, 4000, CompletionTombstoned(eventId(1))),
            ),
        )

        assertNull("cell is still live via the other add, but the note's parent is dead", note(state))
    }

    @Test
    fun `a note update arriving before its add converges with the in-order result`() {
        val add = event(1, 1000, completionAdded(habit, "2026-08-17"))
        val update = event(2, 2000, CompletionNoteUpdated(eventId(1), "early bird"))

        val inOrder = listOf(add, update).fold(ProjectedState.EMPTY, Projector::apply)
        val reversed = listOf(update, add).fold(ProjectedState.EMPTY, Projector::apply)

        assertEquals(inOrder, reversed)
        assertEquals("early bird", note(reversed))
    }

    @Test
    fun `a dangling note update stays parked and invisible`() {
        val state = Projector.rebuild(
            listOf(
                event(1, 1000, completionAdded(habit, "2026-08-17")),
                event(2, 2000, CompletionNoteUpdated(eventId(99), "never lands")),
            ),
        )

        assertNull(note(state))
        assertEquals(1, state.pendingNoteWrites.size)
    }
}
