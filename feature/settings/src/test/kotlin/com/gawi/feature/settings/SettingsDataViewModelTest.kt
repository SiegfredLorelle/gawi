package com.gawi.feature.settings

import android.net.Uri
import app.cash.turbine.test
import com.gawi.core.data.backup.ExportStatus
import com.gawi.core.data.backup.ImportResult
import com.gawi.core.data.settings.UserSettings
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

    // by lazy, for the reason SettingsViewModelTest records: JUnit runs field
    // initialisers before it applies rules.
    private val viewModel by lazy { SettingsViewModel(settings, archive) }

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
     * **A broken caption must not take the recovery path off the screen.**
     *
     * The settings flow going to `Unavailable` on failure is right — settings
     * you cannot read cannot be drawn. This flow is one line of text under a
     * button, and it shares the `catch` with them, so without its own guard a
     * failure here hides the export and import rows entirely: the only
     * disaster-recovery path on the device, lost over its own caption
     * (docs/ux/settings.md §7).
     */
    @Test
    fun `a status that cannot be read leaves the rest of the screen alone`() = runTest {
        archive.statusFailure = IOException("the preferences file is unreadable")

        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            settings.emit(UserSettings(weekStart = DayOfWeek.SUNDAY))

            val state = awaitItem()
            assertEquals(SettingsUiState.Settings(LocalTime.MIDNIGHT, DayOfWeek.SUNDAY, LocalTime.of(21, 0)), state)
            assertEquals(ExportRecency.NothingYet, (state as SettingsUiState.Settings).exportRecency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        val DESTINATION: Uri = Uri.parse("content://documents/gawi-export.json")
        val SOURCE: Uri = Uri.parse("content://documents/incoming.json")
    }
}
