package com.gawi.feature.habits

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.streak.StreakUi
import java.time.LocalDate

/**
 * What habit detail draws.
 *
 * Three branches rather than the four the list and Today use: there is no
 * [HabitListUiState.Empty] equivalent, because a detail screen is always about
 * exactly one habit. An id that resolves to nothing is [Unavailable], the same
 * state a failed read lands on and for the same reason `HabitEditorUiState`
 * gives: with no delete in the event model, an id that resolves to nothing
 * means the id was wrong rather than the habit having gone, and there is
 * nothing different for the user to do about either.
 *
 * This is the screen PRD §6.6 means by "habit detail" — one of the two surfaces
 * a streak is read deliberately on, the widget having been settled as minimal
 * (docs/ux/widget.md §2).
 *
 * Unlike [HabitListRowUi] this carries completion state, week progress and a
 * streak: detail is for looking at a habit you are doing, not for changing what
 * it is. Editing is one tap away and lives on the screen that owns it.
 */
internal sealed interface HabitDetailUiState {

    data object Loading : HabitDetailUiState

    data object Unavailable : HabitDetailUiState

    data class Detail(
        val id: HabitId,
        val name: String,
        val icon: String,
        /** Null when the stored colour does not parse; the header falls back to a theme role. */
        val iconTint: Color?,
        val schedule: ScheduleUi,
        /** Null rather than blank — the header draws nothing at all for an untagged habit. */
        val tag: String?,
        val archived: Boolean,
        val completedToday: Boolean,
        /** Non-null only for a weekly schedule, matching the Today row's rule. */
        val weekProgress: HabitWeekProgress?,
        val streak: StreakUi,
        /** Oldest first, ending on today. See [RetroCellUi]. */
        val strip: List<RetroCellUi>,
    ) : HabitDetailUiState
}

/**
 * One day in the retro strip.
 *
 * [open] is the whole rule made visible. docs/ux/today-view.md §5: "days
 * outside the retro window are drawn shut, not tapped and refused… the command
 * rule should be readable before it is hit". So the oldest cell is drawn and
 * struck through rather than left off, and carries no click at all — a tap that
 * produced a snackbar would be exactly the refusal §5 is arguing against.
 *
 * Decided in the mapper against the date the repository read for, never against
 * a date resolved on this side. [completed] survives on a shut day: a refused
 * day still reports whether it was done.
 *
 * [note] is the note showing on the cell, null when there is none. A cell that
 * is not [completed] can have no note — notes die with the completion they hang
 * off (architecture §4).
 */
internal data class RetroCellUi(
    val date: LocalDate,
    /** The weekday's short label, resolved from resources rather than the locale-free enum. */
    @StringRes val dayLabel: Int,
    val dayOfMonth: Int,
    val completed: Boolean,
    val note: String?,
    /** False for the day drawn shut: outside the retro window, so no tap is legal. */
    val open: Boolean,
    /** Today's cell writes with no confirmation — PRD §6.4's frictionless same-day undo. */
    val isToday: Boolean,
)

/**
 * A weekly habit's progress through its own week.
 *
 * Named apart from `:feature:today`'s `WeekProgress` only because feature
 * modules cannot see each other's types. Unlike [StreakUi], which moved to
 * `:core:ui` because the days-versus-weeks rule has to be one rule, this is two
 * plain ints and carries no rule to keep in step.
 */
internal data class HabitWeekProgress(val done: Int, val target: Int)
