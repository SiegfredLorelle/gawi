package com.gawi.app.navigation

import kotlinx.serialization.Serializable

/**
 * Every place the app can be.
 *
 * Type-safe routes: each destination is a `@Serializable` class rather than a
 * string template, so an argument that changes shape is a compile error instead
 * of a null at runtime. kotlinx-serialization was already a project dependency
 * for the event log's wire format, which is what makes this the cheap option.
 *
 * Namespaced inside one interface rather than declared as six top-level types,
 * because the feature modules already own the names — `TodayRoute`,
 * `HabitListRoute`, `HabitEditorRoute`, `HabitDetailRoute`, `HistoryRoute` — and
 * a route called `Today` beside a composable called `TodayRoute` reads as though
 * one were the other.
 *
 * This file is the whole vocabulary. Architecture §2 gives `:app` the navigation
 * graph, and no feature module has navigation on its classpath, so a screen
 * cannot route itself somewhere — it reports what happened and this decides.
 */
@Serializable
internal sealed interface Destination {

    /** The home screen (PRD §6.2), and the start destination. */
    @Serializable
    data object Today : Destination

    /** Managing habits: the list, with archived ones reachable. */
    @Serializable
    data object Habits : Destination

    /** The three device-local preferences (PRD §5, architecture §2). */
    @Serializable
    data object Settings : Destination

    /**
     * The editor. A null [habitId] creates; anything else edits that habit.
     *
     * A `String` rather than a `HabitId`, because `HabitId` rejects a
     * non-canonical UUIDv7 by throwing and a route argument is exactly where an
     * unexpected value can arrive. It is validated inside the editor's
     * ViewModel, which turns a bad route into a state the screen can draw.
     */
    @Serializable
    data class HabitEditor(val habitId: String? = null) : Destination

    /**
     * One habit, read-only: its streak, its schedule, and where it stands today
     * (PRD §6.6).
     *
     * [habitId] is non-null, unlike [HabitEditor]'s: there is no "new habit"
     * detail. Still a `String`, and for the same reason — validated inside the
     * detail ViewModel rather than on the way to it.
     */
    @Serializable
    data class HabitDetail(val habitId: String) : Destination

    /**
     * One habit's full history — the month grid (docs/ux/insights.md §2).
     *
     * A destination of its own rather than a section of [HabitDetail], because
     * it is a different module's screen: architecture §2 gives the heatmap to
     * `:feature:insights` and uses this very door as the reason it can live
     * there. Habit detail reports that the user wants the history, and this file
     * is where that becomes a place.
     *
     * Which month is being looked at is deliberately **not** an argument. It is
     * the screen's own state, so stepping through months does not push a
     * back-stack entry and Back leaves the history rather than unwinding it a
     * month at a time.
     *
     * [habitId] is a `String` for the reason the two above give: `HabitId`
     * throws on anything that is not a canonical UUIDv7, and it is validated
     * inside the history ViewModel rather than on the way to it.
     */
    @Serializable
    data class HabitHistory(val habitId: String) : Destination
}
