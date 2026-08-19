package com.gawi.core.data.db.mapper

import com.gawi.core.data.db.entity.EventEntity
import com.gawi.core.data.testsupport.completionAdded
import com.gawi.core.data.testsupport.event
import com.gawi.core.data.testsupport.eventId
import com.gawi.core.data.testsupport.habitCreated
import com.gawi.core.data.testsupport.habitId
import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.event.EventPayload
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.serialization.EventCodecException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The envelope↔row mapping, with no database in the way. Room's own behaviour
 * is `GawiDatabaseTest`'s business; this file is about the translation the
 * entity deliberately does not do for itself.
 */
class EventMapperTest {

    private val codec = EventCodec()
    private val habit = habitId(9)

    @Test
    fun `every payload type round-trips`() {
        val payloads = listOf(
            habitCreated(habit),
            HabitUpdated(habit, "read", "book", "#aabbcc", Schedule.Weekly(3), tag = "health"),
            HabitArchived(habit),
            HabitUnarchived(habit),
            completionAdded(habit, "2026-08-19", note = "done"),
            CompletionTombstoned(eventId(5)),
            CompletionNoteUpdated(eventId(5), "later thought"),
        )

        payloads.forEachIndexed { index, payload -> assertRoundTrips(payload, index) }
    }

    @Test
    fun `an instant survives as epoch millis`() {
        val original = event(1, atMillis = 1_700_000_000_123, payload = habitCreated(habit))

        val row = original.toEntity(codec)

        assertEquals(1_700_000_000_123, row.occurredAt)
        assertEquals(original.occurredAt, row.toDomain(codec).occurredAt)
    }

    @Test
    fun `a schedule survives the wire round trip`() {
        val weekly = HabitUpdated(habit, "read", "book", "#aabbcc", Schedule.Weekly(3), tag = null)

        val decoded = event(1, atMillis = 1, payload = weekly).toEntity(codec).toDomain(codec)

        assertEquals(Schedule.Weekly(3), (decoded.payload as HabitUpdated).schedule)
    }

    @Test
    fun `an unknown type fails loudly rather than dropping the row`() {
        val row = event(1, atMillis = 1, payload = habitCreated(habit)).toEntity(codec).copy(type = "HabitTeleported")

        assertThrows(EventCodecException::class.java) { row.toDomain(codec) }
    }

    @Test
    fun `an unknown schema version fails loudly`() {
        val row = event(1, atMillis = 1, payload = habitCreated(habit)).toEntity(codec).copy(schemaVersion = 99)

        assertThrows(EventCodecException::class.java) { row.toDomain(codec) }
    }

    @Test
    fun `a corrupt payload body fails loudly`() {
        val row = event(1, atMillis = 1, payload = habitCreated(habit)).toEntity(codec).copy(payload = "{\"nope\":1}")

        assertThrows(EventCodecException::class.java) { row.toDomain(codec) }
    }

    @Test
    fun `a non-canonical stored id is rejected`() {
        val row = event(1, atMillis = 1, payload = habitCreated(habit)).toEntity(codec).copy(id = "not-a-uuid")

        assertThrows(IllegalArgumentException::class.java) { row.toDomain(codec) }
    }

    private fun assertRoundTrips(payload: EventPayload, index: Int) {
        val original = event(index + 1, atMillis = 1_000L + index, payload = payload)

        val restored: Event = original.toEntity(codec).toDomain(codec)

        assertEquals(original, restored)
    }

    @Test
    fun `the type column is the payload class name`() {
        val row: EventEntity = event(1, atMillis = 1, payload = HabitArchived(habit)).toEntity(codec)

        assertEquals("HabitArchived", row.type)
        assertEquals(1, row.schemaVersion)
    }
}
