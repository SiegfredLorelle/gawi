package com.gawi.feature.insights

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gawi.core.ui.date.weekdayLetter
import com.gawi.core.ui.date.weekdayName
import com.gawi.core.ui.theme.GawiTheme
import com.gawi.feature.insights.testsupport.THIS_MONTH
import com.gawi.feature.insights.testsupport.habitDetail
import com.gawi.feature.insights.testsupport.habitState
import com.gawi.feature.insights.testsupport.thisMonth
import com.gawi.feature.insights.testsupport.todayHabit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * What the history grid draws, and what its three actions report.
 *
 * Aimed at the stateless screen rather than the Route, so no Hilt graph and no
 * ViewModel are involved in a red result — the pattern `HabitDetailScreenTest`
 * sets. The state comes out of the real mapper rather than being hand-built:
 * thirty-one cells are too many to restate, and the two files agreeing on what a
 * month looks like is worth more here than their independence.
 *
 * The substance is the three display decisions a reader could delete without
 * noticing: that a day which has not happened draws nothing, that the ambiguous
 * column letters are hidden while every cell speaks its own weekday, and that
 * the later stepper is absent on the month containing today.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // Robolectric's own accessor rather than ApplicationProvider, which would be
    // androidx.test:core — a different library, reached only transitively through
    // ui-test-junit4 and carrying no catalog entry to bump.
    private val resources = RuntimeEnvironment.getApplication().resources

    private fun string(@StringRes id: Int): String = resources.getString(id)

    /** The spoken label for one cell, resolved the way the screen resolves it. */
    private fun dayLabel(@StringRes id: Int, day: DayOfWeek, dayOfMonth: Int): String =
        resources.getString(id, string(weekdayName(day)), dayOfMonth)

    private fun month(
        month: YearMonth = THIS_MONTH,
        name: String = "read",
        completed: Map<LocalDate, String?> = emptyMap(),
    ): HistoryUiState.Month = habitDetail(habit = todayHabit(habitState(name = name))).toMonthUiState(month, DayOfWeek.MONDAY, completed)

    private fun render(state: HistoryUiState, actions: HistoryActions = NO_ACTIONS) {
        compose.setContent {
            GawiTheme { HistoryScreen(state, actions) }
        }
    }

    @Test
    fun `the month is named, and so is the habit`() {
        render(month(name = "swim"))

        compose.onNodeWithText("swim").assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.insights_month_title, string(R.string.insights_month_august), 2026))
            .assertIsDisplayed()
    }

    @Test
    fun `the days up to today are drawn`() {
        render(month())

        listOf("1", "3", "17", "18").forEach { day ->
            compose.onNodeWithText(day).assertIsDisplayed()
        }
    }

    /**
     * docs/ux/insights.md §4, as the screen: a day that has not happened is not
     * a day that was missed, so it gets no cell at all rather than a quiet one.
     * Asserted as absent text, because a cell that drew nothing but its ground
     * would look identical in a screenshot to one that drew nothing.
     */
    @Test
    fun `a day that has not happened is not drawn at all`() {
        render(month())

        listOf("19", "20", "31").forEach { day ->
            compose.onAllNodesWithText(day).assertCountEquals(0)
        }
        // And nothing claims a state for one either.
        compose.onAllNodesWithContentDescription(dayLabel(R.string.insights_day_not_done, DayOfWeek.WEDNESDAY, 19))
            .assertCountEquals(0)
    }

    /** A month that is over draws all of its days, since none of them are future. */
    @Test
    fun `an earlier month draws every one of its days`() {
        render(month(month = THIS_MONTH.minusMonths(1)))

        compose.onNodeWithText("31").assertIsDisplayed()
    }

    @Test
    fun `a completed day says done and a finished empty one says not done`() {
        // 3 August 2026 is a Monday and 4 August a Tuesday.
        render(month(completed = mapOf(thisMonth(3) to null)))

        compose.onNodeWithContentDescription(dayLabel(R.string.insights_day_done, DayOfWeek.MONDAY, 3)).assertIsDisplayed()
        compose.onNodeWithContentDescription(dayLabel(R.string.insights_day_not_done, DayOfWeek.TUESDAY, 4)).assertIsDisplayed()
    }

    /**
     * Today is announced as today and as still open — "not done yet" rather than
     * the flat "not done" a finished day gets, which would be an accusation
     * about a day the user is still inside.
     */
    @Test
    fun `today announces itself, and that it is not over`() {
        render(month())

        // 18 August 2026 is a Tuesday, and is the fixture's logical date.
        compose.onNodeWithContentDescription(dayLabel(R.string.insights_day_today_not_done, DayOfWeek.TUESDAY, 18))
            .assertIsDisplayed()
    }

    @Test
    fun `today announces a completion too`() {
        render(month(completed = mapOf(thisMonth(18) to null)))

        compose.onNodeWithContentDescription(dayLabel(R.string.insights_day_today_done, DayOfWeek.TUESDAY, 18))
            .assertIsDisplayed()
    }

    /**
     * The column letters are hidden from the semantics tree on purpose — `T` and
     * `S` each name two days, so read aloud they are noise. What replaces them is
     * asserted in the same test, because hiding content is only defensible while
     * the cells carry the weekday themselves.
     */
    @Test
    fun `the column letters are not read aloud, and the cells carry the weekday instead`() {
        render(month())

        compose.onAllNodesWithText(string(weekdayLetter(DayOfWeek.MONDAY))).assertCountEquals(0)
        compose.onAllNodesWithText(string(weekdayLetter(DayOfWeek.WEDNESDAY))).assertCountEquals(0)
        compose.onNodeWithContentDescription(dayLabel(R.string.insights_day_not_done, DayOfWeek.SATURDAY, 1))
            .assertIsDisplayed()
    }

    @Test
    fun `the later stepper is absent on the month containing today`() {
        render(month())

        compose.onAllNodesWithContentDescription(string(R.string.insights_month_next)).assertCountEquals(0)
        compose.onNodeWithContentDescription(string(R.string.insights_month_previous)).assertIsDisplayed()
    }

    @Test
    fun `an earlier month offers the way back, and reports it`() {
        var later = 0
        render(month(month = THIS_MONTH.minusMonths(1)), NO_ACTIONS.copy(onLater = { later++ }))

        compose.onNodeWithContentDescription(string(R.string.insights_month_next)).performClick()

        assertEquals(1, later)
    }

    @Test
    fun `the earlier stepper reports`() {
        var earlier = 0
        render(month(), NO_ACTIONS.copy(onEarlier = { earlier++ }))

        compose.onNodeWithContentDescription(string(R.string.insights_month_previous)).performClick()

        assertEquals(1, earlier)
    }

    @Test
    fun `back reports`() {
        var back = 0
        render(month(), NO_ACTIONS.copy(onBack = { back++ }))

        compose.onNodeWithContentDescription(string(R.string.insights_back)).performClick()

        assertEquals(1, back)
    }

    @Test
    fun `an unavailable history explains itself`() {
        render(HistoryUiState.Unavailable)

        compose.onNodeWithText(string(R.string.insights_unavailable_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.insights_unavailable_body)).assertIsDisplayed()
    }

    /**
     * Blank rather than a spinner, since the first emission is one Room query.
     * Asserted as the absence of a grid rather than the presence of nothing: the
     * app bar is drawn in every branch, so "nothing on screen" would be false.
     */
    @Test
    fun `loading draws no grid`() {
        render(HistoryUiState.Loading)

        compose.onNodeWithText(string(R.string.insights_history_title)).assertIsDisplayed()
        compose.onAllNodesWithText("18").assertCountEquals(0)
        compose.onAllNodesWithContentDescription(string(R.string.insights_month_previous)).assertCountEquals(0)
    }

    @Test
    fun `nothing on the grid is clickable`() {
        // Read-only by docs/ux/insights.md §3: the domain refuses a completion
        // write outside the retro window, so a cell that answered a tap would be
        // a cell that lied. Asserted through the semantics rather than by
        // clicking, since a click on a node with no handler is a no-op either way.
        render(month(completed = mapOf(thisMonth(3) to null)))

        val cell = compose.onNodeWithContentDescription(dayLabel(R.string.insights_day_done, DayOfWeek.MONDAY, 3))
        assertTrue(cell.fetchSemanticsNode().config.none { it.key.name == "OnClick" })
    }

    private companion object {
        val NO_ACTIONS = HistoryActions(onEarlier = {}, onLater = {}, onBack = {})
    }
}
