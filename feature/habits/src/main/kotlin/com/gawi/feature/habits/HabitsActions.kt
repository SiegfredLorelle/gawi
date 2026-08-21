package com.gawi.feature.habits

import com.gawi.core.domain.model.HabitId

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
    val onEdit: (HabitId) -> Unit,
    val onArchiveToggle: (HabitId, Boolean) -> Unit,
    val onBack: () -> Unit,
)

/**
 * Detail's two, both of them navigation.
 *
 * Nothing here writes: the screen is read-only until the retro strip and the
 * note sheet land beside it.
 */
internal data class HabitDetailActions(val onEdit: (HabitId) -> Unit, val onBack: () -> Unit)

internal data class HabitEditorActions(val onEdit: (HabitEditorUiState.Form) -> Unit, val onSave: () -> Unit, val onCancel: () -> Unit)
