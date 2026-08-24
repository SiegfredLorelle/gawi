package com.gawi.feature.insights

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gawi.core.domain.model.Schedule
import com.gawi.core.ui.theme.GawiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * What the app-wide screen draws, and what its two pickers report.
 *
 * The substance is the three decisions a reader could delete without noticing:
 * that the toggle swaps the list rather than the screen, that untagged effort is
 * a row rather than an omission, and that a period with nothing in it says so
 * instead of drawing an empty list.
 */
@RunWith(RobolectricTestRunner::class)
class InsightsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    private fun string(id: Int): String = resources.getString(id)

    /**
     * Suppressed at the declaration, like the other fixture builders here: every
     * parameter is defaulted, so a test names only the field it is about.
     */
    @Suppress("LongParameterList")
    private fun overview(
        period: Period = Period.MONTH,
        breakdown: Breakdown = Breakdown.HABITS,
        activeDays: Int = 3,
        completions: Int = 7,
        habits: List<HabitRateUi> = listOf(
            HabitRateUi("read", ScheduleLabelUi(R.string.insights_schedule_daily, null), percent = 83),
            HabitRateUi("run", ScheduleLabelUi(R.string.insights_schedule_weekly, 3), percent = null),
        ),
        tags: List<TagShareUi> = listOf(
            TagShareUi("career", 86, 1f),
            TagShareUi(null, 12, 0.14f),
        ),
        hasAnyHabit: Boolean = true,
    ) = InsightsUiState.Overview(period, breakdown, activeDays, completions, habits, tags, hasAnyHabit)

    private fun render(state: InsightsUiState, actions: InsightsActions = NO_ACTIONS) {
        compose.setContent {
            GawiTheme { InsightsScreen(state, actions) }
        }
    }

    @Test
    fun `the headline counts days and completions, in plural`() {
        render(overview(activeDays = 3, completions = 7))

        compose.onNodeWithText(resources.getQuantityString(R.plurals.insights_active_days, 3, 3)).assertIsDisplayed()
        compose.onNodeWithText(resources.getQuantityString(R.plurals.insights_completions, 7, 7)).assertIsDisplayed()
    }

    /** One is one, which is the whole reason these are plurals and not formats. */
    @Test
    fun `a single active day reads as one day`() {
        render(overview(activeDays = 1, completions = 1))

        compose.onNodeWithText(resources.getQuantityString(R.plurals.insights_active_days, 1, 1)).assertIsDisplayed()
    }

    @Test
    fun `the selected period is selected, and picking another reports it`() {
        var picked: Period? = null
        render(overview(period = Period.QUARTER), NO_ACTIONS.copy(onPeriod = { picked = it }))

        compose.onNodeWithText(string(R.string.insights_period_quarter)).assertIsSelected()
        compose.onNodeWithText(string(R.string.insights_period_year)).performClick()

        assertEquals(Period.YEAR, picked)
    }

    @Test
    fun `the habits breakdown draws rates and what they are rates of`() {
        render(overview(breakdown = Breakdown.HABITS))

        compose.onNodeWithText("read").assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.insights_rate_percent, 83)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.insights_schedule_daily)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.insights_schedule_weekly, 3)).assertIsDisplayed()
        // A habit with no rate draws a dash, never a zero.
        compose.onNodeWithText(string(R.string.insights_rate_none)).assertIsDisplayed()
        // And no tag totals are on screen while habits are.
        compose.onAllNodesWithText("86").assertCountEquals(0)
    }

    @Test
    fun `the tags breakdown draws totals, with untagged as a row`() {
        render(overview(breakdown = Breakdown.TAGS))

        compose.onNodeWithText("career").assertIsDisplayed()
        compose.onNodeWithText("86").assertIsDisplayed()
        // Untagged is a visible row, not a silent omission (insights.md §5).
        compose.onNodeWithText(string(R.string.insights_untagged)).assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
        // No percentages anywhere: the rows carry totals, deliberately.
        compose.onAllNodesWithText(resources.getString(R.string.insights_rate_percent, 83)).assertCountEquals(0)
    }

    @Test
    fun `the toggle reports, and is selected for the list on screen`() {
        var picked: Breakdown? = null
        render(overview(breakdown = Breakdown.TAGS), NO_ACTIONS.copy(onBreakdown = { picked = it }))

        compose.onNodeWithText(string(R.string.insights_breakdown_tags)).assertIsSelected()
        compose.onNodeWithText(string(R.string.insights_breakdown_habits)).performClick()

        assertEquals(Breakdown.HABITS, picked)
    }

    /**
     * An empty period says why it is blank and keeps its pickers.
     *
     * The picker is the way out of an empty month, so a notice that replaced the
     * whole screen would strand the user on the one period with nothing in it.
     */
    @Test
    fun `an empty period explains itself and leaves the pickers reachable`() {
        render(
            overview(
                breakdown = Breakdown.TAGS,
                activeDays = 0,
                completions = 0,
                habits = emptyList(),
                tags = emptyList(),
            ),
        )

        compose.onNodeWithText(string(R.string.insights_empty_title)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(string(R.string.insights_period_year)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.insights_breakdown_tags)).assertIsDisplayed()
    }

    /**
     * No habit to report on is **not** "nothing logged", and saying so would
     * contradict the headline two rows above it.
     *
     * Reachable by archiving every habit after logging against them: the counts
     * include an archived habit's completions (deliberately — effort spent is
     * history) while the adherence list excludes it. One shared notice made the
     * screen say "12 active days" and "no completions in this period" at the
     * same time. Caught in review, and this is the case that catches it again.
     */
    @Test
    fun `no unarchived habit says so rather than claiming nothing was logged`() {
        render(
            overview(
                breakdown = Breakdown.HABITS,
                activeDays = 12,
                completions = 34,
                habits = emptyList(),
                // Habits exist; they are simply all archived. Without this the
                // fresh-install branch takes it, which is the bug below.
                hasAnyHabit = true,
            ),
        )

        compose.onNodeWithText(string(R.string.insights_no_habits_title)).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.insights_empty_title)).assertCountEquals(0)
        // The headline it must not contradict is still on screen.
        compose.onNodeWithText(resources.getQuantityString(R.plurals.insights_active_days, 12, 12)).assertIsDisplayed()
    }

    /**
     * A fresh install is told to make a habit, not that its habits are archived.
     *
     * The commonest way to reach this screen empty: Today's app bar offers
     * Insights before a first habit exists, so `habits` is empty with nothing
     * archived. The copy that used to show here said "Every habit is archived",
     * which was a claim about the user's data that was untrue. Caught in review,
     * and untested until now — the archived case above fixes `activeDays = 12`,
     * so it never exercised this path.
     */
    @Test
    fun `a fresh install is told to add a habit, not that they are archived`() {
        render(overview(breakdown = Breakdown.HABITS, activeDays = 0, completions = 0, habits = emptyList(), hasAnyHabit = false))

        compose.onNodeWithText(string(R.string.insights_no_habits_yet_title)).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.insights_no_habits_title)).assertCountEquals(0)
        compose.onAllNodesWithText(string(R.string.insights_empty_title)).assertCountEquals(0)
    }

    /** And with tags to show, the same state draws the bars rather than a notice. */
    @Test
    fun `the tags breakdown is unaffected by there being no unarchived habit`() {
        render(overview(breakdown = Breakdown.TAGS, habits = emptyList()))

        compose.onNodeWithText("career").assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.insights_no_habits_title)).assertCountEquals(0)
    }

    @Test
    fun `back reports`() {
        var back = 0
        render(overview(), NO_ACTIONS.copy(onBack = { back++ }))

        compose.onNodeWithContentDescription(string(R.string.insights_back)).performClick()

        assertEquals(1, back)
    }

    /**
     * And explains *this* screen, not the history one.
     *
     * The assertion moved from `insights_unavailable_title` to this screen's own
     * id, which is the point: asserting whichever id the screen happens to
     * render is exactly why this test could not see that it was rendering the
     * history screen's copy. The history string is asserted absent so the two
     * cannot quietly converge again.
     */
    @Test
    fun `an unavailable read explains this screen, not a history`() {
        render(InsightsUiState.Unavailable)

        compose.onNodeWithText(string(R.string.insights_read_failed_title)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.insights_unavailable_title)).assertCountEquals(0)
    }

    @Test
    fun `loading draws no rows`() {
        render(InsightsUiState.Loading)

        compose.onNodeWithText(string(R.string.insights_title)).assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.insights_period_month)).assertCountEquals(0)
    }

    private companion object {
        val NO_ACTIONS = InsightsActions(onPeriod = {}, onBreakdown = {}, onBack = {})
    }
}
