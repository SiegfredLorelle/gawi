package com.gawi.feature.today

import androidx.compose.ui.graphics.Color
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.streak.StreakUi
import java.time.LocalDate

/**
 * What the Today screen draws, and nothing it has to work out for itself.
 *
 * [Empty] is a state rather than [Habits] with an empty list. A `LazyColumn`
 * over no rows draws a blank screen, and docs/ux/today-view.md §4 makes zero
 * habits load-bearing rather than incidental — its rule 0 exists so a first run
 * is not greeted as thriving. Conflating the two here would be the same mistake
 * the mood rules refuse to make.
 *
 * [Loading] is not a spinner. The first emission is one Room query, so anything
 * animated would be a flash; it exists so the screen does not claim there are
 * no habits before it has looked.
 *
 * [Unavailable] is the read path failing, which it can: a fresh install repairs
 * the projection on its first read, and that repair asks the settings store for
 * an answer it refuses to guess at when the file cannot be read. Without a state
 * for it the exception would leave the ViewModel's sharing coroutine and take
 * the process down on the app's only screen.
 *
 * Internal throughout. These types carry `Color`, `Mood` and `HabitId`, all of
 * which arrive on implementation-scope dependencies, so exposing them would
 * publish a surface no consumer could compile against. [TodayRoute] is this
 * module's whole API.
 */
internal sealed interface TodayUiState {

    data object Loading : TodayUiState

    data object Unavailable : TodayUiState

    data class Empty(val mood: Mood) : TodayUiState

    data class Habits(
        val rows: List<HabitRowUi>,
        val mood: Mood,
        /** Outstanding right now, by §4's rule — not simply "not ticked". */
        val remaining: Int,
        /** The date a tap writes to: the one these rows were queried for. */
        val logicalDate: LocalDate,
    ) : TodayUiState
}

/**
 * One row. A model rather than eight parameters, so the row composable stays
 * inside detekt's parameter limit and the display rules are asserted on the
 * JVM instead of through pixels.
 *
 * [note] is deliberately absent, and stays so now that the note sheet exists:
 * it lives on habit detail (PRD §5's "long-press / detail view"), and §6.3 wants
 * notes kept out of the base flow. A field this row never renders would only
 * invite rendering half of it.
 */
internal data class HabitRowUi(
    val id: HabitId,
    val name: String,
    val icon: String,
    /** Null when the stored colour does not parse; the row falls back to a theme role. */
    val iconTint: Color?,
    val completed: Boolean,
    /** Non-null only for a weekly schedule — §5 draws "2/3 this week" for those alone. */
    val weekProgress: WeekProgress?,
    val streak: StreakUi,
)

/** A weekly habit's progress through its own week. */
internal data class WeekProgress(val done: Int, val target: Int)
