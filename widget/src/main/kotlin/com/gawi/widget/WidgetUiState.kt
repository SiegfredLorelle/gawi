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
 * What the widget has to draw right now.
 *
 * Three states rather than a nullable [WidgetUiState], because "not read yet"
 * and "could not be read" have to look different on a widget. Collapsing them
 * would flash the failure copy on every cold render, and drawing an empty list
 * for a broken database is the failure-towards-silence the export nudge took
 * three review rounds to stamp out.
 */
internal sealed interface WidgetContent {

    /** Before the first emission arrives. */
    data object Loading : WidgetContent

    /** The read threw. `SQLiteException` is a `RuntimeException`, and the
     *  settings store refuses to guess a cutoff, so neither is hypothetical. */
    data object Unavailable : WidgetContent

    data class Ready(val state: WidgetUiState) : WidgetContent
}

/**
 * Everything the widget draws, and nothing else.
 *
 * Carries no logical date on purpose. The content *is* backed by a collected
 * flow ([TodayWidget] explains why it has to be), but only while a Glance
 * session is alive — and a session is short-lived, so a widget sitting on a
 * launcher is usually not collecting anything. A date held here would therefore
 * be the one value on screen most likely to be stale, and the tap path
 * deliberately does not trust it (see `toggleHabit`).
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
