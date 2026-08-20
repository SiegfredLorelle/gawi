package com.gawi.feature.habits

import com.gawi.core.domain.model.Schedule

/**
 * A schedule while it is being chosen.
 *
 * A separate type from [Schedule] for one reason: `Schedule.Weekly` validates
 * its target with `require`, so constructing one out of range **throws** rather
 * than returning a rejection. A form has to be able to hold a half-made value
 * — including, mid-edit, an out-of-range one — without taking the app down, so
 * the editor holds this and converts on save.
 *
 * The cap is 1..7 because completions are idempotent per logical date
 * (architecture §4), so a target above seven can never be met.
 */
internal sealed interface ScheduleUi {

    data object Daily : ScheduleUi

    data class Weekly(val timesPerWeek: Int) : ScheduleUi
}

internal fun Schedule.toUi(): ScheduleUi = when (this) {
    Schedule.Daily -> ScheduleUi.Daily
    is Schedule.Weekly -> ScheduleUi.Weekly(timesPerWeek)
}

/**
 * Clamped, deliberately, rather than validated and rejected.
 *
 * The stepper in the editor already stops at both ends, so this is the backstop
 * for the case where something else sets the number. Coercing turns a bug into
 * a wrong-but-saved habit; letting `Schedule.Weekly`'s `require` fire would
 * turn the same bug into a crash on the save button.
 */
internal fun ScheduleUi.toDomain(): Schedule = when (this) {
    ScheduleUi.Daily -> Schedule.Daily
    is ScheduleUi.Weekly -> Schedule.Weekly(timesPerWeek.coerceIn(1, Schedule.DAYS_PER_WEEK))
}
