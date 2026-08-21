package com.gawi.feature.habits

import com.gawi.core.domain.model.HabitId
import java.time.LocalDate

/**
 * What each habits screen can do, as one parameter apiece.
 *
 * Holders rather than loose lambdas in the signatures, for the same reason
 * `HabitListRowUi` is a model rather than six arguments: they keep the
 * composables inside detekt's parameter limit, and adding an action does not
 * re-thread every call site, preview and test.
 *
 * All three live here rather than beside their screens so that no screen file
 * has a lone top-level class in it — which is also what detekt's
 * MatchingDeclarationName asks for.
 */
internal data class HabitListActions(
    val onAdd: () -> Unit,
    /**
     * Opens the habit. Detail rather than the editor since 2026-08-21
     * (docs/ux/habits.md §6): detail is the hub, and it carries an Edit action
     * of its own for the case this used to serve.
     */
    val onOpen: (HabitId) -> Unit,
    val onArchiveToggle: (HabitId, Boolean) -> Unit,
    val onBack: () -> Unit,
)

/**
 * Detail's actions: two navigations and one write.
 *
 * [onToggle] carries the cell's own date and the state it was drawn in, the
 * same rule the Today row follows — what is transmitted is the intent the cell
 * expressed, not a date or a state read back a moment later. That matters more
 * here than anywhere: the 3-day window *accepts* a date one day stale, so a
 * re-read date would write to the wrong day silently.
 *
 * The honesty prompt is not modelled here. It is friction on the way to
 * [onToggle] and lives in the screen, because architecture §5 is explicit that
 * the retro window is a command validation and the confirmation is UI only.
 */
internal data class HabitDetailActions(
    val onEdit: (HabitId) -> Unit,
    val onToggle: (HabitId, LocalDate, Boolean) -> Unit,
    /** Writes the note on one completed day. Empty text clears it, and that is a real write. */
    val onNote: (HabitId, LocalDate, String) -> Unit,
    val onBack: () -> Unit,
)

internal data class HabitEditorActions(val onEdit: (HabitEditorUiState.Form) -> Unit, val onSave: () -> Unit, val onCancel: () -> Unit)
