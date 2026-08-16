package com.gawi.core.domain.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EventIdTest {

    @Test
    fun `accepts a canonical lowercase uuid`() {
        val id = EventId("0190163d-8694-7abc-8def-0123456789ab")

        assertEquals("0190163d-8694-7abc-8def-0123456789ab", id.value)
    }

    @Test
    fun `rejects uppercase hex`() {
        assertThrows(IllegalArgumentException::class.java) {
            EventId("0190163D-8694-7ABC-8DEF-0123456789AB")
        }
    }

    @Test
    fun `rejects malformed strings`() {
        assertThrows(IllegalArgumentException::class.java) { EventId("not-a-uuid") }
    }

    @Test
    fun `rejects a uuid without dashes`() {
        assertThrows(IllegalArgumentException::class.java) {
            EventId("0190163d86947abc8def0123456789ab")
        }
    }

    @Test
    fun `compares by canonical string order`() {
        val smaller = EventId("0190163d-8694-7abc-8def-0123456789ab")
        val larger = EventId("0190163d-8694-7abd-8def-0123456789ab")

        assertTrue(smaller < larger)
        assertTrue(smaller.value < larger.value)
    }

    @Test
    fun `toString is the raw value`() {
        val id = EventId("0190163d-8694-7abc-8def-0123456789ab")

        assertEquals(id.value, id.toString())
    }
}
