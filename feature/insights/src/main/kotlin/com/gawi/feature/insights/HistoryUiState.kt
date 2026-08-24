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
         * insights.md §4 records as arithmetically right and meaningless.
         */
        val canGoLater: Boolean,
    ) : HistoryUiState
}

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
