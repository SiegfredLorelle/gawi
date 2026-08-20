package com.gawi.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gawi.core.data.backup.ImportResult
import com.gawi.core.ui.theme.GawiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The screen, rendered.
 *
 * On the JVM under Robolectric, so this sits inside the existing `make test`
 * gate and architecture §8's "CI runs unit tests only" stays true. Aimed at
 * [SettingsScreen], the stateless composable, rather than at [SettingsRoute]:
 * no Hilt graph and no ViewModel are involved in a red result.
 *
 * Strings are resolved from the test context rather than written out here, so
 * these assertions survive a copy edit and fail only on a behaviour change.
 *
 * Note that `setContent` may be called once per test, so a test that needs two
 * renders has to be two tests.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // Robolectric's own accessor rather than ApplicationProvider, which would be
    // androidx.test:core — a different library, reached only transitively
    // through ui-test-junit4 and carrying no catalog entry to bump.
    private val resources = RuntimeEnvironment.getApplication().resources

    /**
     * The one that matters most, and the one worth mutation-checking.
     *
     * Every value on this screen is a stored value, so a screen that drew the
     * defaults instead would look entirely plausible — three rows, three
     * sensible times — while telling the user something false about their own
     * device. Swapping `state.dayCutoff` for `LocalTime.MIDNIGHT` in
     * `SettingsList` reddens this and nothing else.
     */
    @Test
    fun rows_showTheStoredValuesRatherThanTheDefaults() {
        render(STORED)

        compose.onNodeWithText("03:00").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_day_sunday)).assertIsDisplayed()
        compose.onNodeWithText("22:30").assertIsDisplayed()
    }

    /** The device's clock convention reaches the rows, not just the picker. */
    @Test
    fun rows_followTheTwelveHourClockWhenTheDeviceDoes() {
        render(STORED, is24Hour = false)

        compose.onNodeWithText("3:00 AM").assertIsDisplayed()
        compose.onNodeWithText("10:30 PM").assertIsDisplayed()
    }

    /** Each row says what it changes; the cutoff also says what it does not. */
    @Test
    fun eachRow_explainsWhatItChanges() {
        render(STORED)

        compose.onNodeWithText(string(R.string.settings_day_cutoff_help)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_week_start_help)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_reminder_help)).assertIsDisplayed()
    }

    /**
     * Dismissing a picker writes nothing.
     *
     * The point of holding the half-made choice in the dialog rather than in the
     * ViewModel. Cancel has to mean the stored setting is untouched, and this is
     * the only test that can see the difference.
     */
    @Test
    fun cancellingTheWeekStartPicker_writesNothing() {
        val picked = mutableListOf<DayOfWeek>()
        render(STORED, actions = NO_ACTIONS.copy(onWeekStartChange = { picked += it }))

        compose.onNodeWithText(string(R.string.settings_week_start_label)).performClick()
        compose.onNodeWithText(string(R.string.settings_day_friday)).performClick()
        compose.onNodeWithText(string(R.string.settings_cancel)).performClick()

        assertEquals(emptyList<DayOfWeek>(), picked)
    }

    /**
     * And confirming reports the day that was chosen, not the one already set.
     *
     * The second half worth mutation-checking: handing `selected` back instead
     * of `choice` in `WeekStartDialog` is a picker that looks like it works and
     * silently always writes what was already there.
     */
    @Test
    fun confirmingTheWeekStartPicker_reportsTheChosenDay() {
        val picked = mutableListOf<DayOfWeek>()
        render(STORED, actions = NO_ACTIONS.copy(onWeekStartChange = { picked += it }))

        compose.onNodeWithText(string(R.string.settings_week_start_label)).performClick()
        compose.onNodeWithText(string(R.string.settings_day_friday)).performClick()
        compose.onNodeWithText(string(R.string.settings_confirm)).performClick()

        assertEquals(listOf(DayOfWeek.FRIDAY), picked)
    }

    /**
     * Opening a time picker at all.
     *
     * Thin-looking and it earns its place: nothing here opened the *time* dialog
     * before, only the week-start one, and the gap let a crash reach a device.
     * `TimePickerDisplayMode` is a value class over an `Int`, so holding one in
     * `rememberSaveable` boxes it into something the default
     * `SaveableStateRegistry` cannot put in a Bundle — and that throws when the
     * dialog is *composed*, not when it is restored, so merely opening it was
     * fatal. Rendering the dialog is the whole assertion.
     */
    @Test
    fun openingTheDayCutoffPicker_rendersRatherThanThrowing() {
        render(STORED)

        compose.onNodeWithText(string(R.string.settings_day_cutoff_label)).performClick()

        compose.onNodeWithText(string(R.string.settings_confirm)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_cancel)).assertIsDisplayed()
    }

    /** And the reminder row opens its own, with the state kept apart. */
    @Test
    fun openingTheReminderPicker_rendersRatherThanThrowing() {
        render(STORED)

        compose.onNodeWithText(string(R.string.settings_reminder_label)).performClick()

        compose.onNodeWithText(string(R.string.settings_confirm)).assertIsDisplayed()
    }

    /** Dismissing a time picker writes nothing, the same rule the day list follows. */
    @Test
    fun cancellingTheDayCutoffPicker_writesNothing() {
        val picked = mutableListOf<LocalTime>()
        render(STORED, actions = NO_ACTIONS.copy(onDayCutoffChange = { picked += it }))

        compose.onNodeWithText(string(R.string.settings_day_cutoff_label)).performClick()
        compose.onNodeWithText(string(R.string.settings_cancel)).performClick()

        assertEquals(emptyList<LocalTime>(), picked)
    }

    /** And confirming reports the time the picker opened on when nothing was touched. */
    @Test
    fun confirmingTheDayCutoffPicker_reportsThePickersTime() {
        val picked = mutableListOf<LocalTime>()
        render(STORED, actions = NO_ACTIONS.copy(onDayCutoffChange = { picked += it }))

        compose.onNodeWithText(string(R.string.settings_day_cutoff_label)).performClick()
        compose.onNodeWithText(string(R.string.settings_confirm)).performClick()

        assertEquals(listOf(LocalTime.of(3, 0)), picked)
    }

    /**
     * The one wiring mistake this screen is shaped to make.
     *
     * `SettingsScreen`'s two `TimeDialog` blocks differ in three tokens, so
     * pointing the reminder branch at `onDayCutoffChange` compiles, reads
     * correctly at a glance, and stayed green through the whole suite — while
     * silently moving the day boundary, which is the most expensive of the three
     * settings to get wrong.
     *
     * Asserting 22:30 rather than any time also pins that the dialog opened on
     * `STORED.reminderTime` and not on the cutoff's 03:00, so a swapped
     * `initial` fails here too.
     */
    @Test
    fun confirmingTheReminderPicker_reportsTheReminderAndNotTheCutoff() {
        val reminders = mutableListOf<LocalTime>()
        val cutoffs = mutableListOf<LocalTime>()
        render(
            STORED,
            actions = NO_ACTIONS.copy(
                onReminderTimeChange = { reminders += it },
                onDayCutoffChange = { cutoffs += it },
            ),
        )

        compose.onNodeWithText(string(R.string.settings_reminder_label)).performClick()
        compose.onNodeWithText(string(R.string.settings_confirm)).performClick()

        assertEquals(listOf(LocalTime.of(22, 30)), reminders)
        assertEquals(emptyList<LocalTime>(), cutoffs)
    }

    @Test
    fun unavailable_saysSoRatherThanDrawingEmptyRows() {
        render(SettingsUiState.Unavailable)

        compose.onNodeWithText(string(R.string.settings_unavailable_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_day_cutoff_label)).assertDoesNotExist()
    }

    /** Blank rather than a spinner, and in particular not a row of defaults. */
    @Test
    fun loading_drawsNoSettingsAtAll() {
        render(SettingsUiState.Loading)

        compose.onNodeWithText(string(R.string.settings_day_cutoff_label)).assertDoesNotExist()
    }

    @Test
    fun backButton_isNamedAndReports() {
        var backs = 0
        render(STORED, actions = NO_ACTIONS.copy(onBack = { backs++ }))

        compose.onNodeWithContentDescription(string(R.string.settings_back)).performClick()

        assertEquals(1, backs)
    }

    // --- the Data section ------------------------------------------------

    @Test
    fun dataSection_offersExportAndImportUnderItsOwnHeading() {
        render(STORED)

        compose.onNodeWithText(string(R.string.settings_data_header)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_export_label)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_import_label)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dataRows_explainWhatTheyDoAndWhatTheyDoNot() {
        render(STORED)

        compose.onNodeWithText(string(R.string.settings_export_help)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_import_help)).performScrollTo().assertIsDisplayed()
    }

    /**
     * Mutation-checked, and the direct analogue of the reminder/cutoff test
     * above: two adjacent rows differing in three tokens is the same
     * copy-paste shape that once wired the reminder dialog to the day cutoff.
     * Here the two lambdas have identical types, so nothing but this notices
     * if they are crossed.
     */
    @Test
    fun exportRow_reportsATapAndTheImportRowDoesNot() {
        val exports = mutableListOf<Unit>()
        val imports = mutableListOf<Unit>()
        render(STORED, actions = NO_ACTIONS.copy(onExport = { exports += Unit }, onImport = { imports += Unit }))

        compose.onNodeWithText(string(R.string.settings_export_label)).performScrollTo().performClick()

        assertEquals(1, exports.size)
        assertEquals(0, imports.size)
    }

    @Test
    fun importRow_reportsATapAndTheExportRowDoesNot() {
        val exports = mutableListOf<Unit>()
        val imports = mutableListOf<Unit>()
        render(STORED, actions = NO_ACTIONS.copy(onExport = { exports += Unit }, onImport = { imports += Unit }))

        compose.onNodeWithText(string(R.string.settings_import_label)).performScrollTo().performClick()

        assertEquals(1, imports.size)
        assertEquals(0, exports.size)
    }

    /**
     * Both rows, not just the running one. Exporting midway through an import
     * reads a half-merged log, and leaving the *other* row live is the easy
     * mistake — nothing else here would see it.
     */
    @Test
    fun whileExporting_bothDataRowsAreDisabled() {
        render(STORED.copy(dataTask = DataTask.Exporting))

        compose.onNodeWithText(string(R.string.settings_export_label)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.settings_import_label)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun whileExporting_theRowSaysSoInsteadOfExplainingItself() {
        render(STORED.copy(dataTask = DataTask.Exporting))

        compose.onNodeWithText(string(R.string.settings_export_running)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_export_help)).assertDoesNotExist()
    }

    /**
     * The status is *announced*, not merely swapped in.
     *
     * A row that has gone dead and changed its caption silently reads as broken
     * to a screen reader, which is why `RowActivity` distinguishes Running from
     * Blocked at all — collapsing the two would have been the tidy way to fit
     * detekt's parameter limit and would have lost exactly this.
     */
    @Test
    fun whileExporting_theStatusIsAnnouncedAndNotJustSwapped() {
        render(STORED.copy(dataTask = DataTask.Exporting))

        compose.onNodeWithText(string(R.string.settings_export_running))
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    /** The mirror: a row that is merely waiting its turn must not interrupt. */
    @Test
    fun whileExporting_theBlockedRowIsNotAnnounced() {
        render(STORED.copy(dataTask = DataTask.Exporting))

        compose.onNodeWithText(string(R.string.settings_import_help))
            .performScrollTo()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    /** Kills a `when` that reports the other branch's copy. */
    @Test
    fun whileImporting_itIsTheImportRowThatSaysSo() {
        render(STORED.copy(dataTask = DataTask.Importing))

        compose.onNodeWithText(string(R.string.settings_import_running)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_export_running)).assertDoesNotExist()
    }

    @Test
    fun whileIdle_neitherRowSaysItIsRunning() {
        render(STORED)

        compose.onNodeWithText(string(R.string.settings_export_running)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.settings_import_running)).assertDoesNotExist()
    }

    /**
     * Recorded as a decision rather than left as a fact: the Data section is
     * inside the `Settings` branch, so a non-IO read failure takes the recovery
     * path off the screen with it. `Unavailable` is a bug-shaped state that IO
     * cannot produce, so this is tolerable — see docs/ux/settings.md §7.
     */
    @Test
    fun unavailable_takesTheDataSectionWithIt() {
        render(SettingsUiState.Unavailable)

        compose.onNodeWithText(string(R.string.settings_export_label)).assertDoesNotExist()
    }

    /**
     * Why there is no `<plurals>`, pinned rather than left in a comment.
     *
     * A quantity resource selects on one number and this sentence has two, so
     * the copy has to be grammatical at every count instead. One-and-one is the
     * case that would read wrong if anyone rewrote it as "1 entries".
     */
    @Test
    fun importCounts_readGrammaticallyAtOne() {
        val message = messageFor(ImportResult.Merged(read = 2, added = 1))

        assertEquals(
            "Imported that file: 1 added, 1 already here.",
            resources.getString(message.text, *message.args.toTypedArray()),
        )
    }

    // --- the last-export nudge (PRD §5) ----------------------------------

    /**
     * The value line the export row gained, which is what turns it into a
     * `SettingRow`. Rendered rather than asserted on the mapper, because a
     * quantity resource is the one part of this that only a composition
     * resolves.
     */
    @Test
    fun exportRow_saysHowLongAgoTheLastBackupWas() {
        render(STORED.copy(exportRecency = ExportRecency.DaysAgo(34)))

        compose.onNodeWithText("Last exported 34 days ago").performScrollTo().assertIsDisplayed()
    }

    /** The reason this is a `<plurals>` and not one string with a `%d` in it. */
    @Test
    fun exportRow_readsGrammaticallyAtOneDay() {
        render(STORED.copy(exportRecency = ExportRecency.DaysAgo(1)))

        compose.onNodeWithText("Last exported 1 day ago").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun exportRow_saysToday_ratherThanNoughtDaysAgo() {
        render(STORED.copy(exportRecency = ExportRecency.Today))

        compose.onNodeWithText(string(R.string.settings_export_today)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun exportRow_neverExported_saysSo() {
        render(STORED.copy(exportRecency = ExportRecency.Never))

        compose.onNodeWithText(string(R.string.settings_export_never)).performScrollTo().assertIsDisplayed()
    }

    /**
     * The nudge itself: overdue replaces the explanation rather than joining it.
     *
     * Both halves matter. A screen that showed the nudge *and* the ordinary help
     * would pass an assertion about the nudge alone while reading as two
     * paragraphs of caption under one row.
     */
    @Test
    fun exportRow_overdue_replacesTheHelpLineWithTheNudge() {
        render(STORED.copy(exportRecency = ExportRecency.DaysAgo(30)))

        compose.onNodeWithText(string(R.string.settings_export_overdue_help)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_export_help)).assertDoesNotExist()
    }

    @Test
    fun exportRow_recentBackup_keepsTheOrdinaryHelp() {
        render(STORED.copy(exportRecency = ExportRecency.DaysAgo(29)))

        compose.onNodeWithText(string(R.string.settings_export_help)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_export_overdue_help)).assertDoesNotExist()
    }

    /**
     * A fresh install is not warned about losing nothing, and the row is exactly
     * what it was before any of this existed.
     */
    @Test
    fun nothingYet_drawsNoValueLineAndNoNudge() {
        render(STORED.copy(exportRecency = ExportRecency.NothingYet))

        compose.onNodeWithText(string(R.string.settings_export_help)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_export_never)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.settings_export_overdue_help)).assertDoesNotExist()
    }

    /**
     * docs/ux/settings.md §6, still true after the two row composables became
     * one: the value line belongs to the export row alone. The import row passes
     * null and draws no middle line, so this counts rather than merely finding.
     */
    @Test
    fun theValueLineBelongsToTheExportRowAlone() {
        render(STORED.copy(exportRecency = ExportRecency.Never))

        assertEquals(
            1,
            compose.onAllNodesWithText(string(R.string.settings_export_never)).fetchSemanticsNodes().size,
        )
        compose.onNodeWithText(string(R.string.settings_import_help)).performScrollTo().assertIsDisplayed()
    }

    /** Running wins over overdue: a row writing a file must not say there is no file. */
    @Test
    fun whileExporting_theStatusReplacesTheNudge() {
        render(STORED.copy(dataTask = DataTask.Exporting, exportRecency = ExportRecency.Never))

        compose.onNodeWithText(string(R.string.settings_export_running)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_export_overdue_help)).assertDoesNotExist()
    }

    /**
     * The value line survives the row going dead. It is a stored fact rather
     * than an affordance, so hiding it while an import runs would make the row
     * look like it had never been exported.
     */
    @Test
    fun whileImporting_theExportRowStillSaysWhenItLastRan() {
        render(STORED.copy(dataTask = DataTask.Importing, exportRecency = ExportRecency.DaysAgo(34)))

        compose.onNodeWithText("Last exported 34 days ago").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.settings_export_label)).performScrollTo().assertIsNotEnabled()
    }

    private fun render(state: SettingsUiState, actions: SettingsActions = NO_ACTIONS, is24Hour: Boolean = true) {
        compose.setContent {
            GawiTheme { SettingsScreen(state, actions, SnackbarHostState(), is24Hour) }
        }
    }

    private fun string(id: Int): String = resources.getString(id)

    private companion object {
        /** Deliberately none of the defaults, so a default cannot pass as this. */
        val STORED = SettingsUiState.Settings(
            dayCutoff = LocalTime.of(3, 0),
            weekStart = DayOfWeek.SUNDAY,
            reminderTime = LocalTime.of(22, 30),
        )

        val NO_ACTIONS = SettingsActions(
            onDayCutoffChange = {},
            onWeekStartChange = {},
            onReminderTimeChange = {},
            onExport = {},
            onImport = {},
            onBack = {},
        )
    }
}
