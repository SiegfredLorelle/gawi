package com.gawi.feature.settings

import android.net.Uri
import app.cash.turbine.test
import com.gawi.core.data.backup.ExportStatus
import com.gawi.core.data.backup.ImportResult
import com.gawi.core.data.settings.UserSettings
import com.gawi.feature.settings.testsupport.FakeCompletionCsvArchive
import com.gawi.feature.settings.testsupport.FakeEventArchive
import com.gawi.feature.settings.testsupport.FakeSettingsSource
import com.gawi.feature.settings.testsupport.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Exporting and importing, from the ViewModel down.
 *
 * **Under Robolectric, and that is not decoration.** `configureAndroid` sets
 * `testOptions.unitTests.isReturnDefaultValues = true` for every module, so in
 * a plain JVM test `Uri.parse` returns **null** instead of throwing "Stub!".
 * A pure version of this file would hand null to the ViewModel, the fake would
 * record null, and `assertEquals(uri, archive.exported.single())` would compare
 * null to null and pass having verified nothing. Measured, not assumed.
 *
 * Separate from [SettingsViewModelTest] rather than merged into it: that file
 * is pure and says so, and there is no reason for the settings-write tests to
 * start paying for a Robolectric runtime.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsDataViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeSettingsSource()
    private val archive = FakeEventArchive()
    private val csv = FakeCompletionCsvArchive()

    // by lazy, for the reason SettingsViewModelTest records: JUnit runs field
    // initialisers before it applies rules.
    private val viewModel by lazy { SettingsViewModel(settings, archive, csv) }

    @Test
    fun `an export writes to the uri the picker returned`() = runTest {
        viewModel.onExportTo(DESTINATION)

        assertEquals(listOf(DESTINATION), archive.exported)
    }

    /**
     * Export speaks on success where a settings write is silent. There is no
     * row redrawing to be the feedback, and the file went somewhere this app is
     * never told about.
     */
    @Test
    fun `an export that succeeds says so`() = runTest {
        viewModel.events.test {
            viewModel.onExportTo(DESTINATION)

            assertEquals(SettingsMessage(R.string.settings_export_done), awaitItem())
        }
    }

    /**
     * The guard that matters. An uncaught throw out of `viewModelScope` is
     * process death rather than a snackbar — the bug a PR review found in three
     * places in the last module, and a document provider revoking a grant is a
     * far likelier throw than anything a settings write can do.
     */
    @Test
    fun `an export that throws is reported rather than fatal`() = runTest {
        archive.failure = IOException("the provider went away")

        viewModel.events.test {
            viewModel.onExportTo(DESTINATION)

            assertEquals(SettingsMessage(R.string.settings_error_export), awaitItem())
        }
    }

    @Test
    fun `an import reports both counts`() = runTest {
        archive.result = ImportResult.Merged(read = 140, added = 128)

        viewModel.events.test {
            viewModel.onImportFrom(SOURCE)

            assertEquals(SettingsMessage(R.string.settings_import_done, listOf(128, 12)), awaitItem())
        }
    }

    @Test
    fun `a refused file is a message rather than a crash`() = runTest {
        archive.result = ImportResult.Refused.FromANewerVersion(formatVersion = 2)

        viewModel.events.test {
            viewModel.onImportFrom(SOURCE)

            assertEquals(SettingsMessage(R.string.settings_error_import_newer), awaitItem())
        }
    }

    @Test
    fun `an import that throws is reported rather than fatal`() = runTest {
        archive.failure = IOException("the disk is full")

        viewModel.events.test {
            viewModel.onImportFrom(SOURCE)

            assertEquals(SettingsMessage(R.string.settings_error_import), awaitItem())
        }
    }

    /**
     * Mutation-checked: never clearing the task leaves both rows dead for the
     * life of the screen, and no other test here would see it.
     */
    @Test
    fun `the state says it is exporting, and stops saying so`() = runTest {
        val gate = CompletableDeferred<Unit>()
        archive.gate = gate

        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            settings.emit(UserSettings())
            assertEquals(DataTask.Idle, (awaitItem() as SettingsUiState.Settings).dataTask)

            viewModel.onExportTo(DESTINATION)
            assertEquals(DataTask.Exporting, (awaitItem() as SettingsUiState.Settings).dataTask)

            gate.complete(Unit)
            assertEquals(DataTask.Idle, (awaitItem() as SettingsUiState.Settings).dataTask)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The `finally`, which is the line most easily forgotten. */
    @Test
    fun `a failed import leaves the screen usable`() = runTest {
        archive.failure = IOException("nope")

        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            settings.emit(UserSettings())
            assertEquals(DataTask.Idle, (awaitItem() as SettingsUiState.Settings).dataTask)

            viewModel.onImportFrom(SOURCE)

            assertEquals(DataTask.Importing, (awaitItem() as SettingsUiState.Settings).dataTask)
            assertEquals(DataTask.Idle, (awaitItem() as SettingsUiState.Settings).dataTask)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A double tap can land before the disabled state has been through
     * `combine` and recomposed, so the rows alone are not the guard.
     */
    @Test
    fun `a second export while one is running is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        archive.gate = gate

        viewModel.onExportTo(DESTINATION)
        viewModel.onExportTo(DESTINATION)
        gate.complete(Unit)

        assertEquals(listOf(DESTINATION), archive.exported)
    }

    // --- the CSV of completions (PRD §5) ---------------------------------

    @Test
    fun `a csv export writes to the uri the picker returned`() = runTest {
        viewModel.onExportCompletionsTo(CSV_DESTINATION)

        assertEquals(listOf(CSV_DESTINATION), csv.exported)
        assertEquals(emptyList<Uri>(), archive.exported)
    }

    @Test
    fun `a csv export that succeeds says what the file is`() = runTest {
        csv.rows = 327

        viewModel.events.test {
            viewModel.onExportCompletionsTo(CSV_DESTINATION)

            assertEquals(SettingsMessage(R.string.settings_export_csv_done), awaitItem())
        }
    }

    /** Nought rows is the one thing the copy cannot say for itself. */
    @Test
    fun `an empty csv export says the file holds only its headings`() = runTest {
        csv.rows = 0

        viewModel.events.test {
            viewModel.onExportCompletionsTo(CSV_DESTINATION)

            assertEquals(SettingsMessage(R.string.settings_export_csv_empty), awaitItem())
        }
    }

    /**
     * The failure message has to be the CSV's own and not the backup's. The
     * backup's copy tells the user to distrust a file they may later need;
     * saying that about a spreadsheet would be alarming and wrong.
     */
    @Test
    fun `a csv export that throws reports its own failure`() = runTest {
        csv.failure = IOException("the provider went away")

        viewModel.events.test {
            viewModel.onExportCompletionsTo(CSV_DESTINATION)

            assertEquals(SettingsMessage(R.string.settings_error_export_csv), awaitItem())
        }
    }

    @Test
    fun `the state says it is writing a csv, and stops saying so`() = runTest {
        val gate = CompletableDeferred<Unit>()
        csv.gate = gate

        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            settings.emit(UserSettings())
            assertEquals(DataTask.Idle, (awaitItem() as SettingsUiState.Settings).dataTask)

            viewModel.onExportCompletionsTo(CSV_DESTINATION)
            assertEquals(DataTask.ExportingCsv, (awaitItem() as SettingsUiState.Settings).dataTask)

            gate.complete(Unit)
            assertEquals(DataTask.Idle, (awaitItem() as SettingsUiState.Settings).dataTask)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an export and an import are refused while a csv is being written`() = runTest {
        val gate = CompletableDeferred<Unit>()
        csv.gate = gate

        viewModel.onExportCompletionsTo(CSV_DESTINATION)
        viewModel.onExportTo(DESTINATION)
        viewModel.onImportFrom(SOURCE)
        gate.complete(Unit)

        assertEquals(emptyList<Uri>(), archive.exported)
        assertEquals(emptyList<Uri>(), archive.imported)
    }

    /**
     * **The negative this whole row depends on.**
     *
     * A CSV holds no events, so it cannot restore anything, so it must not
     * settle the 30-day nudge. `:core:data` enforces that structurally — the CSV
     * archive is not given the journal at all, pinned by
     * `CompletionCsvArchiveWiringTest` — and this asserts the same claim from
     * the other end, at the level a user would notice: a log with something in
     * it and no backup still says so after a CSV has been written.
     */
    @Test
    fun `a csv export does not settle the export nudge`() = runTest {
        archive.exportStatus.value = ExportStatus(daysSinceExport = null, hasEvents = true)

        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            settings.emit(UserSettings())
            assertEquals(ExportRecency.Never, (awaitItem() as SettingsUiState.Settings).exportRecency)

            viewModel.onExportCompletionsTo(CSV_DESTINATION)

            // Two emissions as the task moves out and back. The recency must be
            // untouched in both, which is exactly what a stamp would change.
            assertEquals(ExportRecency.Never, (awaitItem() as SettingsUiState.Settings).exportRecency)
            assertEquals(ExportRecency.Never, (awaitItem() as SettingsUiState.Settings).exportRecency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- the last-export status ------------------------------------------

    @Test
    fun `the state carries what the archive says about the last export`() = runTest {
        archive.exportStatus.value = ExportStatus(daysSinceExport = 34, hasEvents = true)

        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            settings.emit(UserSettings())

            assertEquals(ExportRecency.DaysAgo(34), (awaitItem() as SettingsUiState.Settings).exportRecency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A new stamp reaches a screen that is already open.
     *
     * This is the point of the status being a flow rather than a read: a
     * successful export has to change the row that offered it, and the user is
     * still looking at that row when it lands.
     */
    @Test
    fun `an export landing changes the row that offered it`() = runTest {
        archive.exportStatus.value = ExportStatus(daysSinceExport = null, hasEvents = true)

        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            settings.emit(UserSettings())
            assertEquals(ExportRecency.Never, (awaitItem() as SettingsUiState.Settings).exportRecency)

            archive.exportStatus.value = ExportStatus(daysSinceExport = 0, hasEvents = true)

            assertEquals(ExportRecency.Today, (awaitItem() as SettingsUiState.Settings).exportRecency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * **A broken caption must not take the recovery path off the screen, and must
     * not go quiet either.**
     *
     * Two assertions, and the second is the one a reviewer asked for. The settings
     * flow going to `Unavailable` on failure is right — settings you cannot read
     * cannot be drawn — while this flow is one line of text under a button and
     * shares the `catch` with them, so without its own guard a failure here hides
     * the export and import rows entirely: the only disaster-recovery path on the
     * device, lost over its own caption (docs/ux/settings.md §7).
     *
     * Asserting `Never` rather than `NothingYet` is what distinguishes "the
     * caption failed safely" from "the caption failed quietly". The old
     * expectation passed against a fallback that silenced the nudge.
     */
    @Test
    fun `a status that cannot be read nudges rather than going quiet`() = runTest {
        archive.statusFailure = IOException("the preferences file is unreadable")

        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            settings.emit(UserSettings(weekStart = DayOfWeek.SUNDAY))

            val state = awaitItem()
            assertEquals(
                SettingsUiState.Settings(LocalTime.MIDNIGHT, DayOfWeek.SUNDAY, LocalTime.of(21, 0), exportRecency = ExportRecency.Never),
                state,
            )
            assertEquals(ExportRecency.Never, (state as SettingsUiState.Settings).exportRecency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        val DESTINATION: Uri = Uri.parse("content://documents/gawi-export.json")
        val SOURCE: Uri = Uri.parse("content://documents/incoming.json")

        // Distinct from DESTINATION, so a test cannot pass by confusing the two
        // archives with each other.
        val CSV_DESTINATION: Uri = Uri.parse("content://documents/gawi-completions.csv")
    }
}
