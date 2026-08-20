package com.gawi.feature.today

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.theme.GawiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * The screen, rendered.
 *
 * On the JVM under Robolectric rather than on a device, so this runs inside the
 * existing `make test` gate and architecture §8's "CI runs unit tests only"
 * stays true. What it is here to catch is the class of bug the other tests in
 * this module structurally cannot: [TodayUiMapperTest] asserts what the state
 * says and [TodayViewModelTest] asserts which state is emitted, and neither one
 * can see what a composable does with it.
 *
 * That gap has already cost something. The empty state once drew "Nothing left
 * today" above "No habits yet" — a first run congratulated for having done
 * nothing, which is precisely what docs/ux/today-view.md §4's rule 0 exists to
 * prevent. It was captured in a screenshot during device verification and read
 * past; a reviewer found it afterwards. [emptyState_doesNotClaimNothingLeft] is
 * that bug, kept failing.
 *
 * Deliberately aimed at [TodayScreen] rather than [TodayRoute]: the stateless
 * composable is the whole of what is under test, so no Hilt graph, no
 * ViewModel and no substituted clock are involved in a red result. Driving the
 * wired route belongs with the cross-module journey tests, which need a Hilt
 * test graph and a substituted clock and so are their own piece of work.
 *
 * Strings are resolved from the test context rather than written out here, so
 * these assertions survive a copy edit and fail only on a behaviour change.
 */
