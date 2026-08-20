package com.gawi.feature.habits

import androidx.compose.ui.graphics.Color
import com.gawi.core.domain.model.HabitId

/**
 * What the habit list draws.
 *
 * The same four-branch shape `:feature:today` uses, for the same reasons.
 * [Empty] is a state rather than [Habits] with two empty lists, because a first
 * run wants a sentence and not a blank screen. [Loading] is not a spinner — the
 * first emission is one Room query — and [Unavailable] exists because the read
 * path can genuinely fail on a fresh install, where the first read repairs the
 * projection and asks the settings store for an answer it refuses to guess at.
 *
 * Active and archived habits arrive as two lists rather than one list with a
 * flag, so the screen cannot accidentally draw them in one run and offer the
 * wrong action on half of it. Both are drawn: there is no show-archived toggle,
 * because a habit you have put away still has to be findable to bring back, and
 * a section heading says that with no state to keep.
 *
 * Internal throughout, like Today's: these types carry `Color` and `HabitId`,
 * which arrive on implementation-scope dependencies. The two Route composables
 * are this module's whole API.
 */
internal sealed interface HabitListUiState {

    data object Loading : HabitListUiState

    data object Unavailable : HabitListUiState

    data object Empty : HabitListUiState

    data class Habits(val active: List<HabitListRowUi>, val archived: List<HabitListRowUi>) : HabitListUiState
}

/**
 * One habit as the management list draws it.
 *
 * No completion state, no week progress and no streak: this screen is for
 * changing a habit, not for doing it. `observeAllHabits` does not read them
 * either, so there is nothing here to leave stale.
 */
internal data class HabitListRowUi(
    val id: HabitId,
    val name: String,
    val icon: String,
    /** Null when the stored colour does not parse; the row falls back to a theme role. */
    val iconTint: Color?,
    val schedule: ScheduleUi,
    val archived: Boolean,
)
