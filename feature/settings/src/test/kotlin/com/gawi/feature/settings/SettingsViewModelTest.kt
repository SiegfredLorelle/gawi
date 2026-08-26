package com.gawi.feature.settings

import app.cash.turbine.test
import com.gawi.core.data.settings.ThemeMode
import com.gawi.core.data.settings.UserSettings
import com.gawi.feature.settings.testsupport.FakeCompletionCsvArchive
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
    private val viewModel by lazy { SettingsViewModel(settings, FakeEventArchive(), FakeCompletionCsvArchive()) }

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
                SettingsUiState.Settings(LocalTime.of(3, 0), DayOfWeek.SUNDAY, LocalTime.of(22, 30), ThemeMode.SYSTEM),
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

    /**
     * The one settings combination that is refused, from the reminder's side.
     *
     * `reminderOn` resolves a reminder equal to the day cutoff to the logical
     * day's **start**, so the pair is meaningless rather than merely odd — and once
     * the reminder shipped it meant a "N of N left today" at the top of every day
     * that also consumed that day's one reminder. reminderOn's KDoc always said a
     * settings screen was where to prevent it; nothing did. Found by /code-review.
     */
    @Test
    fun `a reminder time equal to the day cutoff is refused and reported`() = runTest {
        viewModel.events.test {
            viewModel.onReminderTimeChange(LocalTime.MIDNIGHT)

            assertEquals(SettingsMessage(R.string.settings_error_reminder_equals_cutoff), awaitItem())
        }

        assertEquals(LocalTime.of(21, 0), settings.stored.reminderTime)
    }

    /** And from the cutoff's side, because either row can create the collision. */
    @Test
    fun `a day cutoff equal to the reminder time is refused and reported`() = runTest {
        viewModel.events.test {
            viewModel.onDayCutoffChange(LocalTime.of(21, 0))

            assertEquals(SettingsMessage(R.string.settings_error_reminder_equals_cutoff), awaitItem())
        }

        assertEquals(LocalTime.MIDNIGHT, settings.stored.dayCutoff)
    }

    /**
     * The control: a value that merely *looks* close is written normally.
     *
     * Without this, a validation that refused every reminder-time edit would pass
     * both tests above.
     */
    @Test
    fun `a reminder time one minute off the cutoff is written`() = runTest {
        viewModel.onReminderTimeChange(LocalTime.of(0, 1))

        assertEquals(LocalTime.of(0, 1), settings.stored.reminderTime)
    }

    @Test
    fun `each of the four settings is written, and only that one changes`() = runTest {
        viewModel.onDayCutoffChange(LocalTime.of(4, 15))
        viewModel.onWeekStartChange(DayOfWeek.SATURDAY)
        viewModel.onReminderTimeChange(LocalTime.of(20, 45))
        viewModel.onThemeChange(ThemeMode.DARK)

        assertEquals(
            listOf(
                UserSettings(dayCutoff = LocalTime.of(4, 15)),
                UserSettings(dayCutoff = LocalTime.of(4, 15), weekStart = DayOfWeek.SATURDAY),
                UserSettings(
                    dayCutoff = LocalTime.of(4, 15),
                    weekStart = DayOfWeek.SATURDAY,
                    reminderTime = LocalTime.of(20, 45),
                ),
                UserSettings(
                    dayCutoff = LocalTime.of(4, 15),
                    weekStart = DayOfWeek.SATURDAY,
                    reminderTime = LocalTime.of(20, 45),
                    theme = ThemeMode.DARK,
                ),
            ),
            settings.writes,
        )
    }

    /**
     * Every mode is legal, including the one already stored.
     *
     * The theme is the second unrefusable write on this screen, and the only
     * one whose value cannot collide with another setting — so the test that
     * matters is that nothing narrows it.
     */
    @Test
    fun `every theme mode is written as chosen`() = runTest {
        ThemeMode.entries.forEach { mode -> viewModel.onThemeChange(mode) }

        assertEquals(ThemeMode.entries, settings.writes.map { it.theme })
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
