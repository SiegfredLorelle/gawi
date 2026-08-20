package com.gawi.feature.settings

import com.gawi.core.data.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The mapper, which is where this screen's two formatting decisions live.
 *
 * Pure JVM: no Robolectric, no composition. What is worth pinning here is that
 * the state carries the stored values rather than defaults, that every day has
 * a name, and that both clock conventions read the way a person expects at the
 * three places a 12-hour clock is easy to get wrong — midnight, noon, and a
 * single-digit hour.
 */
class SettingsUiMapperTest {

    @Test
    fun `the state carries the stored settings`() {
        val settings = UserSettings(
            dayCutoff = LocalTime.of(3, 30),
            weekStart = DayOfWeek.SUNDAY,
            reminderTime = LocalTime.of(22, 15),
        )

        val state = settings.toUiState()

        assertEquals(
            SettingsUiState.Settings(LocalTime.of(3, 30), DayOfWeek.SUNDAY, LocalTime.of(22, 15)),
            state,
        )
    }

    @Test
    fun `the defaults are the PRD's own`() {
        val state = UserSettings().toUiState() as SettingsUiState.Settings

        assertEquals(LocalTime.MIDNIGHT, state.dayCutoff)
        assertEquals(DayOfWeek.MONDAY, state.weekStart)
        assertEquals(LocalTime.of(21, 0), state.reminderTime)
    }

    /**
     * Every day is offered, and every one of them has a name.
     *
     * A missing branch is a compile error rather than a test failure, since the
     * `when` is exhaustive — what this catches is the other half, a branch that
     * points at a resource that was never written.
     */
    @Test
    fun `every day of the week is offered and named distinctly`() {
        assertEquals(7, WEEK_START_OPTIONS.size)
        assertEquals(DayOfWeek.MONDAY, WEEK_START_OPTIONS.first())

        val labels = WEEK_START_OPTIONS.map { labelFor(it) }

        assertEquals(7, labels.toSet().size)
        assertTrue(labels.all { it != 0 })
    }

    @Test
    fun `a 24 hour clock pads the hour and never says midday`() {
        assertEquals("00:00", formatTime(LocalTime.MIDNIGHT, is24Hour = true))
        assertEquals("09:05", formatTime(LocalTime.of(9, 5), is24Hour = true))
        assertEquals("12:00", formatTime(LocalTime.NOON, is24Hour = true))
        assertEquals("21:00", formatTime(LocalTime.of(21, 0), is24Hour = true))
    }

    /**
     * The three cases a 12-hour clock gets wrong when it is written by hand.
     *
     * Midnight is 12 AM and not 0 AM, noon is 12 PM and not 12 AM, and an hour
     * below ten is not padded.
     */
    @Test
    fun `a 12 hour clock reads midnight and noon correctly`() {
        assertEquals("12:00 AM", formatTime(LocalTime.MIDNIGHT, is24Hour = false))
        assertEquals("9:05 AM", formatTime(LocalTime.of(9, 5), is24Hour = false))
        assertEquals("12:00 PM", formatTime(LocalTime.NOON, is24Hour = false))
        assertEquals("9:00 PM", formatTime(LocalTime.of(21, 0), is24Hour = false))
    }
}