@RunWith(RobolectricTestRunner::class)
class TodayScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // Robolectric's own accessor rather than ApplicationProvider, which would be
    // androidx.test:core — a different library, reached only transitively
    // through ui-test-junit4 and carrying no catalog entry to bump.
    private val resources = RuntimeEnvironment.getApplication().resources

    /**
     * §4's rule 0, as a test: a habitless first run is not thriving.
     *
     * The mood line is asserted alongside the absence, because "Nothing left
     * today" was never wrong on its own — it was wrong *under* Momo waiting for
     * a first habit. Asserting only the absence would still pass if the panel
     * stopped rendering entirely.
     */
    @Test
    fun emptyState_doesNotClaimNothingLeft() {
        compose.setContent {
            GawiTheme { TodayScreen(TodayUiState.Empty(Mood.CONTENT), NO_ACTIONS, SnackbarHostState()) }
        }

        compose.onNodeWithText(string(R.string.today_mood_empty)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_remaining_none)).assertDoesNotExist()
    }

    /**
     * A tap carries the row's own date and its own state, not the screen's idea
     * of either.
     *
     * This is 4b's load-bearing decision seen from the layer that consumes it:
     * `observeToday()` emits one snapshot so the rows and the date they were
     * queried for cannot disagree, and `HabitList` passes that date down with
     * each row. A tap that resolved "today" for itself would differ from the
     * day the user was looking at across a cutoff — and would still pass every
     * test in this module except this one.
     *
     * The second row is the one tapped, and it is the un-completed one, so a
     * `completed` argument that was read off the wrong row would come back
     * `true`.
     */
    @Test
    fun rowTap_reportsTheRowsOwnDateAndState() {
        var reported: Triple<HabitId, Boolean, LocalDate>? = null
        compose.setContent {
            GawiTheme {
                TodayScreen(
                    state = HABITS,
                    actions = NO_ACTIONS.copy(
                        onToggle = { id, completed, date -> reported = Triple(id, completed, date) },
                    ),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithText(WALK.name).performClick()

        assertEquals(Triple(WALK.id, false, LOGICAL_DATE), reported)
    }

    /** A completed row reports the state it is in, so the ViewModel can undo it. */
    @Test
    fun completedRowTap_reportsThatItIsAlreadyDone() {
        var reported: Triple<HabitId, Boolean, LocalDate>? = null
        compose.setContent {
            GawiTheme {
                TodayScreen(
                    state = HABITS,
                    actions = NO_ACTIONS.copy(
                        onToggle = { id, completed, date -> reported = Triple(id, completed, date) },
                    ),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithText(READ.name).performClick()

        assertEquals(Triple(READ.id, true, LOGICAL_DATE), reported)
    }

    /**
     * The failed read tells the user what to do about it.
     *
     * The copy matters more than it looks: `catch` terminates the flow, so this
     * state clears only when the screen re-subscribes. It says to reopen the app
     * because nothing else will clear it, and that stays honest only while the
     * two are changed together.
     */
    @Test
    fun unavailable_tellsTheUserToReopen() {
        compose.setContent {
            GawiTheme { TodayScreen(TodayUiState.Unavailable, NO_ACTIONS, SnackbarHostState()) }
        }

        compose.onNodeWithText(string(R.string.today_unavailable_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_unavailable_body)).assertIsDisplayed()
    }

    /**
     * The one state where "Nothing left today" is the right thing to say.
     *
     * The sibling of [emptyState_doesNotClaimNothingLeft], and the reason that
     * one is not enough on its own: the panel's count line is guarded on
     * `total > 0`, so asserting only its absence pins half the rule. Everything
     * ticked with habits present must say it, and must not slide into the empty
     * state's copy — which is what keying the empty line off the mood instead of
     * the count would produce, since `THRIVING` and a habitless run are
     * different things that would start reading the same.
     */
    @Test
    fun allDone_saysNothingLeftWithoutSoundingEmpty() {
        compose.setContent {
            GawiTheme { TodayScreen(ALL_DONE, NO_ACTIONS, SnackbarHostState()) }
        }

        compose.onNodeWithText(string(R.string.today_remaining_none)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_mood_empty)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.today_empty_title)).assertDoesNotExist()
    }

    /**
     * Loading draws nothing rather than guessing.
     *
     * Not a spinner and not the empty state: the first emission is one Room
     * query, so this frame exists only so the screen does not tell someone they
     * have no habits before it has looked.
     */
    @Test
    fun loading_claimsNothingEitherWay() {
        compose.setContent {
            GawiTheme { TodayScreen(TodayUiState.Loading, NO_ACTIONS, SnackbarHostState()) }
        }

        compose.onNodeWithText(string(R.string.today_empty_title)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.today_mood_empty)).assertDoesNotExist()
    }

    /**
     * The empty state's button, which is the shortest path from a fresh install
     * to a first habit.
     *
     * `today_empty_body` has said "Add a habit and it starts here" since 4b with
     * nothing to tap. Until the habits module existed there was nowhere for it to
     * go, so this is the assertion that the sentence is now true.
     */
    @Test
    fun emptyState_offersTheAddItPromises() {
        var added = 0
        compose.setContent {
            GawiTheme {
                TodayScreen(
                    state = TodayUiState.Empty(Mood.CONTENT),
                    actions = NO_ACTIONS.copy(onAddHabit = { added++ }),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithText(string(R.string.today_add_habit)).performClick()

        assertEquals(1, added)
    }

    /**
     * One of the two ways off this screen.
     *
     * Deliberately not offered as a second add button: adding a habit is rare
     * and completing one is daily, so Today keeps one affordance for each and
     * the rows keep the room PRD §6.1 wants for a single tap.
     */
    @Test
    fun manageButton_isNamedAndLeadsToTheHabitList() {
        var managed = 0
        compose.setContent {
            GawiTheme {
                TodayScreen(
                    state = HABITS,
                    actions = NO_ACTIONS.copy(onManageHabits = { managed++ }),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithContentDescription(string(R.string.today_manage_habits)).performClick()

        assertEquals(1, managed)
    }

    /**
     * The other way off, and the reason the two are told apart by name.
     *
     * Both app bar actions are glyphs, so nothing but the content description
     * distinguishes them to a test or to a screen reader. This asserts that
     * tapping the one named "Settings" reports settings and not the habit list
     * — which is the mistake the two buttons are one glyph apart from making.
     */
    @Test
    fun settingsButton_isNamedAndLeadsToSettings() {
        var opened = 0
        var managed = 0
        compose.setContent {
            GawiTheme {
                TodayScreen(
                    state = HABITS,
                    actions = NO_ACTIONS.copy(onOpenSettings = { opened++ }, onManageHabits = { managed++ }),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithContentDescription(string(R.string.today_settings)).performClick()

        assertEquals(1, opened)
        assertEquals(0, managed)
    }

    /** And the add button is not on the populated screen, only the empty one. */
    @Test
    fun populatedScreen_doesNotOfferTheEmptyStatesAddButton() {
        compose.setContent {
            GawiTheme { TodayScreen(HABITS, NO_ACTIONS, SnackbarHostState()) }
        }

        compose.onNodeWithText(string(R.string.today_add_habit)).assertDoesNotExist()
    }

    private fun string(id: Int): String = resources.getString(id)

    private companion object {
        val LOGICAL_DATE: LocalDate = LocalDate.parse("2026-08-17")
        val NO_ACTIONS = TodayActions(
            onToggle = { _, _, _ -> },
            onAddHabit = {},
            onManageHabits = {},
            onOpenSettings = {},
        )

        /** Completed, so a tap on it must report `true`. */
        val READ = HabitRowUi(
            id = HabitId("00000000-0000-7000-8000-000000000001"),
            name = "read",
            icon = "R",
            iconTint = null,
            completed = true,
            weekProgress = null,
            streak = StreakUi.Days(count = 3),
        )

        /** Outstanding, and the row the tap test clicks. */
        val WALK = HabitRowUi(
            id = HabitId("00000000-0000-7000-8000-000000000002"),
            name = "walk",
            icon = "W",
            iconTint = null,
            completed = false,
            weekProgress = null,
            streak = StreakUi.None,
        )

        val HABITS = TodayUiState.Habits(
            rows = listOf(READ, WALK),
            mood = Mood.CONTENT,
            remaining = 1,
            logicalDate = LOGICAL_DATE,
        )

        /** Habits present and none outstanding — READ is the completed one. */
        val ALL_DONE = TodayUiState.Habits(
            rows = listOf(READ),
            mood = Mood.THRIVING,
            remaining = 0,
            logicalDate = LOGICAL_DATE,
        )
    }
}
