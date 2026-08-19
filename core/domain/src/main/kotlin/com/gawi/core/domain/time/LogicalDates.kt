package com.gawi.core.domain.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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
 * misbucket the days around New Year. Shared by the streak calculators and
 * the mascot's mood rules so the two cannot disagree about where a week
 * begins.
 */
internal fun weekStartOn(date: LocalDate, weekStart: DayOfWeek): LocalDate = date.with(TemporalAdjusters.previousOrSame(weekStart))
