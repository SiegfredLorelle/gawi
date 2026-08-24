package com.gawi.feature.insights

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.gawi.core.domain.model.Schedule
import com.gawi.core.ui.date.weekdayLetter
import com.gawi.core.ui.date.weekdayName
import com.gawi.core.ui.theme.GawiTheme
import com.gawi.feature.insights.testsupport.THIS_MONTH
import com.gawi.feature.insights.testsupport.TODAY
import com.gawi.feature.insights.testsupport.habitState
import com.gawi.feature.insights.testsupport.thisMonth
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
        schedule: Schedule = Schedule.Daily,
    ): HistoryUiState.Month {
        val habit = habitState(name = name, schedule = schedule)
        return habit.toMonthUiState(
            month = month,
            today = TODAY,
            weekStart = DayOfWeek.MONDAY,
            completedDates = completed,
            rate = habit.toRateTrend(TODAY, DayOfWeek.MONDAY, completed.keys),
        )
    }

    /** A trend with nothing to say in any month — every point a dash. */
    private fun dashes(): RateTrendUi = habitState(createdOn = TODAY.plusDays(1))
        .toRateTrend(TODAY, DayOfWeek.MONDAY, emptySet())

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

    // ---- the completion-rate card ----

    @Test
    fun `the rate card names its months and says what the rate is of`() {
        render(month())

        // Scrolled to individually: the card sits under a six-row grid, so more
        // than one of these is off a short screen at once.
        listOf(
            string(R.string.insights_rate_title),
            string(R.string.insights_schedule_daily),
            // Five months, and the two ends of them.
            string(R.string.insights_month_april),
            string(R.string.insights_month_august),
        ).forEach { text ->
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }

    /**
     * A weekly habit says so, because §4 forbids reading its percentages as a
     * daily habit's. Asserted through the formatted string so the argument is
     * checked too — an id with no target passed would render "%1$d× a week".
     */
    @Test
    fun `a weekly habit's rate card carries its target`() {
        render(month(schedule = Schedule.Weekly(3)))

        compose.onNodeWithText(resources.getString(R.string.insights_schedule_weekly, 3)).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(string(R.string.insights_schedule_daily)).assertCountEquals(0)
    }

    /**
     * The line is hidden and the numbers are not — the trade the card's KDoc
     * makes. If the Canvas ever announces itself, a reader gets "graphic" where
     * they used to get five percentages.
     */
    @Test
    fun `the sparkline is not in the semantics tree, and the percentages are`() {
        // Every finished day of this month done, so the last point is a real 100%
        // rather than a dash — the decision this slice reversed.
        val everyFinishedDay = (1..17).associate { thisMonth(it) to null }
        render(month(completed = everyFinishedDay))

        compose.onNodeWithText(resources.getString(R.string.insights_rate_percent, 100)).performScrollTo().assertIsDisplayed()
        // A Canvas with cleared semantics leaves nothing findable of its own; the
        // labels above and below it are the whole of what a reader gets.
        compose.onAllNodesWithContentDescription(string(R.string.insights_rate_title)).assertCountEquals(0)
    }

    @Test
    fun `a month with nothing finished draws a dash rather than a zero`() {
        // April is five months back and this habit was created this month, so
        // April offered nothing — a dash, not 0%.
        render(month(name = "new", completed = emptyMap()).copy(rate = dashes()))

        compose.onAllNodesWithText(string(R.string.insights_rate_none)).onFirst().performScrollTo().assertIsDisplayed()
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
