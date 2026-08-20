package com.gawi.feature.habits

import com.gawi.core.domain.projection.HabitMetadata

/**
 * What the habit editor draws.
 *
 * One screen for creating and for editing, because `updateHabit` is a
 * whole-record last-write-wins write and not a patch (architecture §3) — an
 * edit therefore submits every field, which is the same form a create submits.
 * [Form.editing] changes the title and the confirm button and nothing else.
 *
 * [Unavailable] covers both halves of "we cannot show you this habit": the read
 * failed, or there is no habit with that id. They are one state because there
 * is nothing different for the user to do about them, and because with no
 * delete in the event model an id that resolves to nothing means the id was
 * wrong rather than the habit having gone.
 *
 * Creating starts at [Form] directly — there is nothing to load.
 */
internal sealed interface HabitEditorUiState {

    data object Loading : HabitEditorUiState

    data object Unavailable : HabitEditorUiState

    /**
     * The form as it currently stands.
     *
     * Held rather than observed after the first read. A form that kept following
     * `observeHabit` would overwrite what someone was in the middle of typing
     * the moment anything else touched the habit.
     *
     * [tag] is a plain String because the field is a String on screen, while
     * `HabitMetadata.tag` is nullable — blank means no tag, and [toMetadata] is
     * where that translation happens once.
     */
    data class Form(
        val editing: Boolean,
        val name: String,
        val icon: String,
        val color: String,
        val schedule: ScheduleUi,
        val tag: String,
    ) : HabitEditorUiState {

        /**
         * A blank name is the only thing the domain rejects about metadata, and
         * the only thing this form can get wrong: the colour and icon are picked
         * from a fixed palette and the weekly target is clamped.
         *
         * Untrimmed, matching `Commands.createHabit`, which tests
         * `name.isBlank()` on the string it is given. Agreeing by construction
         * rather than by luck.
         */
        val canSave: Boolean get() = name.isNotBlank()
    }
}

/** Blank tag to null: an empty field means no tag, not a tag that is empty. */
internal fun HabitEditorUiState.Form.toMetadata(): HabitMetadata = HabitMetadata(
    name = name,
    icon = icon,
    color = color,
    schedule = schedule.toDomain(),
    tag = tag.ifBlank { null },
)
