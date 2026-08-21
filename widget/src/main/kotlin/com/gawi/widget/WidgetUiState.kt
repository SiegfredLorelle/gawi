package com.gawi.widget

import com.gawi.core.data.model.TodaySnapshot

/**
 * One habit as the widget draws it: a name and whether today's cell is ticked.
 *
 * The id is a plain [String], not a [com.gawi.core.domain.model.HabitId], and
 * that is deliberate rather than lazy. It travels to the tap callback through
 * `ActionParameters`, which holds only bundle-able types — and `HabitId`'s
 * constructor *throws* on anything that is not a canonical UUIDv7, so building
 * one from a parameter would move that throw into a broadcast receiver. The
 * callback matches this string against the log instead, so a malformed
 * parameter is a habit that does not exist rather than an exception.
 *
 * No streak. PRD OQ-5 is settled minimal (docs/ux/widget.md §2), and leaving the
 * field out of the type is what keeps that decision from being undone by an
 * accident of what was in scope.
 */
internal data class WidgetRow(val habitId: String, val name: String, val completed: Boolean)

/**
 * Everything the widget draws, and nothing else.
 *
 * Carries no logical date on purpose. A widget's render is a snapshot rather
 * than a live `Flow` (see [TodayWidget]), so a date held here would be the one
 * thing on screen guaranteed to go stale — and the tap path deliberately does
 * not trust it (see `toggleHabit`).
 */
internal data class WidgetUiState(val rows: List<WidgetRow>)

/**
 * The whole of the widget's read logic, as a pure function so it is tested
 * without Glance, a device or a Robolectric shadow.
 *
 * A straight projection of the snapshot, in its order, including habits that are
 * already done. Two consequences, both wanted: a completed row can be tapped
 * again to undo it, and the widget has no rule of its own about which habits are
 * worth showing — it shows what the Today screen shows. `observeToday()` has
 * already dropped archived habits, so nothing here filters.
 */
internal fun TodaySnapshot.toWidgetState(): WidgetUiState =
    WidgetUiState(rows = habits.map { WidgetRow(habitId = it.habit.id.value, name = it.habit.name, completed = it.completedToday) })
