package com.gawi.feature.habits

import android.util.Log
import androidx.annotation.StringRes
import com.gawi.core.domain.command.CommandError
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
 * Every rejection this module's commands can produce.
 *
 * All six are reachable since habit detail's retro strip landed. `BlankName`
 * comes from create and update and `HabitNotFound` from update, archive and
 * unarchive; the other four are completion errors, which this module used to be
 * unable to produce and now can. The `when` is exhaustive rather than defaulted
 * so that adding a seventh error is a compile error and not a silent fallback.
 *
 * `RetroWindowExceeded` should be unreachable *in practice* rather than in
 * principle: the strip draws the day outside the window shut and gives it no
 * click at all (docs/ux/today-view.md §5). It still gets real copy, because the
 * day can roll over between the strip being drawn and a tap landing on it, and
 * a stale cell refused in silence would look like a tap that did nothing.
 *
 * `FutureLogicalDate` is genuinely unreachable — the strip ends at today — but
 * shares the same copy rather than pretending to be impossible.
 *
 * Note what is deliberately absent: there is no "already archived" error.
 * Archiving an archived habit is accepted and converges under last-write-wins,
 * so the list never has to guard the action it offers.
 */
@StringRes
internal fun messageFor(error: CommandError): Int = when (error) {
    CommandError.BlankName -> R.string.habits_error_blank_name

    CommandError.HabitNotFound -> R.string.habits_error_habit_missing

    CommandError.HabitIsArchived -> R.string.habits_error_archived

    CommandError.RetroWindowExceeded,
    CommandError.FutureLogicalDate,
    -> R.string.habits_error_retro_window

    CommandError.CompletionNotFound -> R.string.habits_error_completion_missing
}

/**
 * Runs a write command, turning anything it throws into a `null`.
 *
 * Rejections are values, which is exactly what makes the remaining exceptions
 * worth catching. What is left to throw is the real failures the repository
 * documents: an unreadable settings store — `SettingsSource.current()` refuses
 * to guess a cutoff rather than serve a plausible default, and
 * `appendLocked` consults it on **every** write — a corrupt log
 * (`EventCodecException`), and SQLite itself.
 *
 * Without this they escape. `viewModelScope` is a `SupervisorJob` with no
 * `CoroutineExceptionHandler`, so an exception out of a `launch` reaches the
 * thread's default handler: process death on a Save or an Archive tap rather
 * than a snackbar. Both read paths in this module are `.catch`-guarded for
 * precisely this failure mode; the write paths were not, which was the whole
 * asymmetry.
 *
 * Returns `null` rather than a `Result`, because the caller has nothing to do
 * with the throwable beyond what has already been logged — it maps to one
 * message either way.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> commandOrNull(tag: String, command: suspend () -> T): T? = try {
    command()
} catch (failure: Exception) {
    // Cancellation is not a failure, and must not be reported as one:
    // ensureActive() rethrows it. Doing it this way rather than with a bare
    // `throw failure` also keeps detekt's RethrowCaughtException quiet.
    currentCoroutineContext().ensureActive()
    Log.e(tag, "the command failed", failure)
    null
}
