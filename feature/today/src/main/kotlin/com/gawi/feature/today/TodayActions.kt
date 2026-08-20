package com.gawi.feature.today

import com.gawi.core.domain.model.HabitId
import java.time.LocalDate

/**
 * What the Today screen can do, as one parameter.
 *
 * A holder rather than four lambdas in the signature, for the same reason
 * [HabitRowUi] is a model rather than eight arguments: it keeps the composable
 * inside detekt's parameter limit, and adding an action does not re-thread every
 * preview and test.
 *
 * [onToggle] carries the row's own id, state and date rather than reading any of
 * them back, so what is transmitted is the intent the tap expressed.
 */
internal data class TodayActions(
    val onToggle: (HabitId, Boolean, LocalDate) -> Unit,
    /** Straight to the create form — the empty state's call to action. */
    val onAddHabit: () -> Unit,
    /** To the habit list, where editing and archiving live. */
    val onManageHabits: () -> Unit,
    /** To the three preferences that decide how a day and a week are counted. */
    val onOpenSettings: () -> Unit,
)
