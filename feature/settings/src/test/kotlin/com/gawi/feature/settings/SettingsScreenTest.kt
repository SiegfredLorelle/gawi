package com.gawi.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
            onBack = {},
        )
    }
}
