package com.gawi.feature.settings

import android.util.Log
import androidx.annotation.StringRes
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * A failed write worth telling the user about, once.
 *
 * Carried as a string resource id rather than as text, so the ViewModel stays
 * free of a `Context` and the Route resolves it against the current
 * configuration. Same shape as the other two modules' message types, and
 * deliberately not shared with them.
 *
 * There is only ever one message here, and that is worth saying out loud rather
 * than leaving as an accident of the current copy. The habits and today modules
 * map `CommandError` because their writes are commands, which model refusal as
 * a value. `SettingsSource.update` is not a command: it validates nothing and
 * refuses nothing, because a fixed picker cannot express an invalid time or a
 * day that is not a day. So the only way a settings write fails is by throwing,
 * and every throw reads the same to the user.
 */
internal data class SettingsMessage(@StringRes val text: Int)

/**
 * Runs a settings write, turning anything it throws into a `null`.
 *
 * A third copy of the same guard `:feature:habits` and `:feature:today` carry,
 * for the reason `TodayViewModel` records: feature modules do not depend on one
 * another, and `:core:ui` is for composables rather than coroutine utilities.
 *
 * It is needed here for the same reason it was needed there, arrived at from
 * the other end. `DataStore.edit` reads before it writes, so an unreadable or
 * unwritable preferences file throws out of `update` — and `viewModelScope` is
 * a `SupervisorJob` with no `CoroutineExceptionHandler`, so an exception out of
 * a `launch` reaches the thread's default handler. That is process death on
 * tapping a time, rather than a snackbar. The read path is `.catch`-guarded;
 * the write path needs this.
 *
 * Returns `null` rather than a `Result`, because the caller has nothing to do
 * with the throwable beyond what has already been logged.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> commandOrNull(tag: String, command: suspend () -> T): T? = try {
    command()
} catch (failure: Exception) {
    // Cancellation is not a failure, and must not be reported as one:
    // ensureActive() rethrows it. Doing it this way rather than with a bare
    // `throw failure` also keeps detekt's RethrowCaughtException quiet.
    currentCoroutineContext().ensureActive()
    Log.e(tag, "the settings write failed", failure)
    null
}
