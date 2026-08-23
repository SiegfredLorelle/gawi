package com.gawi.feature.habits.testsupport

import com.gawi.core.data.model.HabitDetail
import com.gawi.core.data.model.TodayHabit
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.ui.theme.HabitPalette
import java.time.LocalDate

/**
 * Habits as the repository would hand them over. Named after the `Fixtures.kt`
 * the other modules use, and equally deliberately not shared: this one builds
 * `HabitState`, where Today's builds read-model rows and the core modules' build
 * events and commands.
 */
fun habitId(n: Int): HabitId = HabitId("00000000-0000-7000-8000-" + n.toString(16).padStart(12, '0'))

/**
 * The logical date the detail tests read against.
 *
 * A Tuesday, so the strip runs Fri 14 to Tue 18: Sat 15 is the oldest open day
 * and Fri 14 is the one drawn shut. Same shape as docs/ux/today-view.md §5's
 * worked example, which uses a Tuesday the 19th — the weekday is what the
 * example turns on, and the dates here are its own.
 */
val TODAY: LocalDate = LocalDate.parse("2026-08-18")

/**
 * Suppressed at the declaration: a fixture builder's parameters are its whole
 * point. Every one is defaulted, so a test names only the field it is about.
 *
 * **[color] defaults to a colour the editor still offers, and that is a rule
 * rather than a detail.** It used to be the literal `"#7E57C2"`, which the hue
 * retune turned into a colour `HabitPalette` no longer lists — so an editor
 * rendered from this default would quietly grow the leading "current colour"
 * swatch that only an orphaned hex is supposed to produce, and a test written
 * from the default would be exercising that path without saying so. Nothing
 * renders from the default today, so this is a trap rather than a defect; the
 * symbolic value keeps it shut through the next retune too, which the literal
 * did not. A test that wants an orphan names one, as `HabitEditorScreenTest`
 * does.
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
): HabitState = HabitState(
    id = id,
    name = name,
    icon = icon,
    color = color,
    schedule = schedule,
    tag = tag,
    archived = archived,
)

/**
 * What `observeHabit` returns, and what nests inside a [HabitDetail].
 *
 * The editor reads only its `habit`; detail reads all of it, which is why the
 * completion, week and streak fields are parameters rather than fixed zeroes.
 */
fun todayHabit(
    habit: HabitState = habitState(),
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

/** A live run of [current], in whatever unit the habit's schedule counts in. */
fun running(current: Int): StreakSnapshot = StreakSnapshot(current = current, previous = 0, brokenOn = null)

/** A run of [previous] that has since been lost, and reads zero as of [brokenOn]. */
fun broken(previous: Int, brokenOn: LocalDate = LocalDate.parse("2026-08-16")): StreakSnapshot =
    StreakSnapshot(current = 0, previous = previous, brokenOn = brokenOn)

/**
 * What `observeHabitDetail` returns.
 *
 * [today] defaults to [TODAY] and [recent] to nothing completed, so a test that
 * is about the header says nothing about the strip and vice versa.
 */
fun habitDetail(habit: TodayHabit = todayHabit(), today: LocalDate = TODAY, recent: Map<LocalDate, String?> = emptyMap()): HabitDetail =
    HabitDetail(habit = habit, today = today, recent = recent)

/** [TODAY] minus [back] days, for naming strip cells by their distance from today. */
fun daysAgo(back: Long): LocalDate = TODAY.minusDays(back)
