package com.gawi.core.domain.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class LogicalDateTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")
    private val manila = ZoneId.of("Asia/Manila")
    private val losAngeles = ZoneId.of("America/Los_Angeles")
    private val kathmandu = ZoneId.of("Asia/Kathmandu")
    private val cutoff3 = LocalTime.of(3, 0)
    private val midnight = LocalTime.MIDNIGHT

    private fun at(zone: ZoneId, dateTime: String): Instant = LocalDateTime.parse(dateTime).atZone(zone).toInstant()

    private fun date(s: String): LocalDate = LocalDate.parse(s)

    @Test
    fun `half past one belongs to the previous day under a three am cutoff`() {
        assertEquals(date("2026-08-15"), logicalDate(at(berlin, "2026-08-16T01:30:00"), cutoff3, berlin))
    }

    @Test
    fun `a wall time exactly at the cutoff starts the new day`() {
        assertEquals(date("2026-08-16"), logicalDate(at(berlin, "2026-08-16T03:00:00"), cutoff3, berlin))
    }

    @Test
    fun `one millisecond before the cutoff is still the previous day`() {
        val instant = at(berlin, "2026-08-16T02:59:59.999")

        assertEquals(date("2026-08-15"), logicalDate(instant, cutoff3, berlin))
    }

    @Test
    fun `after the cutoff the calendar date is the logical date`() {
        assertEquals(date("2026-08-16"), logicalDate(at(berlin, "2026-08-16T04:00:00"), cutoff3, berlin))
    }

    @Test
    fun `midnight cutoff maps every instant to its plain calendar date`() {
        assertEquals(date("2026-08-16"), logicalDate(at(berlin, "2026-08-16T00:00:00"), midnight, berlin))
        assertEquals(date("2026-08-16"), logicalDate(at(berlin, "2026-08-16T23:59:59"), midnight, berlin))
    }

    @Test
    fun `a late evening cutoff shifts the small hours and the evening differently`() {
        val cutoff = LocalTime.of(23, 0)

        assertEquals(date("2026-08-15"), logicalDate(at(berlin, "2026-08-16T22:59:00"), cutoff, berlin))
        assertEquals(date("2026-08-16"), logicalDate(at(berlin, "2026-08-16T23:01:00"), cutoff, berlin))
    }

    @Test
    fun `berlin spring forward partitions the day even when the cutoff does not exist`() {
        val nonexistentCutoff = LocalTime.of(2, 30)

        val beforeGap = Instant.parse("2026-03-29T00:59:00Z")
        val firstAfterGap = Instant.parse("2026-03-29T01:00:00Z")

        assertEquals(date("2026-03-28"), logicalDate(beforeGap, nonexistentCutoff, berlin))
        assertEquals(date("2026-03-29"), logicalDate(firstAfterGap, nonexistentCutoff, berlin))
    }

    @Test
    fun `berlin spring forward with a cutoff at the gap edge starts the new day at it`() {
        val firstInstantAfterGap = Instant.parse("2026-03-29T01:00:00Z")

        assertEquals(date("2026-03-29"), logicalDate(firstInstantAfterGap, cutoff3, berlin))
    }

    @Test
    fun `berlin fall back maps both occurrences of a repeated wall time the same way`() {
        val halfPastTwoCest = Instant.parse("2026-10-25T00:30:00Z")
        val halfPastTwoCet = Instant.parse("2026-10-25T01:30:00Z")

        assertEquals(date("2026-10-25"), logicalDate(halfPastTwoCest, LocalTime.of(2, 30), berlin))
        assertEquals(date("2026-10-25"), logicalDate(halfPastTwoCet, LocalTime.of(2, 30), berlin))
        assertEquals(date("2026-10-24"), logicalDate(halfPastTwoCest, cutoff3, berlin))
        assertEquals(date("2026-10-24"), logicalDate(halfPastTwoCet, cutoff3, berlin))
    }

    @Test
    fun `berlin fall back keeps all twenty-five hours on one calendar date under midnight cutoff`() {
        val startOfDay = Instant.parse("2026-10-24T22:00:00Z")

        for (hour in 0 until 25) {
            val instant = startOfDay.plusSeconds(hour * 3600L)
            assertEquals("hour $hour diverged", date("2026-10-25"), logicalDate(instant, midnight, berlin))
        }
    }

    @Test
    fun `new york spring forward respects a cutoff at the gap start`() {
        val cutoff = LocalTime.of(2, 0)
        val beforeGap = Instant.parse("2026-03-08T06:59:00Z")
        val afterGap = Instant.parse("2026-03-08T07:01:00Z")

        assertEquals(date("2026-03-07"), logicalDate(beforeGap, cutoff, newYork))
        assertEquals(date("2026-03-08"), logicalDate(afterGap, cutoff, newYork))
    }

    @Test
    fun `new york fall back maps both repeated wall times to the same logical date`() {
        val halfPastOneEdt = Instant.parse("2026-11-01T05:30:00Z")
        val halfPastOneEst = Instant.parse("2026-11-01T06:30:00Z")
        val cutoff = LocalTime.of(1, 30)

        assertEquals(date("2026-11-01"), logicalDate(halfPastOneEdt, cutoff, newYork))
        assertEquals(date("2026-11-01"), logicalDate(halfPastOneEst, cutoff, newYork))
    }

    @Test
    fun `the same instant lands on different dates in different zones`() {
        val instant = Instant.parse("2026-08-16T18:00:00Z")

        assertEquals(date("2026-08-17"), logicalDate(instant, midnight, manila))
        assertEquals(date("2026-08-16"), logicalDate(instant, midnight, losAngeles))
    }

    @Test
    fun `a non-hour offset zone gets its midnight boundary right`() {
        val justBeforeMidnight = Instant.parse("2026-08-16T18:14:59Z")
        val atMidnight = Instant.parse("2026-08-16T18:15:00Z")

        assertEquals(date("2026-08-16"), logicalDate(justBeforeMidnight, midnight, kathmandu))
        assertEquals(date("2026-08-17"), logicalDate(atMidnight, midnight, kathmandu))
    }

    @Test
    fun `changing the cutoff re-buckets nothing because stored dates never recompute`() {
        val instant = at(berlin, "2026-08-16T01:30:00")

        val storedAtLogTime = logicalDate(instant, cutoff3, berlin)
        val underNewSetting = logicalDate(instant, midnight, berlin)

        assertNotEquals(storedAtLogTime, underNewSetting)
    }
}
