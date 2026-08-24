package com.gawi.feature.insights

import androidx.annotation.StringRes
import java.time.LocalDate

/**
 * What the history grid draws.
 *
 * Three branches, the same three habit detail has and for the same reasons
 * (`HabitDetailUiState`): there is no empty state, because a month with nothing
 * completed in it is a month of not-done days rather than an absence, and
 * [Unavailable] covers both a malformed id and one that resolves to nothing —
 * with no delete in the event model those mean the same thing and leave the
 * user the same nothing to do about it.
 *
 * This is docs/ux/insights.md §2's first surface: PRD §5's *"per-habit
 * heatmap/calendar history"*. Read-only, deliberately — §3: the domain rejects
 * a completion write outside the retro window, so a writable month grid would
 * draw twenty-odd cells that look identical to the four that work and refuse
 * the rest. The writable window is habit detail's five-cell strip and stays
 * there.
 */
internal sealed interface HistoryUiState {

    data object Loading : HistoryUiState

    data object Unavailable : HistoryUiState

    /**
     * One month of one habit.
     *
     * [monthName] and [weekdayLetters] are resource ids rather than strings: the
     * mapper has no composition around it, and resolving either through
     * `java.time.format` would read the JVM's locale instead of the app's
     * resource configuration.
     */
    data class Month(
        val habitName: String,
        @StringRes val monthName: Int,
        val year: Int,
        /**
         * Seven one-letter ids, ordered from the user's week start — so the
         * columns follow the same setting the week-based streaks and the weekly
         * targets do.
         *
         * Letters, not names. The spelled-out form is what a cell's spoken label
         * carries, because a letter cannot be read aloud; these are the visual
         * headers and nothing else.
         */
        val weekdayLetters: List<Int>,
        /**
         * Empty slots before the 1st, so it lands under its own weekday column.
         *
         * Zero when the month begins on the week start. Held as a count rather
         * than as null cells in [days], because a blank leading slot is a
         * layout fact and a cell is a day — mixing them would make every
         * consumer ask which kind it was holding.
         */
        val leadingBlanks: Int,
        /** Every day of the month, oldest first. */
        val days: List<DayCellUi>,
        /**
         * False on the month containing today.
         *
         * Later than that is all [DayCellUi.future], so the stepper would open
         * a grid with nothing in it. Earlier has no such bound: a month before
         * the habit existed draws as a month nothing was done in, which is
         * empty rather than untrue — unlike a *rate* over that window, which
         * [RateTrendUi] draws as a dash for exactly that reason.
         */
        val canGoLater: Boolean,
        /** The trailing months, which do **not** move when the grid is stepped. */
        val rate: RateTrendUi,
    ) : HistoryUiState
}

/**
 * The completion-rate trend: a handful of finished months, and this one.
 *
 * Deliberately not driven by the grid's steppers, and not by a period picker
 * either. A trend needs several buckets to be a trend, so "which period" is the
 * wrong question to ask of it — the answer is always "the last few months" —
 * while the grid can only ever show one month and the Insights screen's picker
 * governs one window. Three surfaces, three different time questions.
 *
 * [scheduleLabel] is on the card rather than implied, because docs/ux/insights.md
 * §4 forbids reading one habit's percentages as another's: a daily habit's rate
 * is completions over days and a weekly habit's is completions over
 * `timesPerWeek × weeks`, and only the label says which one these are.
 */
internal data class RateTrendUi(val schedule: ScheduleLabelUi, val points: List<RatePointUi>) {

    /**
     * Whether there is anything to plot at all.
     *
     * A habit created today has five dashes and no line, and an empty plot area
     * above the labels reads as a chart that failed to draw rather than as a
     * chart with nothing in it. So the plot is omitted and the labelled dashes
     * carry the whole message — which they already did.
     *
     * Here rather than in the composable because it is a decision about the
     * data, and one a screenshot is the only other way to catch. One point is
     * enough: the sparkline draws it as a dot, and a single month's rate is
     * worth seeing even with no line to put it on.
     */
    val plottable: Boolean get() = points.any { it.percent != null }
}

/**
 * One month of the trend. A null [percent] draws a dash.
 *
 * **Null means "nothing in this month had finished", and nothing else.** It is
 * `CompletionRate.fraction`'s own null — a habit created this month, or a month
 * that predates the habit entirely — never a stand-in for "this month is not
 * over". The current month draws a real number: `Rates` counts only finished
 * units on *both* sides of the fraction, so a month three weeks in is 15 of 15
 * rather than 15 of 31, which is already comparable to a finished one. Drawing a
 * dash there would be withholding a number the calculator went to some trouble
 * to make safe.
 */
internal data class RatePointUi(@StringRes val monthName: Int, val percent: Int?)

/**
 * One day in the grid.
 *
 * [completed] and [future] are not two ways of saying the same thing.
 * A day after the logical date is drawn as nothing at all, because an
 * unfinished day is not a miss — the liveness rule `Rates` applies to a
 * completion rate, stated in pixels. A not-done day that has finished is a
 * cell, and it says only that: docs/ux/insights.md §4 rules out a "missed"
 * state entirely, since `Schedule.Weekly` is *n times per week on any days* and
 * so has no day it was supposed to be done on.
 *
 * [date] is carried even though nothing on a read-only grid clicks. It is what
 * the spoken label and the tests are about, and deriving it back from
 * [dayOfMonth] and the month header would be re-deciding in two places what the
 * mapper already decided once.
 */
internal data class DayCellUi(val date: LocalDate, val dayOfMonth: Int, val completed: Boolean, val isToday: Boolean, val future: Boolean)
