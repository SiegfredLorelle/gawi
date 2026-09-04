package com.gawi.feature.insights

import androidx.annotation.StringRes
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields

/**
 * The period the Insights screen reports on — settled as docs/ux/insights.md
 * §7's last open question.
 *
 * **Calendar periods, not trailing windows.** "Month" is this calendar month,
 * not the last thirty days. Two reasons, and the second is why it is not a
 * close call: the labels say so, and PRD §5's Phase 1.5 retrospectives are
 * *quarterly and yearly reviews* — the whole argument for deciding this once
 * rather than twice is that they share this picker, and a trailing window cannot
 * serve a quarterly review.
 *
 * Known consequence, recorded rather than hidden: on the 1st of a month, Month
 * has almost nothing in it and Quarter is the answer. That is the honest reading
 * of "this month" on the 1st, and the picker is right there.
 *
 * A window may extend past today, and that is deliberately not clipped here.
 * `Rates.completionRate` clips its own, for the reason its KDoc gives — it
 * anticipated this exact caller — and a **total** needs no clipping at all,
 * because a completion cannot be logged for a day that has not happened.
 */
internal enum class Period(@StringRes val label: Int) {

    MONTH(R.string.insights_period_month),
    QUARTER(R.string.insights_period_quarter),
    YEAR(R.string.insights_period_year),
    ;

    /**
     * Inclusive at both ends, in the calendar the label names, [back] periods
     * before the one holding [today].
     *
     * The retrospective's stepper (docs/ux/insights.md §9) is this one
     * parameter: `back = 0` is the period the screen opened on and `back = 1`
     * is the one before it, so "the previous period" — what the focus sentence
     * compares against — is the same function asked once more rather than a
     * second piece of date arithmetic that could drift from this one.
     */
    fun window(today: LocalDate, back: Int = 0): ClosedRange<LocalDate> = when (this) {
        MONTH -> YearMonth.from(today).minusMonths(back.toLong()).let { month -> month.atDay(1)..month.atEndOfMonth() }

        // IsoFields rather than month / 3 arithmetic: the same field the rest of
        // java.time buckets quarters by, so a quarter here and a quarter in a
        // retrospective cannot come to disagree.
        QUARTER -> {
            val first = LocalDate.of(today.year, quarterFirstMonth(today), 1).minusMonths(MONTHS_PER_QUARTER * back)
            first..first.plusMonths(MONTHS_PER_QUARTER).minusDays(1)
        }

        YEAR -> {
            val year = today.year - back
            LocalDate.of(year, 1, 1)..LocalDate.of(year, MONTHS_PER_YEAR, LAST_DECEMBER_DAY)
        }
    }

    private fun quarterFirstMonth(today: LocalDate): Int = (today.get(IsoFields.QUARTER_OF_YEAR) - 1) * MONTHS_PER_QUARTER.toInt() + 1

    private companion object {
        const val MONTHS_PER_QUARTER = 3L
        const val MONTHS_PER_YEAR = 12
        const val LAST_DECEMBER_DAY = 31
    }
}
