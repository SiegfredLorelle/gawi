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
 * It used to be true that there was only ever one message here, and the reason
 * it was true is the reason it no longer is. `SettingsSource.update` is not a
 * command: it validates nothing and refuses nothing, because a fixed picker
 * cannot express an invalid time or a day that is not a day, so the only way a
 * settings write fails is by throwing and every throw reads the same. Import is
 * the first thing on this screen whose *input the user chooses*, and so the
 * first that can be refused rather than only thrown at — which is what the
 * habits and today modules have always modelled with `CommandError`, arrived at
 * here from the other end.
 *
 * [args] carries the import counts. A list rather than a vararg so this stays a
 * data class an assertion can compare whole, and `Any` rather than `Int` so a
 * future message naming a file needs no second type.
 */
internal data class SettingsMessage(@StringRes val text: Int, val args: List<Any> = emptyList())

/**
 * Runs a write, turning anything it throws into a `null`.
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
 * Two kinds of caller now, which is why [what] is a parameter rather than a
 * fixed string: a settings write, and an export or an import. The second is by
 * far the likelier to throw — a document provider can revoke a grant, run out
 * of space or vanish mid-write — so a log line claiming a settings write failed
 * would point at the wrong half of the screen.
 *
 * Returns `null` rather than a `Result`, because the caller has nothing to do
 * with the throwable beyond what has already been logged.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> commandOrNull(tag: String, what: String, command: suspend () -> T): T? = try {
    command()
} catch (failure: Exception) {
    // Cancellation is not a failure, and must not be reported as one:
    // ensureActive() rethrows it. Doing it this way rather than with a bare
    // `throw failure` also keeps detekt's RethrowCaughtException quiet.
    currentCoroutineContext().ensureActive()
    Log.e(tag, "$what failed", failure)
    null
}

// What a data task is called, in a log line and in a snackbar.
//
// Here rather than on the ViewModel for two reasons. The CSV task took that
// class to eleven functions and detekt's TooManyFunctions fires *at* eleven for
// a class — measured, and note it fires at *twelve* for a file, which is not the
// same rule read twice. The better reason is that both of these are decisions
// about what to say, which is what this file is for; neither touches a
// coroutine, a store or a Uri.

/**
 * How a task is named in a log line.
 *
 * Not user-facing, and deliberately not derived from the enum name: what a log
 * reader wants is the phrase that completes "… failed".
 */
internal fun DataTask.describe(): String = when (this) {
    DataTask.Exporting -> "the export"
    DataTask.ExportingCsv -> "the completions export"
    DataTask.Importing -> "the import"
    DataTask.Idle -> "the data task"
}

/**
 * What to tell the user when a data task throws.
 *
 * Three genuinely different promises, which is why this is not one string. The
 * backup's copy has to admit that a plausibly-named partial file now exists,
 * because the picker created the document before the write began. The CSV's is
 * narrower on purpose: a half-written spreadsheet is a nuisance where a
 * half-written backup is a file someone would later trust with their whole
 * history. The import's can promise the log is untouched and mean it, because
 * the whole file is validated before a row is written.
 *
 * Exhaustive over [DataTask], including [DataTask.Idle] — which `runDataTask`
 * cannot reach, but a `when` that needs no `else` cannot silently acquire a
 * wrong default when a fourth task is added.
 */
@StringRes
internal fun failureFor(task: DataTask): Int = when (task) {
    DataTask.Exporting -> R.string.settings_error_export
    DataTask.ExportingCsv -> R.string.settings_error_export_csv
    DataTask.Importing -> R.string.settings_error_import
    DataTask.Idle -> R.string.settings_error_unexpected
}
