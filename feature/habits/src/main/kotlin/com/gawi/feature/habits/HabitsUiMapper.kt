package com.gawi.feature.habits

import com.gawi.core.domain.projection.HabitState
import com.gawi.core.ui.theme.HabitPalette
import com.gawi.core.ui.theme.parseHabitColor

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
)
