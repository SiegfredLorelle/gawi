package com.gawi.core.domain.model

/**
 * A habit's schedule (PRD §4): daily, or n times per week on any days.
 * Weekly targets are capped at 7 because completions are idempotent per
 * logical date (architecture §4) — more than one completion per day can
 * never count, so a target above 7 would be unsatisfiable.
 */
sealed interface Schedule {

    data object Daily : Schedule

    data class Weekly(val timesPerWeek: Int) : Schedule {
        init {
            require(timesPerWeek in 1..DAYS_PER_WEEK) {
                "weekly target must be in 1..$DAYS_PER_WEEK, was $timesPerWeek"
            }
        }
    }

    companion object {
        const val DAYS_PER_WEEK = 7
    }
}
