package com.gawi.core.domain.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * The date a moment belongs to, given the configurable day-boundary cutoff
 * (architecture §5): with a 03:00 cutoff, 01:30 on the 16th is still the
 * 15th. A wall time exactly at the cutoff starts the new day.
 *
 * Called at command time only — the completion event stores the result, so
 * later cutoff or timezone changes are prospective and replay never
 * re-buckets. Comparison is on wall-clock time, which keeps the mapping
 * total and deterministic through DST gaps and overlaps: a cutoff that does
 * not exist on a given day still partitions it, and both occurrences of a
 * repeated wall time land on the same logical date.
 *
 * Accepted anomaly: a cutoff strictly inside a DST fall-back's repeated
 * hour makes "today" regress to "yesterday" for the rewound stretch (wall
 * clock passes the cutoff, then falls back below it). It is deterministic,
 * affects at most one hour a year for cutoffs inside the shift window, and
 * is pinned by a test rather than special-cased.
 */
fun logicalDate(instant: Instant, cutoff: LocalTime, zone: ZoneId): LocalDate {
    val local = instant.atZone(zone)
    return if (local.toLocalTime() < cutoff) local.toLocalDate().minusDays(1) else local.toLocalDate()
}

/**
 * The start date of the week [date] falls in, under the configurable week
 * start (architecture §5).
 *
 * Weeks are keyed by this date and never by week-of-year numbers, which
 * misbucket the days around New Year. Public rather than internal because the
 * three places that bucket a week — the streak calculators, the mascot's mood
 * rules, and the read model's week bounds in `:core:data` — must not be able
 * to disagree about where a week begins.
 */
fun weekStartOn(date: LocalDate, weekStart: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(weekStart))

/**
 * The wall-clock moment the end-of-day reminder threshold falls for the
 * logical date [today].
 *
 * The logical day runs from its cutoff to the next, so the reminder falls on
 * the *following* calendar date whenever it is set earlier in the day than the
 * cutoff. With a 03:00 cutoff and a 21:00 reminder, 01:30 on the 16th is 22:30
 * into the logical 15th — the case a same-date comparison gets backwards.
 *
 * A reminder set *equal* to the cutoff therefore marks the day's start rather
 * than its end. That follows from [logicalDate]'s own rule that a wall time
 * exactly at the cutoff begins the new day, and is left consistent with it
 * rather than special-cased; a settings screen offering the two as one control
 * is where the combination should be prevented.
 *
 * Public and here rather than private to the mascot because three callers need
 * the same answer: the mood's `nearBoundary`, the data layer's ticker that
 * re-reads the mood when the threshold passes, and the WorkManager reminder
 * when it lands. A second copy of this shift is how the worried face and the
 * notification would come to fire at different hours.
 */
fun reminderOn(today: LocalDate, reminderTime: LocalTime, dayCutoff: LocalTime): LocalDateTime {
    val date = if (reminderTime < dayCutoff) today.plusDays(1) else today
    return LocalDateTime.of(date, reminderTime)
}
