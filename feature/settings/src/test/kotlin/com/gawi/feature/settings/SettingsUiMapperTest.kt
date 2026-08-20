package com.gawi.feature.settings

import com.gawi.core.data.backup.ImportResult
import com.gawi.core.data.settings.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
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

    // --- the export file name ---------------------------------------------

    /**
     * Mutation-checked. A `dd-MM-yyyy` pattern reads perfectly well and
     * destroys the one property the name is for — that a folder of these sorts
     * chronologically — and a locale-dependent formatter is the same bug
     * `formatTime`'s `Locale.ROOT` guards against.
     */
    @Test
    fun `the export file name is the date in ISO order`() {
        assertEquals("gawi-export-2026-08-20.json", exportFileName(LocalDate.of(2026, 8, 20)))
    }

    @Test
    fun `a single digit month and day are padded`() {
        assertEquals("gawi-export-2026-01-05.json", exportFileName(LocalDate.of(2026, 1, 5)))
    }

    // --- what an import says ----------------------------------------------

    @Test
    fun `an import that added something reports both counts`() {
        val message = messageFor(ImportResult.Merged(read = 140, added = 128))

        assertEquals(SettingsMessage(R.string.settings_import_done, listOf(128, 12)), message)
    }

    /**
     * One added and one skipped is the case the copy has to survive without a
     * `<plurals>`. That it *reads* correctly is asserted in `SettingsScreenTest`,
     * which has resources; what is pinned here is that both counts arrive.
     */
    @Test
    fun `a single added entry carries both counts`() {
        assertEquals(
            SettingsMessage(R.string.settings_import_done, listOf(1, 1)),
            messageFor(ImportResult.Merged(read = 2, added = 1)),
        )
    }

    /**
     * Mutation-checked: collapsing these two branches is invisible everywhere
     * else, and one of them would then tell the user something false about
     * their file.
     */
    @Test
    fun `a file of duplicates is not the same message as an empty one`() {
        assertEquals(SettingsMessage(R.string.settings_import_nothing_new), messageFor(ImportResult.Merged(read = 9, added = 0)))
        assertEquals(SettingsMessage(R.string.settings_import_empty), messageFor(ImportResult.Merged(read = 0, added = 0)))
    }

    @Test
    fun `the wrong file and a damaged one read the same`() {
        val unreadable = SettingsMessage(R.string.settings_error_import_unreadable)

        assertEquals(unreadable, messageFor(ImportResult.Refused.NotAnExport))
        assertEquals(unreadable, messageFor(ImportResult.Refused.Damaged("event 3: nope")))
    }

    /** Intact but newer must never read as damage — the fix is to update. */
    @Test
    fun `a newer export is not reported as damage`() {
        val message = messageFor(ImportResult.Refused.FromANewerVersion(formatVersion = 2))

        assertEquals(SettingsMessage(R.string.settings_error_import_newer), message)
    }
}
