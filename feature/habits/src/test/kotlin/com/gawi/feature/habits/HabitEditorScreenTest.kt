package com.gawi.feature.habits

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.gawi.core.ui.theme.GawiTheme
import com.gawi.core.ui.theme.HabitPalette
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The habit editor, rendered.
 *
 * Aimed at [HabitEditorScreen], the stateless composable, for the reason
 * [HabitListScreenTest] gives. What it catches that the other tests cannot: the
 * stepper's bounds, whether Save is actually disabled rather than merely
 * reported as such, and whether the title says which of the two things this
 * screen is currently being.
 */
@RunWith(RobolectricTestRunner::class)
class HabitEditorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    private fun string(id: Int): String = resources.getString(id)

    private val edits = mutableListOf<HabitEditorUiState.Form>()
    private var saves = 0
    private var cancels = 0

    private fun render(state: HabitEditorUiState) {
        compose.setContent {
            GawiTheme {
                HabitEditorScreen(
                    state = state,
                    actions = HabitEditorActions(
                        onEdit = { edits += it },
                        onSave = { saves++ },
                        onCancel = { cancels++ },
                    ),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }

    @Test
    fun creating_isTitledAsANewHabit() {
        render(newHabitForm())

        compose.onNodeWithText(string(R.string.habits_new_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_edit_title)).assertDoesNotExist()
    }

    @Test
    fun editing_isTitledAsAnEdit() {
        render(newHabitForm().copy(editing = true, name = "read"))

        compose.onNodeWithText(string(R.string.habits_edit_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_new_title)).assertDoesNotExist()
    }

    /**
     * Save is disabled, not merely styled as though it were.
     *
     * `canSave` is asserted in the mapper test and enforced in the ViewModel;
     * this is the third place the same rule has to hold, and the only one that
     * can catch a button wired to `enabled = true`.
     */
    @Test
    fun blankName_disablesSaveAndSaysWhy() {
        render(newHabitForm())

        compose.onNodeWithText(string(R.string.habits_save)).assertIsNotEnabled()
        compose.onNodeWithText(string(R.string.habits_name_error)).assertIsDisplayed()
        assertEquals(0, saves)
    }

    @Test
    fun namedHabit_enablesSaveAndDropsTheError() {
        render(newHabitForm().copy(name = "read"))

        compose.onNodeWithText(string(R.string.habits_save)).assertIsEnabled()
        compose.onNodeWithText(string(R.string.habits_name_error)).assertDoesNotExist()

        compose.onNodeWithText(string(R.string.habits_save)).performClick()
        assertEquals(1, saves)
    }

    @Test
    fun typingAName_reportsTheEditedForm() {
        render(newHabitForm())

        compose.onNodeWithText(string(R.string.habits_name_label)).performTextReplacement("swim")

        assertEquals("swim", edits.last().name)
    }

    @Test
    fun cancelling_reportsACancelRatherThanASave() {
        render(newHabitForm().copy(name = "read"))

        compose.onNodeWithContentDescription(string(R.string.habits_cancel)).performClick()

        assertEquals(1, cancels)
        assertEquals(0, saves)
    }

    @Test
    fun aDailyHabit_showsNoWeeklyTarget() {
        render(newHabitForm())

        compose.onNodeWithContentDescription(string(R.string.habits_target_more)).assertDoesNotExist()
    }

    @Test
    fun choosingWeekly_revealsTheTargetStepper() {
        render(newHabitForm().copy(schedule = ScheduleUi.Weekly(3)))

        // Scrolled to first: the form is a verticalScroll and the schedule
        // section sits below the fold at the test window's height, so asserting
        // display without this would fail on layout rather than on behaviour.
        compose.onNodeWithText(resources.getString(R.string.habits_weekly_target, 3))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.habits_target_more)).assertIsEnabled()
        compose.onNodeWithContentDescription(string(R.string.habits_target_fewer)).assertIsEnabled()
    }

    /**
     * The upper bound, which is the one that matters.
     *
     * `Schedule.Weekly` validates with `require`, so a stepper that let the
     * target reach eight would not be rejected on save — it would throw out of
     * the constructor. The cap is seven because completions are idempotent per
     * logical date, so an eighth can never be earned.
     */
    @Test
    fun weeklyTarget_cannotBeSteppedAboveSeven() {
        render(newHabitForm().copy(schedule = ScheduleUi.Weekly(7)))

        compose.onNodeWithContentDescription(string(R.string.habits_target_more)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(string(R.string.habits_target_fewer)).assertIsEnabled()
    }

    /** And the lower one: a habit due zero times a week is not a habit. */
    @Test
    fun weeklyTarget_cannotBeSteppedBelowOne() {
        render(newHabitForm().copy(schedule = ScheduleUi.Weekly(1)))

        compose.onNodeWithContentDescription(string(R.string.habits_target_fewer)).assertIsNotEnabled()
        compose.onNodeWithContentDescription(string(R.string.habits_target_more)).assertIsEnabled()
    }

    @Test
    fun steppingTheTarget_reportsTheNewNumber() {
        render(newHabitForm().copy(schedule = ScheduleUi.Weekly(3)))

        compose.onNodeWithContentDescription(string(R.string.habits_target_more))
            .performScrollTo()
            .performClick()

        assertEquals(ScheduleUi.Weekly(4), edits.last().schedule)
    }

    @Test
    fun pickingAColour_reportsThePaletteEntry() {
        render(newHabitForm())
        val other = HabitPalette.Colors.last()

        compose.onNodeWithContentDescription(other).performScrollTo().performClick()

        assertEquals(other, edits.last().color)
    }

    /**
     * A failed load is not a fresh create.
     *
     * Titling it "New habit" is what reading `form?.editing` gives you, since
     * there is no form to ask — a screen claiming to be a new habit while
     * showing an error. Loading and Unavailable only happen when an id was
     * supplied, so both are an edit.
     */
    @Test
    fun unavailable_isNotTitledAsANewHabit() {
        render(HabitEditorUiState.Unavailable)

        compose.onNodeWithText(string(R.string.habits_new_title)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.habits_edit_title)).assertIsDisplayed()
    }

    @Test
    fun loading_isNotTitledAsANewHabitEither() {
        render(HabitEditorUiState.Loading)

        compose.onNodeWithText(string(R.string.habits_new_title)).assertDoesNotExist()
    }

    @Test
    fun unavailable_saysSoRatherThanShowingABlankForm() {
        render(HabitEditorUiState.Unavailable)

        compose.onNodeWithText(string(R.string.habits_editor_unavailable_title)).assertIsDisplayed()
        // No form to submit, so no save to offer.
        compose.onNodeWithText(string(R.string.habits_name_label)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.habits_save)).assertIsNotEnabled()
    }

    @Test
    fun loading_showsNeitherAFormNorAnError() {
        render(HabitEditorUiState.Loading)

        compose.onNodeWithText(string(R.string.habits_name_label)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.habits_editor_unavailable_title)).assertDoesNotExist()
    }
}
