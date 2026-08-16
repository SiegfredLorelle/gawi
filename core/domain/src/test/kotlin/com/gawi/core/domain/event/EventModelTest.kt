package com.gawi.core.domain.event

import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EventModelTest {

    private val habitId = HabitId("0190163d-8694-7abc-8def-0123456789ab")

    @Test
    fun `payloads compare by value`() {
        val a = CompletionAdded(habitId, LocalDate.parse("2026-08-17"), note = null)
        val b = CompletionAdded(habitId, LocalDate.parse("2026-08-17"), note = null)

        assertEquals(a, b)
    }

    @Test
    fun `an empty note update is distinct from a non-empty one`() {
        val target = EventId("0190163d-8694-7abc-8def-0123456789ab")

        assertNotEquals(
            CompletionNoteUpdated(target, text = ""),
            CompletionNoteUpdated(target, text = "felt great"),
        )
    }

    @Test
    fun `envelope carries id and instant and offset unchanged`() {
        val event = Event(
            id = EventId("0190163d-8694-7abc-8def-0123456789ab"),
            occurredAt = Instant.ofEpochMilli(1_755_400_000_000),
            tzOffsetMin = 480,
            payload = HabitArchived(habitId),
        )

        assertEquals(1_755_400_000_000, event.occurredAt.toEpochMilli())
        assertEquals(480, event.tzOffsetMin)
        assertEquals(HabitArchived(habitId), event.payload)
    }
}
