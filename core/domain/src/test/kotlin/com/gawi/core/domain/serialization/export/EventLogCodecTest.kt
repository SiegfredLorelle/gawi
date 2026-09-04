package com.gawi.core.domain.serialization.export

import com.gawi.core.domain.serialization.EncodedPayload
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.testing.completionAdded
import com.gawi.core.domain.testing.eventId
import com.gawi.core.domain.testing.habitCreated
import com.gawi.core.domain.testing.habitId
import com.gawi.core.domain.testing.uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The export file format, both directions.
 *
 * Plain JVM: the codec is `:core:domain`, so none of this needs Robolectric or
 * a database. What it is aimed at is the two things a round trip can lose
 * without anyone noticing — a payload key this build does not know, and the
 * distinction between a file from the future and a file that is broken.
 */
class EventLogCodecTest {

    private val payloads = EventCodec()
    private val codec = EventLogCodec(payloads)

    @Test
    fun `an envelope carries the marker, the version and the count`() {
        val root = exportOf(encoded(1, habitCreated(habitId(9))))

        assertEquals("gawi.event-log", root["format"]?.jsonPrimitive?.content)
        assertEquals(1, root["format_version"]?.jsonPrimitive?.content?.toInt())
        assertEquals(1, root["event_count"]?.jsonPrimitive?.content?.toInt())
        assertEquals("0.1.0", root["app_version"]?.jsonPrimitive?.content)
        assertEquals(EXPORTED_AT.toString(), root["exported_at"]?.jsonPrimitive?.content)
    }

    @Test
    fun `every payload type survives a round trip`() {
        val log = ALL_PAYLOAD_TYPES.mapIndexed { i, payload -> encoded(i + 1, payload) }

        assertEquals(log, readBack(log))
    }

