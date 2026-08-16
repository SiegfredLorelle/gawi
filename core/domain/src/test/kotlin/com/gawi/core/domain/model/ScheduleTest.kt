package com.gawi.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScheduleTest {

    @Test
    fun `weekly accepts targets from one to seven`() {
        for (n in 1..7) {
            assertEquals(n, Schedule.Weekly(timesPerWeek = n).timesPerWeek)
        }
    }

    @Test
    fun `weekly rejects a zero target`() {
        assertThrows(IllegalArgumentException::class.java) { Schedule.Weekly(timesPerWeek = 0) }
    }

    @Test
    fun `weekly rejects a target above seven`() {
        assertThrows(IllegalArgumentException::class.java) { Schedule.Weekly(timesPerWeek = 8) }
    }

    @Test
    fun `weekly rejects a negative target`() {
        assertThrows(IllegalArgumentException::class.java) { Schedule.Weekly(timesPerWeek = -1) }
    }
}
