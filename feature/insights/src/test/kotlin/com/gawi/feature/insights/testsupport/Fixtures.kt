package com.gawi.feature.insights.testsupport

import com.gawi.core.data.model.HabitDetail
import com.gawi.core.data.model.TodayHabit
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.ui.theme.HabitPalette
import java.time.LocalDate
import java.time.YearMonth

/**
 * Habits as the repository would hand them over.
 *
 * Named after the `Fixtures.kt` the other modules use and equally deliberately
 * not shared: this one builds only what a history grid reads, which is a name, a
 * logical date and a set of completed days. No streak, no week count and no
 * schedule variation, because the grid draws the same two-state cells for both
 * schedules (docs/ux/insights.md §4) — a fixture that varied the schedule would
 * imply the screen looked at it.
 */
fun habitId(n: Int): HabitId = HabitId("00000000-0000-7000-8000-" + n.toString(16).padStart(12, '0'))

/**
 * The logical date these tests read against.
 *
 * **A Tuesday in a month that starts on a Saturday**, and both halves are load
 * bearing. The weekday of the 1st is what the column arithmetic turns on, and a
 * Saturday gives five leading blanks on a Monday-start week and six on a
 * Sunday-start one — two different answers, so a mapper that ignored the setting
 * could not pass both. Sitting mid-month leaves finished days on one side of
 * today and unstarted ones on the other, which is the other thing the grid has
 * to get right.
 *
 * August 2026 has 31 days, so the grid is six rows and the trailing padding is
 * not zero either.
 */
val TODAY: LocalDate = LocalDate.parse("2026-08-18")

/** The month [TODAY] falls in — what the screen opens on. */
val THIS_MONTH: YearMonth = YearMonth.from(TODAY)

fun habitState(id: HabitId = habitId(1), name: String = "read", schedule: Schedule = Schedule.Daily): HabitState = HabitState(
    id = id,
    name = name,
    icon = "📖",
    // A colour the editor still offers, for the reason :feature:habits' copy of
    // this file records: the retune turned the old literal into an orphaned hex,
    // and a fixture that quietly exercised the orphan path would say so nowhere.
    color = HabitPalette.DefaultColor,
    schedule = schedule,
    tag = null,
    archived = false,
)

/**
 * What nests inside a [HabitDetail].
 *
 * Every field but the habit is fixed at its empty value. The grid reads none of
 * them — not the completion, not the week count and not the streak — so varying
 * one here would suggest it did.
 */
fun todayHabit(habit: HabitState = habitState()): TodayHabit = TodayHabit(
    habit = habit,
    completedToday = false,
    note = null,
    weekCount = 0,
    streak = StreakSnapshot.NONE,
)

/**
 * What `observeHabitDetail` returns.
 *
 * `recent` is left empty throughout. It is the strip's five cells and this
 * screen never reads it — the grid is drawn from `observeCompletedDates`, which
 * is a different query over a range the strip cannot express.
 */
fun habitDetail(habit: TodayHabit = todayHabit(), today: LocalDate = TODAY): HabitDetail =
    HabitDetail(habit = habit, today = today, recent = emptyMap())

/** A day in [TODAY]'s month, by day of month. */
fun thisMonth(dayOfMonth: Int): LocalDate = THIS_MONTH.atDay(dayOfMonth)
