package com.gawi.core.domain.serialization

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.EventPayload
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitCreated
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.event.HabitUpdated
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EventSerializationTest {

    private val codec = EventCodec()
    private val habitId = HabitId("0190163d-8694-7abc-8def-0123456789ab")
    private val addId = com.gawi.core.domain.id.EventId("0190163d-8694-7abc-8def-0123456789ac")

    private fun roundTrip(payload: EventPayload): EventPayload {
        val encoded = codec.encode(payload)
        return codec.decode(encoded.type, encoded.schemaVersion, encoded.json)
    }

    @Test
    fun `habit created round-trips with all optionals set`() {
        val payload = HabitCreated(habitId, "Read", "book", "#aabbcc", Schedule.Weekly(3), tag = "mind")

        assertEquals(payload, roundTrip(payload))
    }

    @Test
    fun `habit created round-trips with optionals absent and daily schedule`() {
        val payload = HabitCreated(habitId, "Read", "book", "#aabbcc", Schedule.Daily, tag = null)

        assertEquals(payload, roundTrip(payload))
    }

    @Test
    fun `habit updated round-trips`() {
        val payload = HabitUpdated(habitId, "Read more", "book", "#001122", Schedule.Weekly(5), tag = null)

        assertEquals(payload, roundTrip(payload))
    }

    @Test
    fun `habit archived and unarchived round-trip`() {
        assertEquals(HabitArchived(habitId), roundTrip(HabitArchived(habitId)))
        assertEquals(HabitUnarchived(habitId), roundTrip(HabitUnarchived(habitId)))
    }

    @Test
    fun `completion added round-trips with and without a note`() {
        val with = CompletionAdded(habitId, LocalDate.parse("2026-08-17"), note = "early run")
        val without = CompletionAdded(habitId, LocalDate.parse("2026-08-17"), note = null)

        assertEquals(with, roundTrip(with))
        assertEquals(without, roundTrip(without))
    }

    @Test
    fun `completion tombstoned round-trips`() {
        val payload = CompletionTombstoned(addId)

        assertEquals(payload, roundTrip(payload))
    }

    @Test
    fun `note update round-trips and empty text stays empty`() {
        val cleared = CompletionNoteUpdated(addId, text = "")

        val decoded = roundTrip(cleared) as CompletionNoteUpdated

        assertEquals("", decoded.text)
    }

    @Test
    fun `type and version columns come from the codec not the json`() {
        val encoded = codec.encode(HabitArchived(habitId))

        assertEquals("HabitArchived", encoded.type)
        assertEquals(1, encoded.schemaVersion)
    }

    @Test
    fun `habit created wire format is pinned`() {
        val encoded = codec.encode(
            HabitCreated(habitId, "Read", "book", "#aabbcc", Schedule.Weekly(3), tag = "mind"),
        )

        assertEquals(
            """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab","name":"Read","icon":"book",""" +
                """"color":"#aabbcc","schedule":{"kind":"weekly","times_per_week":3},"tag":"mind"}""",
            encoded.json,
        )
    }

    @Test
    fun `daily schedule and absent optionals are pinned on the wire`() {
        val encoded = codec.encode(
            HabitCreated(habitId, "Read", "book", "#aabbcc", Schedule.Daily, tag = null),
        )

        assertEquals(
            """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab","name":"Read","icon":"book",""" +
                """"color":"#aabbcc","schedule":{"kind":"daily"}}""",
            encoded.json,
        )
    }

    @Test
    fun `completion added wire format is pinned with iso logical date`() {
        val encoded = codec.encode(
            CompletionAdded(habitId, LocalDate.parse("2026-08-17"), note = "early run"),
        )

        assertEquals(
            """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab",""" +
                """"logical_date":"2026-08-17","note":"early run"}""",
            encoded.json,
        )
    }

    @Test
    fun `tombstone and note update wire formats are pinned`() {
        assertEquals(
            """{"completion_event_id":"0190163d-8694-7abc-8def-0123456789ac"}""",
            codec.encode(CompletionTombstoned(addId)).json,
        )
        assertEquals(
            """{"completion_event_id":"0190163d-8694-7abc-8def-0123456789ac","text":""}""",
            codec.encode(CompletionNoteUpdated(addId, text = "")).json,
        )
    }

    @Test
    fun `pinned v1 fixtures decode for every type`() {
        val habitJson =
            """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab","name":"Read","icon":"book",""" +
                """"color":"#aabbcc","schedule":{"kind":"weekly","times_per_week":3},"tag":"mind"}"""

        assertEquals(
            HabitCreated(habitId, "Read", "book", "#aabbcc", Schedule.Weekly(3), tag = "mind"),
            codec.decode("HabitCreated", 1, habitJson),
        )
        assertEquals(
            HabitUpdated(habitId, "Read", "book", "#aabbcc", Schedule.Weekly(3), tag = "mind"),
            codec.decode("HabitUpdated", 1, habitJson),
        )
        assertEquals(
            HabitArchived(habitId),
            codec.decode("HabitArchived", 1, """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab"}"""),
        )
        assertEquals(
            HabitUnarchived(habitId),
            codec.decode("HabitUnarchived", 1, """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab"}"""),
        )
        assertEquals(
            CompletionAdded(habitId, LocalDate.parse("2026-08-17"), note = null),
            codec.decode(
                "CompletionAdded",
                1,
                """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab","logical_date":"2026-08-17"}""",
            ),
        )
        assertEquals(
            CompletionTombstoned(addId),
            codec.decode(
                "CompletionTombstoned",
                1,
                """{"completion_event_id":"0190163d-8694-7abc-8def-0123456789ac"}""",
            ),
        )
        assertEquals(
            CompletionNoteUpdated(addId, text = "hi"),
            codec.decode(
                "CompletionNoteUpdated",
                1,
                """{"completion_event_id":"0190163d-8694-7abc-8def-0123456789ac","text":"hi"}""",
            ),
        )
    }
}