    /**
     * The payload is nested JSON, not JSON hidden inside a string.
     *
     * Mutation-checked: storing `payload` as the raw text makes this fail, and
     * nothing else here would notice. It is what the PRD's "open formats"
     * promise costs — `jq '.events[].payload.habit_id'` has to work.
     */
    @Test
    fun `the payload is a nested object rather than an escaped string`() {
        val root = exportOf(encoded(1, habitCreated(habitId(9), name = "read")))
        val payload = root["events"]!!.jsonArray.single().jsonObject["payload"]

        assertTrue("payload was $payload", payload is JsonObject)
        assertEquals("read", payload!!.jsonObject["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `the type and schema version stay outside the payload`() {
        val entry = exportOf(encoded(1, habitCreated(habitId(9)))).getValue("events").jsonArray.single().jsonObject

        assertEquals("HabitCreated", entry["type"]?.jsonPrimitive?.content)
        assertEquals(1, entry["schema_version"]?.jsonPrimitive?.content?.toInt())
        assertTrue(entry.getValue("payload").jsonObject.keys.none { it == "type" || it == "schema_version" })
    }

    /**
     * A key this build does not know survives the round trip.
     *
     * The one test that kills decode-then-re-encode. Routing the payload
     * through `HabitCreatedWireV1` drops `from_the_future` — `wireJson` sets
     * `ignoreUnknownKeys` precisely so a rolled-back build can still read what
     * a newer one wrote — and rewrites `schema_version` to the current one.
     * Both are the log being migrated in place, which architecture §3 forbids,
     * and both are invisible to every other assertion in this file.
     */
    @Test
    fun `an unknown payload key survives a round trip`() {
        val fromTheFuture = EncodedPayload(
            type = "HabitCreated",
            schemaVersion = 1,
            json = """{"habit_id":"${uuid(9)}","name":"read","icon":"book",""" +
                """"color":"#aabbcc","schedule":{"kind":"daily"},"from_the_future":true}""",
        )
        val log = listOf(EncodedEvent(eventId(1), EXPORTED_AT, tzOffsetMin = 0, payload = fromTheFuture))

        val payload = readBack(log).single().payload

        assertEquals(1, payload.schemaVersion)
        assertTrue(payload.json, Json.parseToJsonElement(payload.json).jsonObject.containsKey("from_the_future"))
    }

    @Test
    fun `an empty log round-trips`() {
        assertEquals(emptyList<EncodedEvent>(), readBack(emptyList()))
    }

    @Test
    fun `instants keep millisecond precision`() {
        val at = Instant.parse("2026-08-17T08:00:00.412Z")
        val log = listOf(EncodedEvent(eventId(1), at, tzOffsetMin = 480, payload = payloads.encode(habitCreated(habitId(9)))))

        assertEquals(at, readBack(log).single().occurredAt)
        assertEquals(480, readBack(log).single().tzOffsetMin)
    }

    /**
     * A third-party writer reaching for a nanosecond clock is the likeliest
     * mistake an open format invites, and the column stores millis, so the
     * difference is one this app does not record. Truncated, not refused.
     */
    @Test
    fun `sub-millisecond precision is truncated rather than refused`() {
        val text = fileWith(occurredAt = "2026-08-17T08:00:00.412987654Z")

        assertEquals(Instant.parse("2026-08-17T08:00:00.412Z"), events(text).single().occurredAt)
    }

    // --- refusals -------------------------------------------------------

    /**
     * A newer envelope is unsupported, not malformed.
     *
     * Mutation-checked, and the reason `ExportReader` reads the version off the
     * raw object first. Telling someone their only backup is damaged when it is
     * merely newer than this build is the worst message this screen can send.
     */
    @Test
    fun `a newer format version is unsupported rather than malformed`() {
        val refusal = refusalFrom(fileWith(formatVersion = 2))

        assertEquals(ExportRejection.UnsupportedFormatVersion(found = 2, supported = 1), refusal)
    }

    /**
     * The mutation this really guards: checking the version *after* decoding
     * the envelope. A v2 that renamed `events` then fails as unreadable, and
     * the previous test still passes because its v2 file happens to be
     * v1-shaped. This one is the honest version of that check.
     */
    @Test
    fun `a newer envelope that renamed its fields is still unsupported`() {
        val text = """
            {"format":"gawi.event-log","format_version":2,"exported_at":"$EXPORTED_AT",
             "app_version":"9.9.9","entry_count":0,"entries":[]}
        """.trimIndent()

        assertEquals(ExportRejection.UnsupportedFormatVersion(found = 2, supported = 1), refusalFrom(text))
    }

    @Test
    fun `text that is not json is malformed`() {
        assertTrue(refusalFrom("not json at all") is ExportRejection.Malformed)
    }

    @Test
    fun `a json array is not an export`() {
        assertEquals(ExportRejection.NotAnExport, refusalFrom("""[1,2,3]"""))
    }

    @Test
    fun `an object without the marker is not an export`() {
        assertEquals(ExportRejection.NotAnExport, refusalFrom("""{"hello":"world"}"""))
    }

    @Test
    fun `our format with no version is not an export`() {
        assertEquals(ExportRejection.NotAnExport, refusalFrom("""{"format":"gawi.event-log"}"""))
    }

    /**
     * A file that carries our marker and a version we cannot read is ours and
     * broken, not somebody else's file. A future envelope writing `2.0` would
     * otherwise be turned away with the least useful message available.
     */
    @Test
    fun `a version that is not a whole number is malformed rather than foreign`() {
        val text = """{"format":"gawi.event-log","format_version":2.0,"events":[]}"""

        val refusal = refusalFrom(text)

        assertTrue("$refusal", refusal is ExportRejection.Malformed)
    }

    /** A quoted version is not a version either, and says so honestly. */
    @Test
    fun `a version written as a string is malformed`() {
        val refusal = refusalFrom("""{"format":"gawi.event-log","format_version":"1","events":[]}""")

        assertTrue("$refusal", refusal is ExportRejection.Malformed)
    }

    /**
     * Below ours is still unsupported, and the rejection carries both numbers
     * so a caller can tell the two directions apart. `EventLogArchive` is where
     * that matters — telling someone to update for a version we never wrote
     * would be advice that cannot come true.
     */
    @Test
    fun `a version below ours reports both numbers`() {
        val refusal = refusalFrom(fileWith(formatVersion = 0))

        assertEquals(ExportRejection.UnsupportedFormatVersion(found = 0, supported = 1), refusal)
    }

    @Test
    fun `an event count that disagrees with the array is malformed`() {
        val refusal = refusalFrom(fileWith(eventCount = 7))

        assertTrue((refusal as ExportRejection.Malformed).detail, refusal.detail.contains("declared 7"))
    }

    @Test
    fun `an uppercase id is refused and the message says lowercase`() {
        // uuid(9) has no hex letters, so .uppercase() on it is a no-op and the
        // assertion would pass against a reader that lowercases silently.
        val refusal = refusalFrom(fileWith(id = uuid(0xABCDEF).uppercase()))

        assertTrue((refusal as ExportRejection.Malformed).detail, refusal.detail.contains("lowercase"))
    }

    @Test
    fun `an unknown event type is malformed`() {
        assertTrue(refusalFrom(fileWith(type = "HabitTeleported")) is ExportRejection.Malformed)
    }

    @Test
    fun `an unknown schema version is malformed`() {
        assertTrue(refusalFrom(fileWith(schemaVersion = 99)) is ExportRejection.Malformed)
    }

    @Test
    fun `a corrupt payload body is malformed`() {
        assertTrue(refusalFrom(fileWith(payload = """{"habit_id":"not-a-uuid"}""")) is ExportRejection.Malformed)
    }

    /**
     * Nothing reads `tz_offset_min` today, which is why it is checked now
     * rather than later: unvalidated, the first consumer to build a
     * `ZoneOffset` from it would get a `DateTimeException` from inside a
     * transaction, on an event nobody can point at any more.
     */
    @Test
    fun `an offset outside the legal range is malformed`() {
        val refusal = refusalFrom(fileWith(tzOffsetMin = 99_999)) as ExportRejection.Malformed

        assertTrue(refusal.detail, refusal.detail.startsWith("event 0 (${uuid(1)})"))
        assertTrue(refusal.detail, refusal.detail.contains("tz_offset_min"))
    }

    @Test
    fun `the extremes of the legal offset range are accepted`() {
        assertEquals(1_080, events(fileWith(tzOffsetMin = 1_080)).single().tzOffsetMin)
        assertEquals(-1_080, events(fileWith(tzOffsetMin = -1_080)).single().tzOffsetMin)
    }

    @Test
    fun `an unreadable instant is malformed`() {
        assertTrue(refusalFrom(fileWith(occurredAt = "yesterday")) is ExportRejection.Malformed)
    }

    /**
     * Naming the offending event is what makes refusing the whole file
     * affordable: an open format a user can repair by hand is the compensating
     * control for all-or-nothing.
     */
    @Test
    fun `a malformed event names its position and id`() {
        val refusal = refusalFrom(fileWith(type = "HabitTeleported")) as ExportRejection.Malformed

        assertTrue(refusal.detail, refusal.detail.startsWith("event 0 (${uuid(1)})"))
    }

    // --- helpers --------------------------------------------------------

    private fun encoded(n: Int, payload: com.gawi.core.domain.event.EventPayload) =
        EncodedEvent(eventId(n), EXPORTED_AT.plusMillis(n.toLong()), tzOffsetMin = 0, payload = payloads.encode(payload))

    private fun exportOf(vararg events: EncodedEvent): JsonObject = exportOf(events.toList())

    private fun exportOf(events: List<EncodedEvent>): JsonObject = Json.parseToJsonElement(codec.encode(events, META)).jsonObject

    private fun readBack(log: List<EncodedEvent>): List<EncodedEvent> = events(codec.encode(log, META))

    private fun events(text: String): List<EncodedEvent> = when (val read = codec.decode(text)) {
        is ExportRead.Events -> read.events
        is ExportRead.Refused -> error("refused: ${read.reason}")
    }

    private fun refusalFrom(text: String): ExportRejection = when (val read = codec.decode(text)) {
        is ExportRead.Refused -> read.reason
        is ExportRead.Events -> error("expected a refusal, read ${read.events.size} events")
    }

    /** A one-event file with exactly one thing wrong with it, or nothing. */
    @Suppress("LongParameterList")
    private fun fileWith(
        formatVersion: Int = 1,
        eventCount: Int = 1,
        id: String = uuid(1),
        occurredAt: String = "2026-08-17T08:00:00Z",
        tzOffsetMin: Int = 0,
        type: String = "HabitCreated",
        schemaVersion: Int = 1,
        payload: String = """{"habit_id":"${uuid(9)}","name":"read","icon":"book","color":"#aabbcc","schedule":{"kind":"daily"}}""",
    ) = """
        {"format":"gawi.event-log","format_version":$formatVersion,
         "exported_at":"$EXPORTED_AT","app_version":"0.1.0","event_count":$eventCount,
         "events":[{"id":"$id","occurred_at":"$occurredAt","tz_offset_min":$tzOffsetMin,
                    "type":"$type","schema_version":$schemaVersion,"payload":$payload}]}
    """.trimIndent()

    private companion object {
        val EXPORTED_AT: Instant = Instant.parse("2026-08-20T09:15:32.412Z")
        val META = ExportMeta(EXPORTED_AT, appVersion = "0.1.0")

        val ALL_PAYLOAD_TYPES = listOf(
            habitCreated(habitId(101), tag = "mind"),
            com.gawi.core.domain.event.HabitUpdated(
                habitId(101),
                "read more",
                "book",
                "#aabbcc",
                com.gawi.core.domain.model.Schedule.Daily,
                null,
            ),
            com.gawi.core.domain.event.HabitArchived(habitId(101)),
            com.gawi.core.domain.event.HabitUnarchived(habitId(101)),
            completionAdded(habitId(101), "2026-08-17", note = "early"),
            com.gawi.core.domain.event.CompletionTombstoned(eventId(5)),
            com.gawi.core.domain.event.CompletionNoteUpdated(eventId(5), "later"),
        )
    }
}
