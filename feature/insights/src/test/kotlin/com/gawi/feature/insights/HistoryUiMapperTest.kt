package com.gawi.feature.insights

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.ui.date.weekdayLetter
import com.gawi.feature.insights.testsupport.THIS_MONTH
import com.gawi.feature.insights.testsupport.TODAY
import com.gawi.feature.insights.testsupport.habitState
import com.gawi.feature.insights.testsupport.thisMonth
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
        habit: HabitState = habitState(),
    ): HistoryUiState.Month = habit.toMonthUiState(
        month = month,
        today = today,
        weekStart = weekStart,
        completedDates = completed,
        // The trend is its own function with its own tests; passing an empty one
        // keeps a grid assertion from depending on five months of arithmetic.
        rate = RateTrendUi(schedule = habit.schedule.toLabelUi(), points = emptyList()),
    )

    /** The trend, over the same fixtures the grid uses. */
    private fun trend(
        habit: HabitState = habitState(),
        completed: Set<LocalDate> = emptySet(),
        today: LocalDate = TODAY,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): RateTrendUi = habit.toRateTrend(today, weekStart, completed)

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
        val state = map(habit = habitState(name = "swim"))

        assertEquals("swim", state.habitName)
        assertEquals(THIS_MONTH.year, state.year)
    }

    // ---- the completion-rate trend ----

    @Test
    fun `the trend is five months, oldest first, ending on this one`() {
        val points = trend().points

        assertEquals(5, points.size)
        assertEquals(
            listOf(
                R.string.insights_month_april,
                R.string.insights_month_may,
                R.string.insights_month_june,
                R.string.insights_month_july,
                R.string.insights_month_august,
            ),
            points.map { it.monthName },
        )
    }

    /**
     * The decision this slice reversed, and the one worth a test of its own.
     *
     * The artboard drew the current month as a dash and justified it by saying
     * `Rates` returns null for a part-month. It does not: it counts only
     * *finished* units on both sides, so with today the 18th the month offers 17
     * opportunities and taking all 17 is 100% — not 17/31 = 55%. A dash here
     * would be withholding a number that is already comparable.
     */
    @Test
    fun `the current month draws a real number, not a dash`() {
        val everyFinishedDay = (1..17).map { thisMonth(it) }.toSet()

        val august = trend(completed = everyFinishedDay).points.last()

        assertEquals(100, august.percent)
    }

    /** Today itself is not an opportunity either, so missing it costs nothing. */
    @Test
    fun `today is excluded from its own month's rate`() {
        val everythingButToday = (1..17).map { thisMonth(it) }.toSet()

        assertEquals(100, trend(completed = everythingButToday).points.last().percent)
    }

    /**
     * A month wholly before the habit existed has no rate, because it offered no
     * opportunity — not a rate of zero, which would read as a month of failure.
     */
    @Test
    fun `a month before the habit existed draws a dash`() {
        val born = habitState(createdOn = thisMonth(1))

        val points = trend(habit = born).points

        assertEquals(listOf(null, null, null, null), points.dropLast(1).map { it.percent })
        assertEquals("the month it was created in still has a rate", 0, points.last().percent)
    }

    /**
     * And the month it was created in is measured from that day, not from the 1st.
     *
     * Created on the 12th, completed every finished day after — that is 100%, not
     * the 41% it would be if the eleven days before it existed counted as missed.
     * This is the limitation insights.md §4 recorded as unfixable before the
     * creation date was projected.
     */
    @Test
    fun `the month a habit was created in is measured from its creation`() {
        val born = habitState(createdOn = thisMonth(12))
        val since = (12..17).map { thisMonth(it) }.toSet()

        assertEquals(100, trend(habit = born, completed = since).points.last().percent)
    }

    @Test
    fun `a habit with no completions at all reads zero rather than a dash`() {
        // Zero is right here and a dash is not: the months are over, they offered
        // opportunities, and none were taken. A dash means "nothing had finished".
        assertEquals(0, trend().points.first().percent)
    }

    /**
     * The window read and the months drawn are one fact, asserted as an
     * invariant rather than as a number.
     *
     * These were two constants in two files, agreeing only because 4 + 1 = 5.
     * Widening the trend by editing the month count alone left the oldest month
     * unfetched, and a finished month measured against an empty set draws 0%
     * rather than a dash — a month the user is told they failed and never had
     * read. This holds whatever `TREND_MONTHS` becomes, which a literal date
     * would not.
     */
    @Test
    fun `the trend's window starts at the oldest month it draws`() {
        val points = trend().points

        assertEquals(points.size, 5)
        assertEquals(THIS_MONTH.minusMonths((points.size - 1).toLong()).atDay(1), trendWindowStart(TODAY))
        // And it holds on a date in a different month, so this is not the
        // fixture's calendar agreeing with itself.
        val november = LocalDate.parse("2026-11-09")
        assertEquals(YearMonth.from(november).minusMonths(4).atDay(1), trendWindowStart(november))
    }

    /**
     * Nothing to plot is a state, not a blank chart.
     *
     * A habit created today has five dashes, and an empty plot area above them
     * reads as a chart that failed to draw. Asserted here because the plot is
     * cleared from the semantics tree, so a screen test cannot see whether it is
     * there.
     */
    @Test
    fun `a trend with no rate at all has nothing to plot`() {
        val newborn = habitState(createdOn = TODAY)

        assertFalse(trend(habit = newborn).plottable)
        // One month with a number is enough — the sparkline draws it as a dot.
        assertTrue(trend().plottable)
    }

    @Test
    fun `a weekly habit's trend says what it is a rate of`() {
        val weekly = habitState(schedule = Schedule.Weekly(3))

        assertEquals(ScheduleLabelUi(R.string.insights_schedule_weekly, timesPerWeek = 3), trend(habit = weekly).schedule)
        assertEquals(ScheduleLabelUi(R.string.insights_schedule_daily, timesPerWeek = null), trend().schedule)
    }
}
