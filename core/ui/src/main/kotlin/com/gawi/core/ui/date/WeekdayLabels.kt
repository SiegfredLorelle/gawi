package com.gawi.core.ui.date

import androidx.annotation.StringRes
import com.gawi.core.ui.R
import java.time.DayOfWeek

/**
 * A weekday's one-letter label, as a resource id.
 *
 * From resources rather than `DayOfWeek.getDisplayName`, which reads the JVM's
 * locale rather than the app's resource configuration — so a device set to one
 * language could draw a week in another.
 *
 * Returns an id rather than a `String` so no caller has to name `:core:ui`'s
 * `R` class, and so this stays callable from a mapper with no composition
 * around it.
 *
 * **A letter cannot identify a day on its own** — `T` and `S` each name two —
 * so anything that has to *say* a weekday takes [weekdayName] instead. Where
 * these are drawn the column or the cell beside them does the disambiguating.
 */
@StringRes
fun weekdayLetter(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.ui_day_mon
    DayOfWeek.TUESDAY -> R.string.ui_day_tue
    DayOfWeek.WEDNESDAY -> R.string.ui_day_wed
    DayOfWeek.THURSDAY -> R.string.ui_day_thu
    DayOfWeek.FRIDAY -> R.string.ui_day_fri
    DayOfWeek.SATURDAY -> R.string.ui_day_sat
    DayOfWeek.SUNDAY -> R.string.ui_day_sun
}

/**
 * A weekday spelled out, as a resource id.
 *
 * The form for anything read aloud or read on its own: the week-start picker's
 * options, and the history grid's spoken cell labels. Shared for the same reason
 * [weekdayLetter] is — two features draw these seven names, and anything drawn
 * by more than one belongs here (AGENTS.md).
 *
 * Exhaustive rather than defaulted, so this is a compile error if `java.time`
 * ever grows an eighth day. It costs nothing to hold.
 */
@StringRes
fun weekdayName(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.ui_weekday_monday
    DayOfWeek.TUESDAY -> R.string.ui_weekday_tuesday
    DayOfWeek.WEDNESDAY -> R.string.ui_weekday_wednesday
    DayOfWeek.THURSDAY -> R.string.ui_weekday_thursday
    DayOfWeek.FRIDAY -> R.string.ui_weekday_friday
    DayOfWeek.SATURDAY -> R.string.ui_weekday_saturday
    DayOfWeek.SUNDAY -> R.string.ui_weekday_sunday
}
