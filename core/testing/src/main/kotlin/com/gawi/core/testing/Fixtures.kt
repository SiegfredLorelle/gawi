package com.gawi.core.testing

import com.gawi.core.data.model.HabitDetail
import com.gawi.core.data.model.TodayHabit
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.testing.habitId
import com.gawi.core.ui.theme.HabitPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/*
 * Habits, rows and snapshots as the repository would hand them over, for every
 * module's tests. Ids and events come from :core:domain's fixtures, which this
 * module re-exports.
 */

/**
 * The logical date tests read against. Deliberately not today's real date:
 * an assertion about which day a tap writes to is only worth something if
 * `LocalDate.now()` would give a different answer.
 *
 * **A Tuesday in a month that starts on a Saturday**, and both halves are load
 * bearing. The habit detail strip runs Fri 14 to Tue 18, so Sat 15 is the
 * oldest open day and Fri 14 the one drawn shut (docs/ux/today-view.md §5's
 * worked example turns on the weekday too). For the history grid the weekday
 * of the 1st is what the column arithmetic turns on: a Saturday gives five
 * leading blanks on a Monday-start week and six on a Sunday-start one, so a
 * mapper that ignored the setting could not pass both. August 2026 has 31
 * days, so the grid is six rows and the trailing padding is not zero either.
 */
val FIXED_DATE: LocalDate = LocalDate.parse("2026-08-18")

/** The month [FIXED_DATE] falls in — what the history screen opens on. */
val THIS_MONTH: YearMonth = YearMonth.from(FIXED_DATE)

/** A day in [FIXED_DATE]'s month, by day of month. */
fun thisMonth(dayOfMonth: Int): LocalDate = THIS_MONTH.atDay(dayOfMonth)

/** [FIXED_DATE] minus [back] days, for naming strip cells by their distance from today. */
fun daysAgo(back: Long): LocalDate = FIXED_DATE.minusDays(back)

/**
 * Suppressed at the declaration: a fixture builder's parameters are its whole
 * point. Every one is defaulted, so a test names only the field it is about.
 *
 * **[color] defaults to a colour the editor still offers, and that is a rule
 * rather than a detail.** A literal here can be orphaned by a hue retune, at
 * which point an editor rendered from the default would quietly grow the
 * leading "current colour" swatch that only an orphaned hex is supposed to
 * produce, and a test written from the default would exercise that path
 * without saying so. A test that wants an orphan names one.
 *
 * [createdOn] defaults to null, "the log has not said": the one value that
 * makes a rate clip nothing, so a test that is not about the habit's age gets
 * the whole window it asked for.
 */
@Suppress("LongParameterList")
fun habitState(
    id: HabitId = habitId(1),
    name: String = "read",
    icon: String = "📖",
    color: String = HabitPalette.DefaultColor,
    schedule: Schedule = Schedule.Daily,
    tag: String? = null,
    archived: Boolean = false,
    createdOn: LocalDate? = null,
): HabitState = HabitState(
    id = id,
    name = name,
    icon = icon,
    color = color,
    schedule = schedule,
    tag = tag,
    archived = archived,
    createdOn = createdOn,
)

/**
 * A row as Today, the widget and habit detail read it, from a [HabitState] a
 * test already has. The other overload builds the state from flat fields.
 */
fun todayHabit(
    habit: HabitState,
    completedToday: Boolean = false,
    note: String? = null,
    weekCount: Int = 0,
    streak: StreakSnapshot = StreakSnapshot.NONE,
): TodayHabit = TodayHabit(
    habit = habit,
    completedToday = completedToday,
    note = note,
    weekCount = weekCount,
    streak = streak,
)

/**
 * A row from flat fields, for a test that needs only a name and a state.
 * Suppressed at the declaration for the reason [habitState] gives.
 */
@Suppress("LongParameterList")
fun todayHabit(
    id: HabitId = habitId(1),
    name: String = "read",
    schedule: Schedule = Schedule.Daily,
    archived: Boolean = false,
    completedToday: Boolean = false,
    note: String? = null,
    weekCount: Int = 0,
    streak: StreakSnapshot = StreakSnapshot.NONE,
): TodayHabit = todayHabit(
    habit = habitState(id = id, name = name, schedule = schedule, archived = archived),
    completedToday = completedToday,
    note = note,
    weekCount = weekCount,
    streak = streak,
)

/** A live run of [current], in whatever unit the habit's schedule counts in. */
fun running(current: Int): StreakSnapshot = StreakSnapshot(current = current, previous = 0, brokenOn = null)

/** A run of [previous] that has since been lost, and reads zero as of [brokenOn]. */
fun broken(previous: Int, brokenOn: LocalDate = LocalDate.parse("2026-08-16")): StreakSnapshot =
    StreakSnapshot(current = 0, previous = previous, brokenOn = brokenOn)

/**
 * What `observeHabitDetail` returns. [today] defaults to [FIXED_DATE] and
 * [recent] to nothing completed, so a test about the header says nothing about
 * the strip and vice versa.
 */
fun habitDetail(
    habit: TodayHabit = todayHabit(),
    today: LocalDate = FIXED_DATE,
    recent: Map<LocalDate, String?> = emptyMap(),
): HabitDetail = HabitDetail(habit = habit, today = today, recent = recent)

/** Suppressed at the declaration for the reason [habitState] gives. */
@Suppress("LongParameterList")
fun todaySnapshot(
    habits: List<TodayHabit> = emptyList(),
    today: LocalDate = FIXED_DATE,
    now: LocalDateTime = today.atTime(9, 0),
    reminderTime: LocalTime = LocalTime.of(21, 0),
    dayCutoff: LocalTime = LocalTime.MIDNIGHT,
    weekStart: DayOfWeek = DayOfWeek.MONDAY,
): TodaySnapshot = TodaySnapshot(habits, today, now, reminderTime, dayCutoff, weekStart)
