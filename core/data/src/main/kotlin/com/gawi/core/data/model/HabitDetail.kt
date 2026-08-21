package com.gawi.core.data.model

import com.gawi.core.domain.command.Commands
import java.time.LocalDate

/**
 * One habit as the detail screen needs it: what it is, where it stands today,
 * and the recent days it can still write to.
 *
 * Carries [today] for the same reason `TodaySnapshot` does, and it is the whole
 * point of this type existing beside [TodayHabit]. The retro strip has to size
 * its window against a date and write completions to one, and nothing above
 * `:core:data` may resolve a logical date of its own — that needs a clock, a
 * zone and the configurable day cutoff (architecture §5). A screen that derived
 * its own "today" could disagree with the rule the command validates against,
 * and the 3-day window *accepts* a date one day stale rather than refusing it,
 * so the disagreement would be a silent wrong answer rather than an error.
 *
 * [recent] is queried in the same pass as [habit], off the same [today], so the
 * cells and the date they were queried for cannot disagree.
 */
data class HabitDetail(
    val habit: TodayHabit,
    val today: LocalDate,
    /**
     * The completed cells in [stripWindow], mapped to the note showing on each.
     *
     * A `null` value means completed with no note. Absent means not completed —
     * the two are different, and a screen that conflated them would draw an
     * empty note as a missing day.
     */
    val recent: Map<LocalDate, String?>,
) {

    /** The oldest day the strip draws — one older than the oldest it can write. */
    val stripStart: LocalDate get() = stripWindow(today).first

    companion object {

        /**
         * The strip's span: the retro window, plus one day that is already shut.
         *
         * The shut day is deliberate and is docs/ux/today-view.md §5's rule —
         * "days outside the retro window are drawn shut, not tapped and
         * refused", with today at Tue 19 making Sat 16 the oldest open day and
         * Fri 15 the one drawn struck through. The command rule should be
         * readable before it is hit, which needs one refused day on screen.
         *
         * Derived from [Commands.RETRO_WINDOW_DAYS] rather than written as a
         * number, so the strip cannot come to disagree with the rule that
         * decides what a tap on it is allowed to do.
         */
        const val SHUT_DAYS = 1L

        /** Inclusive, oldest first: `today - (window + 1) .. today`. */
        fun stripWindow(today: LocalDate): Pair<LocalDate, LocalDate> = today.minusDays(Commands.RETRO_WINDOW_DAYS + SHUT_DAYS) to today
    }
}
