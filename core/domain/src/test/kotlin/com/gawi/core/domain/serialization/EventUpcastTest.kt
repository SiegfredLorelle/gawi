package com.gawi.core.domain.serialization

import com.gawi.core.domain.event.HabitCreated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the version-dispatch machinery that upcasting rides on. All
 * types are at v1 today, so the covered upcast mechanics are the ones a v2
 * will reuse: absent fields fill from defaults (how an old shape gains a
 * new field) and unknown fields are ignored (how a rolled-back reader
 * survives a newer writer). Unknown types/versions fail loudly.
 */
class EventUpcastTest {

    private val codec = EventCodec()

    @Test
    fun `a fixture missing an optional field decodes with the default`() {
        val decoded = codec.decode(
            "HabitCreated",
            1,
            """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab","name":"Read",""" +
                """"icon":"book","color":"#aabbcc","schedule":{"kind":"daily"}}""",
        ) as HabitCreated

        assertNull(decoded.tag)
    }

    @Test
    fun `a fixture with unknown extra fields decodes ignoring them`() {
        val decoded = codec.decode(
            "HabitCreated",
            1,
            """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab","name":"Read",""" +
                """"icon":"book","color":"#aabbcc","schedule":{"kind":"daily"},""" +
                """"from_the_future":true}""",
        ) as HabitCreated

        assertEquals("Read", decoded.name)
    }

    @Test
    fun `an unknown schema version fails loudly`() {
        val error = assertThrows(EventCodecException::class.java) {
            codec.decode("HabitCreated", 99, """{}""")
        }

        assertTrue(error.message!!.contains("HabitCreated"))
        assertTrue(error.message!!.contains("99"))
    }

    @Test
    fun `a corrupt payload body surfaces as the codec exception`() {
        assertThrows(EventCodecException::class.java) {
            codec.decode("HabitCreated", 1, """not json at all""")
        }
        assertThrows(EventCodecException::class.java) {
            codec.decode(
                "HabitCreated",
                1,
                """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab","name":"Read",""" +
                    """"icon":"book","color":"#aabbcc","schedule":{"kind":"biweekly"}}""",
            )
        }
        assertThrows(EventCodecException::class.java) {
            codec.decode(
                "CompletionAdded",
                1,
                """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab","logical_date":"not-a-date"}""",
            )
        }
        assertThrows(EventCodecException::class.java) {
            codec.decode(
                "CompletionTombstoned",
                1,
                """{"completion_event_id":"NOT-CANONICAL"}""",
            )
        }
    }

    @Test
    fun `an unknown event type fails loudly`() {
        val error = assertThrows(EventCodecException::class.java) {
            codec.decode("HabitPetted", 1, """{}""")
        }

        assertTrue(error.message!!.contains("HabitPetted"))
    }

    @Test
    fun `decode goes through the public entry point for every known version`() {
        val encoded = codec.encode(
            codec.decode(
                "CompletionAdded",
                1,
                """{"habit_id":"0190163d-8694-7abc-8def-0123456789ab","logical_date":"2026-08-17"}""",
            ),
        )

        assertEquals(1, encoded.schemaVersion)
        assertEquals("CompletionAdded", encoded.type)
    }
}
