package com.gawi.core.domain.mascot

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * One habit, as the mood rules need to see it: what it is due to do, whether
 * that is done, and whether its streak just broke.
 *
 * docs/ux/today-view.md §4 describes the mood as a function of "the projected
 * state", which was written before the read model existed. Taking that
 * literally now would mean re-deriving every habit's streak and week count
 * inside the mood function, duplicating work the data layer has already done
 * and cached — and it would put the function out of reach of the UI, which
 * holds rows and not a `ProjectedState`. So the input is this row instead,
 * which the data layer's `TodayHabit` maps onto field for field with no
 * recomputation. It is still a pure function of a projection of the projected
 * state.
 *
 * [archived] is carried rather than assumed. Rule 0 of the precedence table is
 * about non-archived habits, and enforcing that here is what stops a caller
 * that legitimately sees archived habits — the detail screen does — from
 * breaking the rule by accident.
 *
 * [completionsThisWeek] is counted in the week [MoodInputs.weekStart] begins,
 * and [completedToday] against the logical date, so both already answer "as of
 * when" and neither needs the completion set.
 *
 * [completionsThisWeek] **includes today's completion.** The read model counts
 * it that way and a test pins it, but the rules read both fields together — the
 * weekly branch of [Mascot.isOutstanding] subtracts this count *and* checks
 * [completedToday] — so a producer that excluded today would shift every weekly
 * now-or-never threshold by a day without failing a domain test.
 */
data class HabitMoodState(
    val schedule: Schedule,
    val archived: Boolean,
    val completedToday: Boolean,
    val completionsThisWeek: Int,
    val streak: StreakSnapshot,
)

/**
 * Everything [Mascot.mood] reads: the habits, the logical date they were read
 * for, the wall clock, and the settings the rules threshold against.
 *
 * One bundle rather than six parameters. Partly because six is where detekt
 * stops believing a parameter list, but mostly because these six travel
 * together and are assembled in one place — the data layer, from its clock and
 * its `UserSettings`.
 *
 * Not `UserSettings` itself: that type lives in `:core:data`, which this module
 * may not see, and the dependency rule is not negotiable (architecture §2). The
 * three fields it lends are copied in, and the mapping is the data layer's job.
 *
 * [now] is wall-clock time in the device's own zone, so the zone is already
 * resolved and this module needs no `ZoneId`. [today] is passed rather than
 * derived from [now] and [dayCutoff] so the mood is decided for exactly the
 * logical date whose rows are in [habits]; deriving it again here could
 * disagree with them across a boundary.
 */
data class MoodInputs(
    val habits: List<HabitMoodState>,
    val today: LocalDate,
    val now: LocalDateTime,
    val reminderTime: LocalTime,
    val dayCutoff: LocalTime = LocalTime.MIDNIGHT,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
)
