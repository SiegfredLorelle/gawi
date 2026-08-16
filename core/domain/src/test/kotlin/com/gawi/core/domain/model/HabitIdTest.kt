package com.gawi.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HabitIdTest {

    @Test
    fun `accepts a canonical lowercase uuid`() {
        val id = HabitId("0190163d-8694-7abc-8def-0123456789ab")

        assertEquals("0190163d-8694-7abc-8def-0123456789ab", id.toString())
    }

    @Test
    fun `rejects non-canonical strings`() {
        assertThrows(IllegalArgumentException::class.java) { HabitId("habit-1") }
    }
}
