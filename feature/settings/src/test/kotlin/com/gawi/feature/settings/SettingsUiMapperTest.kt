package com.gawi.feature.settings

import com.gawi.core.data.backup.ExportStatus
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

    // --- the last-export nudge (PRD §5) ----------------------------------

    /**
     * An absent stamp means two different things and the log decides which.
     *
     * This is the whole reason [recencyOf] exists rather than a null check at
     * the call site: on a fresh install there is nothing to lose and a warning
     * about it is noise, and on a log with events it is exactly the case the
     * nudge was asked for.
     */
    @Test
    fun `never exported over an empty log says nothing`() {
        assertEquals(ExportRecency.NothingYet, recencyOf(ExportStatus(daysSinceExport = null, hasEvents = false)))
    }

    @Test
    fun `never exported over a log with something in it is the case the nudge is for`() {
        assertEquals(ExportRecency.Never, recencyOf(ExportStatus(daysSinceExport = null, hasEvents = true)))
    }

    /** Nought days is an answer, not a count. "0 days ago" is arithmetic. */
    @Test
    fun `an export today reads as today rather than as nought days`() {
        assertEquals(ExportRecency.Today, recencyOf(status(days = 0)))
    }

    @Test
    fun `a day count is carried through`() {
        assertEquals(ExportRecency.DaysAgo(1), recencyOf(status(days = 1)))
        assertEquals(ExportRecency.DaysAgo(34), recencyOf(status(days = 34)))
    }

    /**
     * The threshold PRD §5 names, in literal days on both sides of itself.
     *
     * **Written as 29, 30 and 31 and not as the constant plus or minus one.**
     * The first draft of these used `EXPORT_NUDGE_DAYS - 1`, which moves with
     * the constant and therefore passes at any threshold — a vacuous boundary
     * test, caught by moving the constant to 31 and watching nothing redden.
     * The number is pinned separately, below.
     *
     * Asserted through [exportHelp] rather than a private predicate, because
     * which sentence the row shows is the only part of this a user can see.
     */
    @Test
    fun `twenty-nine days is not yet overdue`() {
        assertEquals(R.string.settings_export_help, exportHelp(RowActivity.Live, ExportRecency.DaysAgo(29)))
    }

    @Test
    fun `thirty days is overdue`() {
        assertEquals(R.string.settings_export_overdue_help, exportHelp(RowActivity.Live, ExportRecency.DaysAgo(30)))
    }

    @Test
    fun `thirty-one days stays overdue`() {
        assertEquals(R.string.settings_export_overdue_help, exportHelp(RowActivity.Live, ExportRecency.DaysAgo(31)))
    }

    /** The PRD's own number, so the three above cannot drift away from it together. */
    @Test
    fun `the threshold is the thirty days the PRD asks for`() {
        assertEquals(30, EXPORT_NUDGE_DAYS)
    }

    @Test
    fun `never exported is overdue and an empty log is not`() {
        assertEquals(R.string.settings_export_overdue_help, exportHelp(RowActivity.Live, ExportRecency.Never))
        assertEquals(R.string.settings_export_help, exportHelp(RowActivity.Live, ExportRecency.NothingYet))
        assertEquals(R.string.settings_export_help, exportHelp(RowActivity.Live, ExportRecency.Today))
    }

    /**
     * A row writing a file right now has no business saying there is no backup.
     *
     * Running wins over overdue, and the overdue case is the one that makes the
     * order visible — with the plain help line as the loser either way, a
     * reversed precedence would look correct.
     */
    @Test
    fun `a running export says so rather than nudging`() {
        assertEquals(R.string.settings_export_running, exportHelp(RowActivity.Running, ExportRecency.Never))
        assertEquals(R.string.settings_export_overdue_help, exportHelp(RowActivity.Blocked, ExportRecency.Never))
    }

    @Test
    fun `the import row has a status and no nudge`() {
        assertEquals(R.string.settings_import_running, importHelp(RowActivity.Running))
        assertEquals(R.string.settings_import_help, importHelp(RowActivity.Live))
        assertEquals(R.string.settings_import_help, importHelp(RowActivity.Blocked))
    }

    /**
     * Both rows go dead while either runs, and they differ only in which one is
     * announced. The row that is *not* running is the easy one to leave live.
     */
    @Test
    fun `a task makes its own row running and the other one blocked`() {
        assertEquals(RowActivity.Live, activityOf(DataTask.Idle, DataTask.Exporting))
        assertEquals(RowActivity.Live, activityOf(DataTask.Idle, DataTask.Importing))
        assertEquals(RowActivity.Running, activityOf(DataTask.Exporting, DataTask.Exporting))
        assertEquals(RowActivity.Blocked, activityOf(DataTask.Exporting, DataTask.Importing))
        assertEquals(RowActivity.Running, activityOf(DataTask.Importing, DataTask.Importing))
        assertEquals(RowActivity.Blocked, activityOf(DataTask.Importing, DataTask.Exporting))
    }

    @Test
    fun `the state carries the recency it was given`() {
        val state = UserSettings().toUiState(exportRecency = ExportRecency.DaysAgo(34)) as SettingsUiState.Settings

        assertEquals(ExportRecency.DaysAgo(34), state.exportRecency)
    }

    @Test
    fun `the state defaults to saying nothing about exports`() {
        val state = UserSettings().toUiState() as SettingsUiState.Settings

        assertEquals(ExportRecency.NothingYet, state.exportRecency)
    }

    private fun status(days: Int) = ExportStatus(daysSinceExport = days.toLong(), hasEvents = true)

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
