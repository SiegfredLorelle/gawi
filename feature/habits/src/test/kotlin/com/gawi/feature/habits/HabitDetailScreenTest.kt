package com.gawi.feature.habits

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.streak.StreakUi
import com.gawi.core.ui.theme.GawiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * What habit detail draws, and what its one action reports.
 *
 * Aimed at the stateless screen rather than the Route, so no Hilt graph and no
 * ViewModel are involved in a red result. docs/ux/today-view.md §5's streak
 * rules are the substance here: they are display decisions a reader could
 * delete by accident, and this is the second surface that has to honour them.
 */
@RunWith(RobolectricTestRunner::class)
class HabitDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // Robolectric's own accessor rather than ApplicationProvider, which would be
    // androidx.test:core — a different library, reached only transitively
    // through ui-test-junit4 and carrying no catalog entry to bump.
    private val resources = RuntimeEnvironment.getApplication().resources

    private fun render(state: HabitDetailUiState, actions: HabitDetailActions = NO_ACTIONS) {
        compose.setContent {
            GawiTheme { HabitDetailScreen(state, actions, SnackbarHostState()) }
        }
    }

    private fun string(id: Int): String = resources.getString(id)

    @Test
    fun aDailyStreak_isACount() {
        render(detail(streak = StreakUi.Days(12)))

        compose.onNodeWithText("12").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_detail_streak_days_caption)).assertIsDisplayed()
    }

    /**
     * §5: "A daily habit's streak is a count; a weekly habit's is in weeks. The
     * two must never be styled as the same number."
     *
     * The bare count is asserted absent, not just the `w` present — a weekly
     * streak that also rendered "3" somewhere would be the exact confusion §5
     * forbids.
     */
    @Test
    fun aWeeklyStreak_isCountedInWeeksAndNeverAsABareNumber() {
        render(detail(streak = StreakUi.Weeks(3)))

        compose.onNodeWithText("3w").assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_detail_streak_weeks_caption)).assertIsDisplayed()
        compose.onNodeWithText("3").assertDoesNotExist()
    }

    /**
     * §5: a broken streak "keeps its old value as context (`was 4`) next to the
     * `0`, with a cut-thread glyph". All three parts, because the zero on its
     * own reads as a habit that never started.
     */
    @Test
    fun aBrokenStreak_keepsWhatWasLostBesideTheZero() {
        render(detail(streak = StreakUi.Broken(previous = 4, weekly = false)))

        compose.onNodeWithText(string(R.string.habits_detail_streak_broken)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.habits_detail_streak_was_days, 4)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_detail_streak_broken_glyph)).assertIsDisplayed()
    }

    /**
     * §5 again, from the other side: never reading zero is a rule about a *live*
     * streak. A habit with no completions has nothing to draw, and a `0` here
     * would claim a break that never happened.
     */
    @Test
    fun aHabitWithNoCompletions_saysSoRatherThanDrawingZero() {
        render(detail(streak = StreakUi.None))

        compose.onNodeWithText(string(R.string.habits_detail_streak_none)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_detail_streak_broken)).assertDoesNotExist()
    }

    /**
     * Only a weekly habit draws "2/3 this week" — the Today row's rule, kept in
     * step, since a detail screen that disagreed with the row that led to it
     * would be its own bug.
     *
     * Two tests rather than one with two renders: the compose rule's activity
     * takes `setContent` once, and a second call throws rather than redrawing.
     */
    @Test
    fun weekProgress_isDrawnForAWeeklyHabit() {
        render(detail(weekProgress = HabitWeekProgress(done = 2, target = 3)))

        compose.onNodeWithText(resources.getString(R.string.habits_detail_week_progress, 2, 3)).assertIsDisplayed()
    }

    @Test
    fun weekProgress_isAbsentForADailyHabit() {
        render(detail(weekProgress = null))

        compose.onNodeWithText(resources.getString(R.string.habits_detail_week_progress, 2, 3)).assertDoesNotExist()
    }

    /**
     * Edit reports the habit the screen is showing.
     *
     * It closes over the state rather than over anything the Route captured, so
     * a screen that had re-read a different habit cannot send the old id to the
     * editor and quietly edit the wrong one.
     */
    @Test
    fun editAction_reportsTheHabitOnScreen() {
        var edited: HabitId? = null
        render(detail(id = OTHER), NO_ACTIONS.copy(onEdit = { edited = it }))

        compose.onNodeWithContentDescription(string(R.string.habits_detail_edit)).performClick()

        assertEquals(OTHER, edited)
    }

    /**
     * And is absent when there is nothing to edit.
     *
     * On `Unavailable` there is no id to hand back, so the action would either
     * navigate nowhere or need an id it does not have.
     */
    @Test
    fun editAction_isAbsentWhenThereIsNoHabit() {
        var edited: HabitId? = null
        render(HabitDetailUiState.Unavailable, NO_ACTIONS.copy(onEdit = { edited = it }))

        compose.onNodeWithContentDescription(string(R.string.habits_detail_edit)).assertDoesNotExist()
        assertNull(edited)
    }

    @Test
    fun anArchivedHabit_saysSo() {
        render(detail(archived = true))

        compose.onNodeWithText(string(R.string.habits_detail_archived)).assertIsDisplayed()
    }

    private companion object {
        val HABIT = HabitId("00000000-0000-7000-8000-000000000001")
        val OTHER = HabitId("00000000-0000-7000-8000-000000000002")

        val NO_ACTIONS = HabitDetailActions(onEdit = {}, onBack = {})

        /**
         * Suppressed here for the reason the fixture builders elsewhere are: every
         * parameter is defaulted, so a test names only the field it is about.
         */
        @Suppress("LongParameterList")
        fun detail(
            id: HabitId = HABIT,
            name: String = "read",
            schedule: ScheduleUi = ScheduleUi.Daily,
            tag: String? = null,
            archived: Boolean = false,
            completedToday: Boolean = false,
            weekProgress: HabitWeekProgress? = null,
            streak: StreakUi = StreakUi.None,
        ) = HabitDetailUiState.Detail(
            id = id,
            name = name,
            icon = "📖",
            iconTint = null,
            schedule = schedule,
            tag = tag,
            archived = archived,
            completedToday = completedToday,
            weekProgress = weekProgress,
            streak = streak,
        )
    }
}
