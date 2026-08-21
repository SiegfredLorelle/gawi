package com.gawi.feature.settings

import com.gawi.core.data.backup.ExportStatus
import com.gawi.core.data.backup.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The Data section's decisions, which is everything on this screen that is
 * about a file rather than about a preference.
 *
 * Pure JVM: no Robolectric, no composition. Split from `SettingsUiMapperTest`
 * with the production file it covers, so an assertion sits beside the thing it
 * asserts.
 */
class SettingsDataMapperTest {

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
    fun `a task makes its own row running and the others blocked`() {
        assertEquals(RowActivity.Live, activityOf(DataTask.Idle, DataTask.Exporting))
        assertEquals(RowActivity.Live, activityOf(DataTask.Idle, DataTask.Importing))
        assertEquals(RowActivity.Live, activityOf(DataTask.Idle, DataTask.ExportingCsv))
        assertEquals(RowActivity.Running, activityOf(DataTask.Exporting, DataTask.Exporting))
        assertEquals(RowActivity.Blocked, activityOf(DataTask.Exporting, DataTask.Importing))
        assertEquals(RowActivity.Blocked, activityOf(DataTask.Exporting, DataTask.ExportingCsv))
        assertEquals(RowActivity.Running, activityOf(DataTask.Importing, DataTask.Importing))
        assertEquals(RowActivity.Blocked, activityOf(DataTask.Importing, DataTask.Exporting))
        assertEquals(RowActivity.Blocked, activityOf(DataTask.Importing, DataTask.ExportingCsv))
    }

    /**
     * The CSV is the row most easily left live by accident, because it reads
     * nothing and writes somewhere else — but a spreadsheet of a log that is
     * still being merged is a spreadsheet of a state that never existed.
     */
    @Test
    fun `a csv export blocks the other two rows and runs its own`() {
        assertEquals(RowActivity.Running, activityOf(DataTask.ExportingCsv, DataTask.ExportingCsv))
        assertEquals(RowActivity.Blocked, activityOf(DataTask.ExportingCsv, DataTask.Exporting))
        assertEquals(RowActivity.Blocked, activityOf(DataTask.ExportingCsv, DataTask.Importing))
    }

    private fun status(days: Int) = ExportStatus(daysSinceExport = days.toLong(), hasEvents = true)

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

    // --- the CSV of completions (PRD §5) ----------------------------------

    @Test
    fun `the csv row has a status and no nudge`() {
        assertEquals(R.string.settings_export_csv_running, csvHelp(RowActivity.Running))
        assertEquals(R.string.settings_export_csv_help, csvHelp(RowActivity.Live))
        assertEquals(R.string.settings_export_csv_help, csvHelp(RowActivity.Blocked))
    }

    /**
     * The row that must never settle the 30-day warning.
     *
     * A CSV holds no events, so offering it as an answer to "there is no other
     * copy of your history" would tell the user something false about their own
     * data. `csvHelp` takes no [ExportRecency] at all, which is what makes this
     * true by construction rather than by branch — so what is asserted here is
     * the consequence: neither of its two sentences is the nudge, and neither is
     * the JSON row's either.
     */
    @Test
    fun `the csv row never shows the overdue nudge`() {
        val csvSentences = RowActivity.entries.map { csvHelp(it) }.toSet()

        assertEquals(2, csvSentences.size)
        assertFalse(csvSentences.contains(R.string.settings_export_overdue_help))
        assertFalse(csvSentences.contains(R.string.settings_export_help))
    }

    @Test
    fun `an empty csv export is not the same message as a full one`() {
        assertEquals(SettingsMessage(R.string.settings_export_csv_empty), csvMessageFor(rows = 0))
        assertEquals(SettingsMessage(R.string.settings_export_csv_done), csvMessageFor(rows = 1))
        assertEquals(SettingsMessage(R.string.settings_export_csv_done), csvMessageFor(rows = 327))
    }

    /** No count in the copy, so no `<plurals>` and nothing to interpolate. */
    @Test
    fun `a csv message carries no arguments`() {
        assertEquals(emptyList<Any>(), csvMessageFor(rows = 327).args)
        assertEquals(emptyList<Any>(), csvMessageFor(rows = 0).args)
    }

    /**
     * A different stem and not just a different extension, so the two files are
     * told apart by the part a person reads first. ISO order for the reason
     * [exportFileName] gives.
     */
    @Test
    fun `the csv file name names the completions and sorts chronologically`() {
        assertEquals("gawi-completions-2026-08-21.csv", csvFileName(LocalDate.of(2026, 8, 21)))
        assertEquals("gawi-completions-2026-01-05.csv", csvFileName(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `the two export file names cannot be mistaken for each other`() {
        val day = LocalDate.of(2026, 8, 21)

        assertNotEquals(csvFileName(day), exportFileName(day))
        assertTrue(exportFileName(day).endsWith(".json"))
        assertTrue(csvFileName(day).endsWith(".csv"))
    }
}
