package com.gawi.feature.settings

import app.cash.turbine.test
import com.gawi.core.data.settings.UserSettings
import com.gawi.feature.settings.testsupport.FakeEventArchive
import com.gawi.feature.settings.testsupport.FakeSettingsSource
import com.gawi.feature.settings.testsupport.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Which state is emitted, and what a tap writes.
 *
 * The counterpart to [SettingsUiMapperTest], which asserts what the state says.
 * The load-bearing one here is [aWriteThatThrowsIsReportedRatherThanFatal]: an
 * uncaught throw out of `viewModelScope` is process death rather than a
 * snackbar, and that is the bug a PR review found in three places in the last
 * module.
 */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val settings = FakeSettingsSource()

    // by lazy, not a field initialiser. JUnit runs field initialisers before it
    // applies rules, so an eager ViewModel would bind viewModelScope to the real
    // main dispatcher before MainDispatcherRule installs the test one.
    private val viewModel by lazy { SettingsViewModel(settings, FakeEventArchive()) }

    @Test
    fun `it starts loading, before the store has answered`() = runTest {
        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the stored settings reach the state, not the defaults`() = runTest {
        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            settings.emit(
                UserSettings(
                    dayCutoff = LocalTime.of(3, 0),
                    weekStart = DayOfWeek.SUNDAY,
                    reminderTime = LocalTime.of(22, 30),
                ),
            )

            assertEquals(
                SettingsUiState.Settings(LocalTime.of(3, 0), DayOfWeek.SUNDAY, LocalTime.of(22, 30)),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A read that fails is a screen that says so, not a screen that crashes.
     *
     * Worth being precise about what this covers. An unreadable preferences
     * file does not arrive here: `observe()` absorbs `IOException` into the
     * defaults on purpose, because a wrong cutoff is worse than a plain one.
     * What this pins is the branch below that — a failure that is not IO still
     * reaching a state the screen can draw.
     *
     * `Loading` is deliberately not asserted first. A `StateFlow` conflates, and
     * this upstream throws the moment it is collected, so the initial value is
     * already superseded by the time the first `awaitItem` runs. Asserting it
     * here would be asserting the test dispatcher's scheduling rather than the
     * ViewModel — `it starts loading` covers that, against a store that stays
     * quiet.
     */
    @Test
    fun `a read that fails leaves the screen unavailable rather than dead`() = runTest {
        settings.readFailure = IllegalStateException("the store is broken")

        viewModel.uiState.test {
            assertEquals(SettingsUiState.Unavailable, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each of the three settings is written, and only that one changes`() = runTest {
        viewModel.onDayCutoffChange(LocalTime.of(4, 15))
        viewModel.onWeekStartChange(DayOfWeek.SATURDAY)
        viewModel.onReminderTimeChange(LocalTime.of(20, 45))

        assertEquals(
            listOf(
                UserSettings(dayCutoff = LocalTime.of(4, 15)),
                UserSettings(dayCutoff = LocalTime.of(4, 15), weekStart = DayOfWeek.SATURDAY),
                UserSettings(
                    dayCutoff = LocalTime.of(4, 15),
                    weekStart = DayOfWeek.SATURDAY,
                    reminderTime = LocalTime.of(20, 45),
                ),
            ),
            settings.writes,
        )
    }

    /**
     * The whole reason `commandOrNull` exists in this module.
     *
     * `DataStore.edit` reads before it writes, so an unwritable preferences file
     * throws out of `update`. Uncaught, that leaves `viewModelScope` — a
     * `SupervisorJob` with no `CoroutineExceptionHandler` — and reaches the
     * thread's default handler, which is the process dying on a tap.
     */
    @Test
    fun aWriteThatThrowsIsReportedRatherThanFatal() = runTest {
        settings.writeFailure = IOException("the preferences file is unwritable")

        viewModel.events.test {
            viewModel.onDayCutoffChange(LocalTime.of(5, 0))

            assertEquals(SettingsMessage(R.string.settings_error_unexpected), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** And a write that lands says nothing: the row redrawing is the feedback. */
    @Test
    fun `a write that succeeds is silent`() = runTest {
        viewModel.events.test {
            viewModel.onWeekStartChange(DayOfWeek.SUNDAY)

            expectNoEvents()
        }
    }

    /** A failed write is not the end of the screen — the next one still goes. */
    @Test
    fun `a failed write can be retried`() = runTest {
        settings.writeFailure = IOException("transient")

        viewModel.events.test {
            viewModel.onDayCutoffChange(LocalTime.of(5, 0))
            assertEquals(SettingsMessage(R.string.settings_error_unexpected), awaitItem())

            settings.writeFailure = null
            viewModel.onDayCutoffChange(LocalTime.of(6, 0))

            expectNoEvents()
        }

        assertEquals(listOf(UserSettings(dayCutoff = LocalTime.of(6, 0))), settings.writes)
    }
}
