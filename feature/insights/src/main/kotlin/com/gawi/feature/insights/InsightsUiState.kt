package com.gawi.feature.insights

/**
 * What the Insights screen draws: one period, two numbers about it, and one
 * breakdown of it.
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
        val habits: List<HabitRateUi>,
        val tags: List<TagShareUi>,
        /**
         * Whether the user has any habit at all, archived or not.
         *
         * Carried because [habits] cannot answer it: that list is the
         * *unarchived* ones, so it is empty both on a fresh install and when
         * everything has been archived — two states that deserve opposite
         * advice. Without this the screen told a brand-new user "every habit is
         * archived", which is a claim about their data that was simply untrue,
         * and Insights is reachable from Today's app bar before a first habit
         * exists.
         */
        val hasAnyHabit: Boolean,
    ) : InsightsUiState
}

/** Which breakdown of the period is on screen. */
internal enum class Breakdown { HABITS, TAGS }

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
 */
internal data class HabitRateUi(val name: String, val schedule: ScheduleLabelUi, val percent: Int?)

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
