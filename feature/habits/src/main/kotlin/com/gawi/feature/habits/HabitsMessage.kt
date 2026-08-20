package com.gawi.feature.habits

import androidx.annotation.StringRes
import com.gawi.core.domain.command.CommandError

/**
 * A rejection worth telling the user about, once.
 *
 * Carried as a string resource id rather than as text, so the ViewModel stays
 * free of a `Context` and the Route resolves it against the current
 * configuration. Same shape as `:feature:today`'s `TodayMessage`, and
 * deliberately not shared with it: the two modules map different halves of
 * `CommandError` and each one's mapping is a statement about its own screen.
 */
internal data class HabitsMessage(@StringRes val text: Int)

/**
 * Every rejection a habit-metadata command can produce.
 *
 * The first two are the reachable ones. `BlankName` comes from create and
 * update, and `HabitNotFound` from update, archive and unarchive. The other
 * four are completion errors — this module writes no completions, so they
 * cannot arrive here, but the `when` is exhaustive rather than defaulted so
 * that adding a seventh error is a compile error and not a silent fallback.
 *
 * Note what is deliberately absent: there is no "already archived" error.
 * Archiving an archived habit is accepted and converges under last-write-wins,
 * so the list never has to guard the action it offers.
 */
@StringRes
internal fun messageFor(error: CommandError): Int = when (error) {
    CommandError.BlankName -> R.string.habits_error_blank_name

    CommandError.HabitNotFound -> R.string.habits_error_habit_missing

    CommandError.HabitIsArchived,
    CommandError.RetroWindowExceeded,
    CommandError.FutureLogicalDate,
    CommandError.CompletionNotFound,
    -> R.string.habits_error_unexpected
}
