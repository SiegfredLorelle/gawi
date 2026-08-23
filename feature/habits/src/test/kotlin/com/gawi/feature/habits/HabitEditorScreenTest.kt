package com.gawi.feature.habits

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.gawi.core.ui.theme.GawiSpacing
import com.gawi.core.ui.theme.GawiTheme
import com.gawi.core.ui.theme.HabitPalette
import com.gawi.feature.habits.testsupport.habitState
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

    /** A real orphan: the purple this palette offered before the retune. */
    private val orphanColour = "#7E57C2"

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

    /**
     * Like [render], but the form it draws follows the edits it reports.
     *
     * [render] holds one immutable state, which is right for almost everything
     * here — what is asserted is how a given state draws. It cannot see a defect
     * that only appears *after* an edit, and the picker had one: a swatch list
     * whose length changed on tap. Feeding `onEdit` back is what makes the second
     * composition real rather than a second `setContent`, which the rule rejects.
     */
    private fun renderFollowingEdits(initial: HabitEditorUiState.Form) {
        compose.setContent {
            var form by remember { mutableStateOf(initial) }
            GawiTheme {
                HabitEditorScreen(
                    state = form,
                    actions = HabitEditorActions(
                        onEdit = {
                            edits += it
                            form = it
                        },
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

    /**
     * Tapping the already-selected Weekly chip keeps the target it has.
     *
     * Writing the default unconditionally meant re-tapping the highlighted chip
     * knocked a Weekly(6) habit back to 3, silently, and saving persisted it.
     */
    @Test
    fun tappingWeeklyWhenAlreadyWeekly_keepsTheTarget() {
        render(newHabitForm().copy(schedule = ScheduleUi.Weekly(6)))

        compose.onNodeWithText(string(R.string.habits_schedule_weekly_option)).performScrollTo().performClick()

        assertEquals(ScheduleUi.Weekly(6), edits.last().schedule)
    }

    /** And switching from daily picks a sensible default rather than nothing. */
    @Test
    fun tappingWeeklyFromDaily_startsAtTheDefault() {
        render(newHabitForm())

        compose.onNodeWithText(string(R.string.habits_schedule_weekly_option)).performScrollTo().performClick()

        assertEquals(ScheduleUi.Weekly(3), edits.last().schedule)
    }

    /**
     * Every colour swatch announces a name, not its hex.
     *
     * Read out character by character otherwise — "number sign E F 5 3 5 0".
     */
    @Test
    fun colourSwatches_areNamedForAssistiveTechnology() {
        render(newHabitForm())

        COLOR_LABELS.forEach { label ->
            compose.onNodeWithContentDescription(string(label)).assertExists()
        }
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

        compose.onNodeWithContentDescription(string(COLOR_LABELS.last())).performScrollTo().performClick()

        assertEquals(HabitPalette.Colors.last(), edits.last().color)
    }

    /**
     * A habit whose colour the palette no longer offers still opens on it.
     *
     * The retune in docs/ux/visual-identity.md §6 migrated nothing — a colour is
     * raw hex in an append-only log — so every habit created before it holds a
     * hex `HabitPalette.Colors` has dropped. Selection is exact string equality,
     * so without the leading swatch this form would open with nothing selected,
     * and the obvious repair would be to tap a swatch and silently change a
     * colour the user never touched. `#7E57C2` is a real orphan: the purple this
     * palette used to offer.
     *
     * `HabitsUiMapperTest` cannot catch this. It pins that COLOR_LABELS and the
     * palette are the same length, and this swatch is in neither list.
     */
    @Test
    fun aColourThePaletteDropped_isOfferedAsTheCurrentOne() {
        render(habitState(color = orphanColour).toForm())

        compose.onNodeWithContentDescription(string(R.string.habits_color_current))
            .performScrollTo()
            .assertExists()
            .assertIsSelected()
    }

    /** And it is not offered when there is nothing orphaned to offer. */
    @Test
    fun aColourThePaletteStillOffers_addsNoExtraSwatch() {
        render(habitState(color = HabitPalette.Colors.last()).toForm())

        compose.onNodeWithContentDescription(string(R.string.habits_color_current)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(COLOR_LABELS.last())).performScrollTo().assertIsSelected()
    }

    /**
     * The dropped colour stays offered after picking something else.
     *
     * The defect this pins is a layout one and it was a real bug: derived from
     * the live `form.color`, the extra swatch vanished the moment any hue was
     * tapped, so the row went from nine entries to eight, re-chunked from 5+4 to
     * 5+3, and every swatch slid one place along — under the finger that had
     * just tapped. A second tap in the same spot then picked a different colour,
     * and the habit's own colour was no longer reachable without abandoning the
     * form. Rendering the *result* of that edit is what catches it, which is why
     * this asserts on a second render rather than on the first.
     */
    @Test
    fun pickingAnotherColour_keepsOfferingTheDroppedOne() {
        renderFollowingEdits(habitState(color = orphanColour).toForm())

        compose.onNodeWithContentDescription(string(COLOR_LABELS.last())).performScrollTo().performClick()

        assertEquals(HabitPalette.Colors.last(), edits.last().color)
        compose.onNodeWithContentDescription(string(R.string.habits_color_current))
            .performScrollTo()
            .assertExists()
        compose.onNodeWithContentDescription(string(COLOR_LABELS.last())).performScrollTo().assertIsSelected()
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

    /**
     * A swatch is big enough to hit.
     *
     * WCAG 2.5.5 and Android's own minimum. Both pickers reach it by naming
     * [GawiSpacing.TouchTarget], because a `Box` made selectable by a modifier
     * gets no minimum size the way a Material component does — that is the whole
     * reason the constant exists, and nothing held them to it until now.
     *
     * **One named swatch rather than every `isSelectable()` node**, which was the
     * first attempt and failed on layout instead of behaviour. Measuring them all
     * reported ten controls at 0×0: the form is a `verticalScroll`, so anything
     * below the fold at the test window's height is in the semantics tree but
     * never placed. The same trap `choosingWeekly_revealsTheTargetStepper` above
     * documents. A sweep over every control would need a scroll per node and
     * would still be asserting on the viewport rather than on the code.
     *
     * Both dimensions, because a swatch is square by construction. The icon
     * picker is not asserted separately: it applies the same constant on the same
     * `.size()` line, so this holds the shared code path.
     *
     * **Asserted against a literal 48dp and deliberately not against
     * [GawiSpacing.TouchTarget].** Using the constant was the first version and a
     * mutation check exposed it: the swatch is sized *from* that constant, so
     * both sides of the comparison move together and the assertion can never
     * fail. Lowering the constant to 24dp left it green. 48 is WCAG 2.5.5 and
     * Android's minimum — an external fact, not this project's variable — so it
     * belongs on the right-hand side as a number.
     */
    @Test
    fun aColourSwatchMeetsTheTouchTargetFloor() {
        render(newHabitForm())

        compose.onNodeWithContentDescription(string(COLOR_LABELS.first()))
            .performScrollTo()
            .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
            .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }
}

/** WCAG 2.5.5, and Android's own floor. A literal on purpose — see the test above. */
private val MIN_TOUCH_TARGET = 48.dp
