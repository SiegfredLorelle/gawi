package com.gawi.feature.habits

import androidx.annotation.StringRes
import com.gawi.core.data.model.HabitDetail
import com.gawi.core.domain.command.Commands
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.ui.streak.toUi
import com.gawi.core.ui.theme.HabitPalette
import com.gawi.core.ui.theme.parseHabitColor
import java.time.DayOfWeek

/**
 * The read model as the habits screens draw it.
 *
 * Here rather than in the composables for the same reason Today's mapper is:
 * these are decisions — which habits are shown where, what an unparseable
 * colour falls back to, what an untouched create form starts as — and a
 * composable can only get a decision wrong in a screenshot.
 */
internal fun List<HabitState>.toListUiState(): HabitListUiState {
    if (isEmpty()) return HabitListUiState.Empty
    // Partitioned here rather than in SQL, so one query feeds both sections and
    // the two cannot be read at different moments.
    val (archived, active) = partition { it.archived }
    return HabitListUiState.Habits(
        active = active.map { it.toRowUi() },
        archived = archived.map { it.toRowUi() },
    )
}

internal fun HabitState.toRowUi(): HabitListRowUi = HabitListRowUi(
    id = id,
    name = name,
    icon = icon,
    iconTint = parseHabitColor(color),
    schedule = schedule.toUi(),
    archived = archived,
)

/** An existing habit, opened for editing. */
internal fun HabitState.toForm(): HabitEditorUiState.Form = HabitEditorUiState.Form(
    editing = true,
    name = name,
    icon = icon,
    color = color,
    schedule = schedule.toUi(),
    // Nullable in the model, a field on screen. The inverse of toMetadata.
    tag = tag.orEmpty(),
    // What the log actually holds, kept so the picker can still offer it.
    originalColor = color,
)

/**
 * A new habit, before anything has been chosen.
 *
 * Starts daily and on the palette's first entries rather than on nothing, so
 * the form is savable the moment a name is typed. An unchosen icon or colour
 * would be a second and third thing to get wrong on the way to a first habit.
 */
internal fun newHabitForm(): HabitEditorUiState.Form = HabitEditorUiState.Form(
    editing = false,
    name = "",
    icon = HabitPalette.DefaultIcon,
    color = HabitPalette.DefaultColor,
    schedule = ScheduleUi.Daily,
    tag = "",
    // Nothing to preserve: a new habit starts on the palette by construction.
    originalColor = null,
)

/**
 * One habit, as detail draws it.
 *
 * Reads a `HabitDetail` rather than a `HabitState`, which is what makes the
 * streak, the week count and the strip available at all — the management list's
 * `observeAllHabits` carries none of them. Detail sees archived habits where the
 * Today list does not, since unarchiving has to stay reachable.
 *
 * The week-progress rule is Today's, deliberately: docs/ux/today-view.md §5
 * says only a weekly habit draws "2/3 this week", and a detail screen that
 * disagreed with the row that led to it would be its own bug.
 */
internal fun HabitDetail.toDetailUiState(): HabitDetailUiState.Detail = HabitDetailUiState.Detail(
    id = habit.habit.id,
    name = habit.habit.name,
    icon = habit.habit.icon,
    iconTint = parseHabitColor(habit.habit.color),
    schedule = habit.habit.schedule.toUi(),
    // Blank to null, the same translation toForm does in the other direction.
    tag = habit.habit.tag?.takeUnless { it.isBlank() },
    archived = habit.habit.archived,
    completedToday = habit.completedToday,
    weekProgress = when (val schedule = habit.habit.schedule) {
        Schedule.Daily -> null
        is Schedule.Weekly -> HabitWeekProgress(done = habit.weekCount, target = schedule.timesPerWeek)
    },
    streak = habit.streak.toUi(habit.habit.schedule),
    strip = toStrip(),
)

/**
 * The retro strip: every day from the oldest drawn to today, in order.
 *
 * Built from the read's own `today` and its own window, never from a date
 * resolved here. `stripStart` is one day older than the oldest writable day, so
 * exactly one cell comes back shut — docs/ux/today-view.md §5 wants the rule
 * readable before it is hit, which needs a refused day on screen.
 *
 * `open` is decided against `Commands.RETRO_WINDOW_DAYS`, the same constant the
 * domain rejects with, so what the strip offers and what a tap is allowed to do
 * cannot drift apart.
 *
 * **An archived habit's cells are all shut**, for the same reason and by the
 * same field. `Commands` rejects every completion write on an archived habit —
 * `addCompletion`, `undoCompletion` and `updateCompletionNote` alike — so a live
 * cell there could only ever answer a tap with a refusal, which is precisely
 * what §5 says not to build. Archiving is undone from the list row
 * (docs/ux/habits.md §6); detail is read-only until it is.
 *
 * A missing key in `recent` is "not completed"; a null value is "completed, no
 * note". Conflating them would draw a cleared note as a missing day.
 */
private fun HabitDetail.toStrip(): List<RetroCellUi> {
    val oldestOpen = today.minusDays(Commands.RETRO_WINDOW_DAYS)
    return generateSequence(stripStart) { day -> day.plusDays(1) }
        .takeWhile { day -> !day.isAfter(today) }
        .map { day ->
            RetroCellUi(
                date = day,
                dayLabel = labelFor(day.dayOfWeek),
                dayOfMonth = day.dayOfMonth,
                completed = recent.containsKey(day),
                note = recent[day],
                open = !day.isBefore(oldestOpen) && !habit.habit.archived,
                isToday = day == today,
            )
        }
        .toList()
}

/**
 * A weekday's short label, from resources rather than `DayOfWeek`.
 *
 * `getDisplayName` would read the JVM's locale rather than the app's resource
 * configuration, so a device set to one language could draw the strip in
 * another. `:feature:settings` resolves its day names the same way and for the
 * same reason.
 */
@StringRes
internal fun labelFor(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.habits_day_mon
    DayOfWeek.TUESDAY -> R.string.habits_day_tue
    DayOfWeek.WEDNESDAY -> R.string.habits_day_wed
    DayOfWeek.THURSDAY -> R.string.habits_day_thu
    DayOfWeek.FRIDAY -> R.string.habits_day_fri
    DayOfWeek.SATURDAY -> R.string.habits_day_sat
    DayOfWeek.SUNDAY -> R.string.habits_day_sun
}
