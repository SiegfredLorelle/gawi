package com.gawi.feature.insights

import androidx.annotation.StringRes
import com.gawi.core.ui.streak.StreakUi

/**
 * What the Insights screen draws: one period, two numbers about it, one
 * breakdown of it, and the review of that period — where it sits in the
 * calendar, how the months in it went, and where the focus moved.
 *
 * The app's only surface that reports on **every** habit at once. Everything
 * else — the Today view, habit detail, the history grid, the rate trend — is
 * about one habit or one day. That gap is what this screen exists to close, and
 * it is why the tag distribution lives here: docs/ux/insights.md §5's metric is
 * one number per tag across every habit, so it has no home on a per-habit
 * screen.
 *
 * Two branches rather than three. There is no empty state: a period with
 * nothing logged still has a period, two zeroes and a picker to change it, so
 * [Overview] with empty rows is the honest shape and the screen draws a notice
 * where the rows would be.
 */
internal sealed interface InsightsUiState {

    data object Loading : InsightsUiState

    data object Unavailable : InsightsUiState

    /**
     * Both breakdowns are carried, and only one is drawn.
     *
     * They come out of the same two reads, so computing both costs nothing and
     * flipping the toggle re-queries nothing — which is what makes it feel like
     * a view of one thing rather than two screens sharing a title.
     */
    data class Overview(
        val period: Period,
        /** Which calendar period the screen is on, for the stepper's label. */
        val label: PeriodLabelUi,
        /**
         * Whether the stepper can move forward — false on the period holding
         * today. Carried rather than derived so the screen never learns what
         * the offset is; it only learns whether the later arrow works.
         */
        val canStepLater: Boolean,
        /**
         * Whether the stepper can move back — false once the period starts at
         * or before the oldest habit's creation, or when there is no habit.
         * Walking further would re-read three flows to draw nothing. A habit
         * whose creation date is unknown (projected before `createdOn` existed)
         * keeps the arrow live: unknown is not "nothing before here".
         */
        val canStepEarlier: Boolean,
        val breakdown: Breakdown,
        /**
         * Days in the period with at least one completion.
         *
         * The honest cousin of a "perfect days" count. *Every* habit done on a
         * given day is not answerable for a past day — a weekly habit has no due
         * day at all (docs/ux/insights.md §4) — so this says you turned up,
         * which needs no denominator and cannot be wrong.
         */
        val activeDays: Int,
        /** Completions in the period, across every habit, archived included. */
        val completions: Int,
        /** Where the effort moved against the period before; null when there is nothing to say. */
        val focus: FocusShiftUi?,
        /**
         * Active days per month across the period, oldest first — empty for a
         * single month, and for months that have not happened yet.
         */
        val trend: List<TrendPointUi>,
        val habits: List<HabitRateUi>,
        val tags: List<TagShareUi>,
        /**
         * Whether the user has any habit at all, archived or not.
         *
         * Carried because [habits] cannot answer it: that list is the
         * *unarchived* ones, so it is empty both on a fresh install and when
         * everything has been archived — two states that deserve opposite
         * advice. Without it the screen tells a brand-new user "every habit is
         * archived", a claim about their data that is simply untrue, and Insights
         * is reachable from Today's app bar before a first habit exists.
         */
        val hasAnyHabit: Boolean,
    ) : InsightsUiState
}

internal enum class Breakdown { HABITS, TAGS }

/**
 * The stepper's caption: which month, quarter or year the numbers describe.
 *
 * Resource ids and integers rather than a formatted string, like the history
 * grid's title, so the screen composes it under its own resource configuration
 * and no `java.time.format` locale leaks in.
 */
internal sealed interface PeriodLabelUi {
    data class Month(@StringRes val name: Int, val year: Int) : PeriodLabelUi

    data class Quarter(val quarter: Int, val year: Int) : PeriodLabelUi

    data class Year(val year: Int) : PeriodLabelUi
}

/**
 * One sentence about where the effort went.
 *
 * "Focus" is the largest **tagged** total. Untagged is the residual and never a
 * focus: "shifted from career to Untagged" would say the user stopped caring
 * when all they stopped doing was labelling. When the period has no tagged
 * completion there is no sentence, not a guess.
 *
 * [Shifted] and [Held] compare a period with the one before it and are spoken
 * only for a **complete** period. The current one is still being lived: on 1
 * July a single completion is the whole of "this quarter", and letting it
 * announce a quarter-scale shift would be the claim the trend card refuses when
 * it declines to draw one point as a line. So the current period gets [SoFar],
 * which names the leading tag and claims nothing about movement.
 */
internal sealed interface FocusShiftUi {
    data class Shifted(val from: String, val to: String) : FocusShiftUi

    data class Held(val tag: String) : FocusShiftUi

    /** The current, unfinished period's leading tag — no comparison made. */
    data class SoFar(val tag: String) : FocusShiftUi
}

/**
 * One month of the period's trend: days with at least one completion, and that
 * count as a fraction of the days the month has had — [fill] is what the line is
 * drawn from and [activeDays] is what the label says.
 *
 * Calendar days are the denominator because they are the only one honest for a
 * daily and a weekly habit at once (docs/ux/insights.md §4); a per-habit rate
 * trend over the period would be one line per habit or a dishonest average.
 */
internal data class TrendPointUi(@StringRes val monthName: Int, @StringRes val monthInitial: Int, val activeDays: Int, val fill: Float)

/**
 * One habit's adherence over the period.
 *
 * A row per habit rather than one averaged number, and that is a rule and not a
 * layout choice: a daily habit's rate is completions over days and a weekly
 * habit's is completions over `timesPerWeek × weeks`, so an app-wide average
 * would be the two fractions insights.md §4 exists to keep apart, added
 * together. Each row keeps its own denominator and says which it is.
 *
 * [percent] is null when the period held nothing that had finished — a habit
 * created today, or one created after the period ended. A dash, never a zero.
 *
 * [best] is the longest run inside the period, carrying its unit the way every
 * other streak on screen does ([StreakUi.Days] or [StreakUi.Weeks]), and null
 * rather than a zero when there was none: "best 0 days" under a row is the
 * screen accusing the user. It can stand beside a dash — a habit created and
 * completed today has a one-day run and no finished day to rate — because a run
 * counts today when today is done, as the streak on Today does, while a rate
 * counts only finished units. Two rules, both honest, and this is where they
 * meet.
 *
 * `StreakUi` carries two states this row can never hold, `None` and `Broken`;
 * they are Today's. The type is reused for its unit split all the same, because
 * a third streak type that differed from it only by lacking those two would be
 * the drift the shared one exists to stop. `bestText` names the dead branch.
 */
internal data class HabitRateUi(val name: String, val schedule: ScheduleLabelUi, val percent: Int?, val best: StreakUi? = null)

/**
 * One tag's share of the effort.
 *
 * [completions] is a **total** and there is no percentage anywhere on the row.
 * Narrower than PRD §5's word "share", deliberately: a total cannot become wrong
 * the day OQ-1's multi-tag change lets a completion carry two tags, while a
 * percentage has to be redefined by it (insights.md §5).
 *
 * [fill] is the bar's length as a fraction of the **largest** total, so the bars
 * compare rather than sum. [name] is null for untagged effort, which is a real
 * row: drop it and the rows describe a whole that is not the whole.
 */
internal data class TagShareUi(val name: String?, val completions: Int, val fill: Float)
