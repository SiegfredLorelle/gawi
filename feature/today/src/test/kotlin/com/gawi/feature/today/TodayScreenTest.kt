package com.gawi.feature.today

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.streak.StreakUi
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

    @get:Rule(order = 1)
    val compose = createComposeRule()

    // Robolectric's own accessor rather than ApplicationProvider, which would be
    // androidx.test:core — a different library, reached only transitively
    // through ui-test-junit4 and carrying no catalog entry to bump.
    private val resources = RuntimeEnvironment.getApplication().resources

    /** See the rule: without it, nothing that composes Today ever goes idle. */
    @get:Rule(order = 0)
    val animationsOff = AnimationsOffRule()

    /**
     * today-view §4's rule 0, as a test: a habitless first run is not thriving.
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

        // The second row is below the tank on Robolectric's 470dp screen and a
        // LazyColumn has not composed it yet; scroll the list to it first.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(WALK.name))
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

        // Below the tank on Robolectric's 470dp screen, as it would be on a small
        // phone: scroll to it first, the way a thumb would.
        compose.onNodeWithText(string(R.string.today_add_habit)).performScrollTo().performClick()

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
     * The third way off, and the reason all three are told apart by name.
     *
     * Every app bar action is a glyph, so nothing but the content description
     * distinguishes them to a test or to a screen reader. This asserts that
     * tapping the one named "Insights" reports insights and neither of the
     * others — the mistake three buttons one glyph apart are placed to make.
     */
    @Test
    fun insightsButton_isNamedAndLeadsToInsights() {
        var insights = 0
        var settings = 0
        var managed = 0
        compose.setContent {
            GawiTheme {
                TodayScreen(
                    state = HABITS,
                    actions = NO_ACTIONS.copy(
                        onOpenInsights = { insights++ },
                        onOpenSettings = { settings++ },
                        onManageHabits = { managed++ },
                    ),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }

        compose.onNodeWithContentDescription(string(R.string.today_insights)).performClick()

        assertEquals(1, insights)
        assertEquals(0, settings)
        assertEquals(0, managed)
    }

    /**
     * The other way off, and the reason the three are told apart by name.
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

    /** Scroll the list far enough that item 0 — the tank — is off the top. */
    private fun scrollPastTheTank() {
        compose.onNode(hasScrollAction()).performScrollToIndex(LONG.rows.size)
        compose.waitForIdle()
    }

    /**
     * today-view §1's chip: absent while Momo is on screen, present once he is
     * not, and it takes the title's place rather than crowding in beside it.
     *
     * Both halves in one test on purpose. A chip that is always there and a chip
     * that is never there are indistinguishable from either assertion alone.
     */
    @Test
    fun chip_replacesTheTitleOnceTheTankHasScrolledAway() {
        compose.setContent {
            GawiTheme { TodayScreen(LONG, NO_ACTIONS, SnackbarHostState()) }
        }

        compose.onNodeWithText(string(R.string.today_title)).assertIsDisplayed()
        compose.onNodeWithTag(CHIP_TAG).assertDoesNotExist()

        scrollPastTheTank()

        compose.onNodeWithTag(CHIP_TAG).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_title)).assertDoesNotExist()
    }

    /**
     * What the chip carries: §1 says the mood and the remaining count, and both
     * have to survive being merged into one node.
     *
     * The count is the chip's own short wording, not the panel's sentence — the
     * bar has no room for that one. Asserting the panel's string here would pass
     * on a chip that had quietly grown too wide to fit.
     */
    @Test
    fun chip_carriesTheMoodAndTheRemainingCount() {
        compose.setContent {
            GawiTheme { TodayScreen(LONG, NO_ACTIONS, SnackbarHostState()) }
        }
        scrollPastTheTank()

        compose.onNodeWithText(resources.getString(R.string.today_chip_remaining, LONG.remaining)).assertIsDisplayed()
        compose.onNodeWithContentDescription(string(R.string.today_mood_worried)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.today_remaining, LONG.remaining, LONG.rows.size))
            .assertDoesNotExist()
    }

    /**
     * The chip is not a second live region.
     *
     * MascotPanel's copy already is one and is only scrolled off rather than
     * gone, so a live region here would have TalkBack read the mood twice after
     * every tick. Structural rather than spoken: there is no TalkBack on this
     * image, so this asserts the property that would cause it. The by-hand check
     * is owed in docs/running.md §4.
     */
    @Test
    fun chip_isNotASecondLiveRegion() {
        compose.setContent {
            GawiTheme { TodayScreen(LONG, NO_ACTIONS, SnackbarHostState()) }
        }
        scrollPastTheTank()

        compose.onNodeWithTag(CHIP_TAG).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    /**
     * Mood.REGENERATING's promise, drawn: the line names the habit.
     *
     * Through the screen rather than the mapper because the mapper can only say
     * which name reached the state. Whether the panel puts it in the sentence —
     * or quietly keeps using the unnamed line, which would compile and pass
     * every mapper assertion — is what only a render can see. The absence is
     * asserted alongside the presence for that reason.
     */
    @Test
    fun regenerating_namesTheHabitInTheLine() {
        compose.setContent {
            GawiTheme { TodayScreen(REGENERATING, NO_ACTIONS, SnackbarHostState()) }
        }

        compose.onNodeWithText(resources.getString(R.string.today_mood_regenerating_named, WALK.name)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_mood_regenerating)).assertDoesNotExist()
    }

    /**
     * All four faces, each with its own line — the today-view §4 mapping is no longer
     * three-to-four. The tag proves the mood reached the drawing and the copy
     * proves the line beside it agrees; a screen test cannot see pixels
     * (MomoRenderTest does), so this is the seam it can hold.
     */
    @Test
    fun `each mood draws its own face with its own line`() {
        val lines = mapOf(
            Mood.THRIVING to R.string.today_mood_happy,
            Mood.CONTENT to R.string.today_mood_neutral,
            Mood.WORRIED to R.string.today_mood_worried,
            Mood.REGENERATING to R.string.today_mood_regenerating,
        )
        val mood = mutableStateOf(Mood.THRIVING)
        compose.setContent {
            GawiTheme { TodayScreen(HABITS.copy(mood = mood.value), NO_ACTIONS, SnackbarHostState()) }
        }
        lines.forEach { (next, line) ->
            mood.value = next
            compose.waitForIdle()
            // Unmerged, because the panel merges its descendants so TalkBack
            // reads the line once; the tag lives on the drawing inside it.
            // Displayed, not merely present: a Canvas that measures 0 x 0 still
            // exists in the tree, and one did — the first build shipped an
            // empty tank with this assertion green. assertIsDisplayed needs
            // non-empty bounds, which is the property that was missing.
            compose.onNodeWithTag("momo:$next", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText(string(line)).assertIsDisplayed()
        }
    }

    /**
     * The tank life is drawn behind Momo, for every mood, with real bounds —
     * the same "displayed, not merely present" bar the face is held to, since a
     * 0 x 0 Canvas is exactly how the first tank shipped empty.
     */
    @Test
    fun `the habitat is drawn behind momo in every mood`() {
        val mood = mutableStateOf(Mood.CONTENT)
        compose.setContent {
            GawiTheme { TodayScreen(HABITS.copy(mood = mood.value), NO_ACTIONS, SnackbarHostState()) }
        }
        Mood.entries.forEach { next ->
            mood.value = next
            compose.waitForIdle()
            compose.onNodeWithTag("habitat", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    /**
     * Finishing the day with animations off is not celebrated — a celebration
     * is motion, and the resting thriving frame already says thriving (momo.md
     * §6). The on path is a frame loop no Robolectric composition can wait out,
     * so it is the emulator's to check (running.md §4); the trigger itself is
     * `CelebrationFrameTest`'s.
     */
    @Test
    fun `with animations off finishing the day draws no celebration`() {
        val mood = mutableStateOf(Mood.CONTENT)
        compose.setContent {
            GawiTheme { TodayScreen(HABITS.copy(mood = mood.value), NO_ACTIONS, SnackbarHostState()) }
        }
        compose.onNodeWithTag("celebration", useUnmergedTree = true).assertDoesNotExist()
        mood.value = Mood.THRIVING
        compose.waitForIdle()
        compose.onNodeWithTag("momo:${Mood.THRIVING}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("celebration", useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * With animations off a mood change is a cut, not a fade: one frame after
     * the change the drawing is tagged with the new mood and nothing carries the
     * old one. Since the transition became one Canvas interpolating between two
     * frames (momo.md §3) the tag names the destination from the first frame in
     * either case, so what this holds is that the old name never lingers; how
     * far the body has travelled is `MomoFrameTest`'s and `MomoRenderTest`'s to
     * measure. The clock is held so the frame can be observed rather than raced.
     */
    @Test
    fun `with animations off a mood change cuts instead of fading`() {
        val mood = mutableStateOf(Mood.CONTENT)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GawiTheme { TodayScreen(HABITS.copy(mood = mood.value), NO_ACTIONS, SnackbarHostState()) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("momo:${Mood.CONTENT}", useUnmergedTree = true).assertIsDisplayed()

        mood.value = Mood.WORRIED
        // A handful of frames — 80 ms, a fraction of the 550 ms transition.
        // With the clock held, advancing it does not recompose; waitForIdle
        // does, without moving time, so the two alternate.
        repeat(5) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }

        compose.onNodeWithTag("momo:${Mood.CONTENT}", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("momo:${Mood.WORRIED}", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * A streak crossing a rung swaps the copy line for the milestone line and puts
     * the row's badge on its pill, then two seconds later both return — with
     * animations off, where the line and the pill are all there is (momo.md §5:
     * the line changing is the announcement). The clock is held so the swap can
     * be observed at both ends rather than raced; `advanceTimeBy` drives the
     * delay the milestone holds its line on, because the remembered scope runs
     * on the test's clock.
     */
    @Test
    fun `a streak reaching seven swaps the line and highlights the badge then returns`() {
        val rows = mutableStateOf(listOf(READ.copy(streak = StreakUi.Days(6)), WALK))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GawiTheme { TodayScreen(HABITS.copy(rows = rows.value), NO_ACTIONS, SnackbarHostState()) }
        }
        settle()
        compose.onNodeWithText(string(R.string.today_mood_neutral)).assertIsDisplayed()
        compose.onNodeWithTag("milestone-badge", useUnmergedTree = true).assertDoesNotExist()

        rows.value = listOf(READ.copy(streak = StreakUi.Days(7)), WALK)
        settle()

        compose.onNodeWithText(quantity(R.plurals.today_milestone_days, 7)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.today_mood_neutral)).assertDoesNotExist()
        compose.onNodeWithTag("milestone-badge", useUnmergedTree = true).assertIsDisplayed()
        // No motion with animations off: the milestone canvas is never composed.
        compose.onNodeWithTag("milestone", useUnmergedTree = true).assertDoesNotExist()

        compose.mainClock.advanceTimeBy(MilestoneFrame.MILLIS + 100L)
        settle()

        compose.onNodeWithText(string(R.string.today_mood_neutral)).assertIsDisplayed()
        compose.onNodeWithText(quantity(R.plurals.today_milestone_days, 7)).assertDoesNotExist()
        compose.onNodeWithTag("milestone-badge", useUnmergedTree = true).assertDoesNotExist()
    }

    /** A screen opened on day seven shows a seven and no party — the first sighting never fires. */
    @Test
    fun `opening the screen on a milestone does not celebrate it`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GawiTheme {
                TodayScreen(HABITS.copy(rows = listOf(READ.copy(streak = StreakUi.Days(7)), WALK)), NO_ACTIONS, SnackbarHostState())
            }
        }
        settle()

        compose.onNodeWithText(string(R.string.today_mood_neutral)).assertIsDisplayed()
        compose.onNodeWithText(quantity(R.plurals.today_milestone_days, 7)).assertDoesNotExist()
        compose.onNodeWithTag("milestone-badge", useUnmergedTree = true).assertDoesNotExist()
    }

    /** Unticking the habit mid-run cuts the run: the line and the badge snap back at once. */
    @Test
    fun `unticking during a milestone settles the line`() {
        val rows = mutableStateOf(listOf(READ.copy(streak = StreakUi.Days(6)), WALK))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GawiTheme { TodayScreen(HABITS.copy(rows = rows.value), NO_ACTIONS, SnackbarHostState()) }
        }
        settle()
        rows.value = listOf(READ.copy(streak = StreakUi.Days(7)), WALK)
        settle()
        compose.onNodeWithText(quantity(R.plurals.today_milestone_days, 7)).assertIsDisplayed()

        rows.value = listOf(READ.copy(streak = StreakUi.Days(6)), WALK)
        settle()

        compose.onNodeWithText(string(R.string.today_mood_neutral)).assertIsDisplayed()
        compose.onNodeWithText(quantity(R.plurals.today_milestone_days, 7)).assertDoesNotExist()
        compose.onNodeWithTag("milestone-badge", useUnmergedTree = true).assertDoesNotExist()
    }

    /** A few frames with the clock held: advancing does not recompose and waitForIdle does not move time, so the two alternate. */
    private fun settle() {
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private fun quantity(id: Int, n: Int): String = resources.getQuantityString(id, n, n)

    private companion object {
        val LOGICAL_DATE: LocalDate = LocalDate.parse("2026-08-17")
        val NO_ACTIONS = TodayActions(
            onToggle = { _, _, _ -> },
            onAddHabit = {},
            onManageHabits = {},
            onOpenInsights = {},
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
            regeneratingHabit = null,
        )

        /**
         * A live break inside the window, with the habit the line should name.
         * WALK rather than READ so a test asserting the name cannot pass on the
         * first row by accident.
         */
        val REGENERATING = TodayUiState.Habits(
            rows = listOf(READ, WALK),
            mood = Mood.REGENERATING,
            remaining = 2,
            logicalDate = LOGICAL_DATE,
            regeneratingHabit = WALK.name,
        )

        /**
         * Enough rows that the list really scrolls on Robolectric's 470 dp
         * screen. The chip appears only once item 0 — the 250 dp tank — is off
         * the top, and a list that fits has nothing to scroll past: the tests
         * below would then assert the chip's absence twice and call it a pass.
         */
        val LONG = TodayUiState.Habits(
            rows = List(8) { n ->
                WALK.copy(id = HabitId("00000000-0000-7000-8000-%012d".format(n + 10)), name = "habit $n")
            },
            mood = Mood.WORRIED,
            remaining = 3,
            logicalDate = LOGICAL_DATE,
            regeneratingHabit = null,
        )

        /** Habits present and none outstanding — READ is the completed one. */
        val ALL_DONE = TodayUiState.Habits(
            rows = listOf(READ),
            mood = Mood.THRIVING,
            remaining = 0,
            logicalDate = LOGICAL_DATE,
            regeneratingHabit = null,
        )
    }
}
