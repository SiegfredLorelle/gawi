package com.gawi.feature.insights

import androidx.annotation.StringRes
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.rate.Rates
import com.gawi.core.ui.date.weekdayLetter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * The read model as the history grid draws it.
 *
 * Here rather than in the composable for the reason the other feature modules'
 * mappers give: which column the 1st falls in, which day is today and which
 * days have not happened yet are decisions, and a composable can only get a
 * decision wrong in a screenshot.
 *
 * Pure and JVM-testable, which is what lets the week-start alignment — the one
 * piece of arithmetic here that can be wrong by exactly one column — be
 * asserted for both settings rather than looked at.
 *
 * The note values in [completedDates] are discarded. A note hangs off a
 * completion and is habit detail's business (architecture §4); a month grid
 * that marked one would be advertising an edit it cannot make, since this
 * screen is read-only by docs/ux/insights.md §3.
 */
internal fun HabitState.toMonthUiState(
    month: YearMonth,
    today: LocalDate,
    weekStart: DayOfWeek,
    completedDates: Map<LocalDate, String?>,
    rate: RateTrendUi,
): HistoryUiState.Month = HistoryUiState.Month(
    habitName = name,
    monthName = monthName(month.month),
    year = month.year,
    weekdayLetters = weekdayColumns(weekStart),
    leadingBlanks = columnOf(month.atDay(1).dayOfWeek, weekStart),
    days = (1..month.lengthOfMonth()).map { dayOfMonth ->
        val date = month.atDay(dayOfMonth)
        DayCellUi(
            date = date,
            dayOfMonth = dayOfMonth,
            // containsKey, not the value: a null value means completed with no
            // note, and reading `get() != null` here would draw every
            // unannotated completion as a day that was not done.
            completed = completedDates.containsKey(date),
            isToday = date == today,
            future = date.isAfter(today),
        )
    },
    // Decided against the date the repository read for, never against a date
    // resolved on this side. A screen cannot resolve one: that needs a clock, a
    // zone and the day cutoff (architecture §5).
    canGoLater = month < YearMonth.from(today),
    rate = rate,
)

/**
 * The last few months as percentages, or dashes where there is nothing to say.
 *
 * The denominator comes from [Rates], never from counting rows, because "the
 * completion rate" is two different fractions and only the schedule decides
 * which (docs/ux/insights.md §4). That is also why this takes the habit as its
 * receiver rather than a bare set of dates.
 *
 * **A month is clipped to [HabitState.createdOn], and dropped if it ends before
 * it.** Without that, a habit created three weeks into a month reads as though
 * it had missed the first three — a number that is arithmetically right and
 * accuses the user of days they were never offered. insights.md §4 recorded this
 * as a limitation with no fix; the fix is the creation date now being projected.
 * A null `createdOn` clips nothing, which is the only honest response to not
 * knowing.
 */
internal fun HabitState.toRateTrend(today: LocalDate, weekStart: DayOfWeek, completedDates: Set<LocalDate>): RateTrendUi = RateTrendUi(
    schedule = schedule.toLabelUi(),
    points = trendMonths(today).map { month ->
        RatePointUi(monthName = monthName(month.month), percent = percentIn(month, today, weekStart, completedDates))
    },
)

/** Oldest first, ending on the month [today] falls in. */
private fun trendMonths(today: LocalDate): List<YearMonth> {
    val current = YearMonth.from(today)
    return (TREND_MONTHS - 1 downTo 0).map { back -> current.minusMonths(back.toLong()) }
}

private fun HabitState.percentIn(month: YearMonth, today: LocalDate, weekStart: DayOfWeek, completedDates: Set<LocalDate>): Int? {
    val end = month.atEndOfMonth()
    val born = createdOn
    // The whole month is before the habit existed. Not a rate of zero — there
    // was no opportunity to take, so there is no fraction to draw.
    if (born != null && born > end) return null
    val start = if (born != null) maxOf(month.atDay(1), born) else month.atDay(1)
    val rate = Rates.completionRate(completedDates, schedule, start..end, today, weekStart)
    return rate.fraction?.let { (it * PERCENT).roundToInt() }
}

/**
 * The seven column headers, ordered from the user's week start.
 *
 * `DayOfWeek.plus` wraps, so this is the whole rotation — starting on Sunday
 * gives Sun…Sat with no special case. The setting is the same one the weekly
 * targets and the week-based streaks read, which is the point: a grid that
 * started on Monday for a user whose weeks start on Sunday would put its rows
 * out of step with every other week in the app.
 */
private fun weekdayColumns(weekStart: DayOfWeek): List<Int> =
    (0 until DAYS_IN_WEEK).map { offset -> weekdayLetter(weekStart.plus(offset.toLong())) }

/**
 * How many columns from the week start [day] sits — 0 when it *is* the start.
 *
 * `DayOfWeek.value` is 1 for Monday through 7 for Sunday, so the difference
 * runs -6..6 and the `+ 7` is what makes the remainder non-negative rather than
 * a column index of -3. Kotlin's `%` keeps the sign of the dividend, which is
 * the trap this line exists to avoid.
 */
private fun columnOf(day: DayOfWeek, weekStart: DayOfWeek): Int = (day.value - weekStart.value + DAYS_IN_WEEK) % DAYS_IN_WEEK

/**
 * A month's name, from resources rather than `java.time.format`.
 *
 * The same reason `:core:ui`'s [weekdayLetter] gives: a formatter reads the
 * JVM's locale rather than the app's resource configuration, so a device set to
 * one language could label the grid in another.
 */
@StringRes
private fun monthName(month: Month): Int = when (month) {
    Month.JANUARY -> R.string.insights_month_january
    Month.FEBRUARY -> R.string.insights_month_february
    Month.MARCH -> R.string.insights_month_march
    Month.APRIL -> R.string.insights_month_april
    Month.MAY -> R.string.insights_month_may
    Month.JUNE -> R.string.insights_month_june
    Month.JULY -> R.string.insights_month_july
    Month.AUGUST -> R.string.insights_month_august
    Month.SEPTEMBER -> R.string.insights_month_september
    Month.OCTOBER -> R.string.insights_month_october
    Month.NOVEMBER -> R.string.insights_month_november
    Month.DECEMBER -> R.string.insights_month_december
}

/** Shared with the grid, which chunks its slots by it. */
internal const val DAYS_IN_WEEK = 7

/**
 * Five, which is the artboard's — enough to read a direction from and few enough
 * that the labels stay legible at a phone's width without abbreviating a month.
 */
private const val TREND_MONTHS = 5

private const val PERCENT = 100
