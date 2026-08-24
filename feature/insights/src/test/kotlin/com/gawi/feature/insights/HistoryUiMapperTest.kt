package com.gawi.feature.insights

import com.gawi.core.ui.date.weekdayLetter
import com.gawi.feature.insights.testsupport.THIS_MONTH
import com.gawi.feature.insights.testsupport.TODAY
import com.gawi.feature.insights.testsupport.habitDetail
import com.gawi.feature.insights.testsupport.habitState
import com.gawi.feature.insights.testsupport.thisMonth
import com.gawi.feature.insights.testsupport.todayHabit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

/**
 * The grid's arithmetic, on the JVM.
 *
 * Plain unit tests, no Robolectric: the mapper returns resource *ids* rather
 * than resolved copy, precisely so it can be read without a framework around
 * it. What is asserted here is the class of mistake a screenshot would not
 * catch — a column index off by one, a completion with no note read as a day
 * that was not done, a day that has not happened drawn as one that was missed.
 */
class HistoryUiMapperTest {

    private fun map(
        month: YearMonth = THIS_MONTH,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        completed: Map<LocalDate, String?> = emptyMap(),
        today: LocalDate = TODAY,
    ): HistoryUiState.Month = habitDetail(today = today).toMonthUiState(month, weekStart, completed)

    /**
     * August 2026 starts on a Saturday, which is five columns after Monday and
     * six after Sunday. Two different answers from one month is what makes this
     * a test of the setting rather than of the calendar.
     */
    @Test
    fun `the first of the month lands under its own weekday column`() {
        assertEquals(5, map(weekStart = DayOfWeek.MONDAY).leadingBlanks)
        assertEquals(6, map(weekStart = DayOfWeek.SUNDAY).leadingBlanks)
    }

    @Test
    fun `the columns start on the week start`() {
        val monday = map(weekStart = DayOfWeek.MONDAY)
        assertEquals(7, monday.weekdayLetters.size)
        assertEquals(weekdayLetter(DayOfWeek.MONDAY), monday.weekdayLetters.first())
        assertEquals(weekdayLetter(DayOfWeek.SUNDAY), monday.weekdayLetters.last())

        val sunday = map(weekStart = DayOfWeek.SUNDAY)
        assertEquals(weekdayLetter(DayOfWeek.SUNDAY), sunday.weekdayLetters.first())
        assertEquals(weekdayLetter(DayOfWeek.SATURDAY), sunday.weekdayLetters.last())
    }

    @Test
    fun `every day of the month is a cell, oldest first`() {
        val august = map()
        assertEquals(31, august.days.size)
        assertEquals(1, august.days.first().dayOfMonth)
        assertEquals(31, august.days.last().dayOfMonth)
        assertEquals(thisMonth(1), august.days.first().date)

        // A short month, so the grid is not quietly assuming 31.
        assertEquals(28, map(month = YearMonth.of(2026, Month.FEBRUARY)).days.size)
        assertEquals(29, map(month = YearMonth.of(2028, Month.FEBRUARY)).days.size)
    }

    /**
     * The `containsKey` rule, and the one mistake here that would be invisible:
     * a null value means "completed, with no note", so reading the value would
     * draw every unannotated completion as a day that was not done — which is
     * most of them.
     */
    @Test
    fun `a completion with no note is still completed`() {
        val days = map(completed = mapOf(thisMonth(3) to null, thisMonth(4) to "went far")).days

        assertTrue(days.single { it.dayOfMonth == 3 }.completed)
        assertTrue(days.single { it.dayOfMonth == 4 }.completed)
        assertFalse(days.single { it.dayOfMonth == 5 }.completed)
    }

    @Test
    fun `a completion in another month marks nothing in this one`() {
        val days = map(completed = mapOf(LocalDate.parse("2026-07-03") to null)).days

        assertTrue(days.none { it.completed })
    }

    /**
     * docs/ux/insights.md §4, and `Rates`' liveness rule in pixels: a day that
     * has not happened is not a day that was missed. Today itself is neither —
     * it is today, and it is still open.
     */
    @Test
    fun `today is marked, and the days after it have not happened`() {
        val days = map().days

        assertTrue(days.single { it.dayOfMonth == 18 }.isToday)
        assertFalse(days.single { it.dayOfMonth == 18 }.future)
        assertFalse(days.single { it.dayOfMonth == 17 }.future)
        assertTrue(days.single { it.dayOfMonth == 19 }.future)
        assertTrue(days.single { it.dayOfMonth == 31 }.future)
        assertEquals(1, days.count { it.isToday })
    }

    @Test
    fun `a month that is over is all past, and one before this one can be stepped forward from`() {
        val july = map(month = YearMonth.of(2026, Month.JULY))

        assertTrue(july.days.none { it.future })
        assertTrue(july.days.none { it.isToday })
        assertTrue(july.canGoLater)
    }

    @Test
    fun `the month containing today cannot be stepped forward from`() {
        assertFalse(map().canGoLater)
    }

    @Test
    fun `the twelve month names are twelve different names`() {
        // Cheap, and it rules out the one way a twelve-branch `when` goes wrong:
        // a copy-pasted branch pointing at the month above it.
        val names = (1..12).map { month -> map(month = YearMonth.of(2026, month)).monthName }

        assertEquals(12, names.distinct().size)
    }

    @Test
    fun `the habit's name comes through`() {
        val state = habitDetail(habit = todayHabit(habitState(name = "swim"))).toMonthUiState(THIS_MONTH, DayOfWeek.MONDAY, emptyMap())

        assertEquals("swim", state.habitName)
        assertEquals(THIS_MONTH.year, state.year)
    }
}
