package com.gawi.feature.settings

import androidx.annotation.StringRes
import com.gawi.core.data.backup.ExportStatus
import com.gawi.core.data.backup.ImportResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// The Data section's decisions: which of the four things the export row can say
// applies, which help line each of the three rows shows, what an import
// reports, and what the two save dialogs open on.
//
// Separate from SettingsUiMapper.kt because the two together are past detekt's
// TooManyFunctions, which caps a *file* at eleven — the same threshold that
// keeps SettingsPickers.kt out of SettingsScreen.kt. The line it falls on is
// better than a threshold deserves: nothing here reads or writes a preference,
// and nothing next door knows a file exists.

/**
 * What the export row can say about the last backup.
 *
 * An absent stamp splits in two, and that split is the whole reason this is not
 * a null check at the call site: on a log with events it is the case the nudge
 * exists for, and on an empty one it is a warning about losing nothing.
 *
 * Exhaustive over nothing — [ExportStatus] is two fields — but written as a
 * `when` on the day count so the nought case cannot fall into [ExportRecency.DaysAgo]
 * and render "0 days ago", which is arithmetic rather than an answer.
 */
internal fun recencyOf(status: ExportStatus): ExportRecency {
    val days = status.daysSinceExport
    return when {
        days == null -> if (status.hasEvents) ExportRecency.Never else ExportRecency.NothingYet
        days == 0L -> ExportRecency.Today
        else -> ExportRecency.DaysAgo(days.toInt())
    }
}

/**
 * How one data row behaves while [dataTask] runs, where [row] is the task that
 * row starts.
 *
 * All three rows go dead while any of them runs. Exporting midway through an
 * import reads a log that is half-merged; importing during an export writes a
 * file that is half-written; and a CSV taken mid-import is a spreadsheet of a
 * log that was still changing. None is worth allowing to save a tap. What
 * differs is which row's help line has become a status, which is the only part
 * a screen reader needs telling about.
 */
internal fun activityOf(dataTask: DataTask, row: DataTask): RowActivity = when (dataTask) {
    DataTask.Idle -> RowActivity.Live
    row -> RowActivity.Running
    else -> RowActivity.Blocked
}

/**
 * The export row's help line.
 *
 * Precedence is running, then overdue, then the plain explanation, and the order
 * matters: a row that is writing a file right now has no business telling the
 * user they have no backup.
 *
 * The nudge is carried by words and not by a colour. PRD §5 asks for a gentle
 * one, an alarm-coloured caption is not that, and the value line above this
 * already says how long it has been — so the sentence here does not repeat the
 * number.
 */
@StringRes
internal fun exportHelp(activity: RowActivity, recency: ExportRecency): Int = when {
    activity == RowActivity.Running -> R.string.settings_export_running
    overdue(recency) -> R.string.settings_export_overdue_help
    else -> R.string.settings_export_help
}

/** The import row's help line. It has no nudge, because importing is not a backup. */
@StringRes
internal fun importHelp(activity: RowActivity): Int =
    if (activity == RowActivity.Running) R.string.settings_import_running else R.string.settings_import_help

/**
 * Whether a backup is old enough to say something about.
 *
 * [EXPORT_NUDGE_DAYS] is PRD §5's number and lives here, beside the copy it
 * governs, rather than in the data layer: how many days is too many is a
 * decision about what to say, and counting the days is a fact.
 *
 * [ExportRecency.Never] is overdue and [ExportRecency.NothingYet] is not, which
 * is the same distinction [recencyOf] drew and the reason it drew it.
 */
private fun overdue(recency: ExportRecency): Boolean = when (recency) {
    ExportRecency.Never -> true
    is ExportRecency.DaysAgo -> recency.days >= EXPORT_NUDGE_DAYS
    ExportRecency.NothingYet, ExportRecency.Today -> false
}

/** PRD §5: "no export has been made for 30 days". */
internal const val EXPORT_NUDGE_DAYS = 30

/**
 * What an import is worth saying.
 *
 * Three sentences rather than one with two numbers in it, because the
 * interesting cases are the ones where a number is zero: "0 added, 140 already
 * here" is arithmetic, and "nothing new in that file" is an answer.
 *
 * The copy is also written so that no count governs a noun — "1 added" and
 * "128 added" are both grammatical — which is why none of this is a `<plurals>`
 * and why the suppression in strings.xml is honest rather than a dodge. A
 * quantity resource selects on one number and this sentence has two.
 *
 * Exhaustive over [ImportResult], so a fourth way to refuse a file cannot be
 * added without deciding what it says to the user.
 */
internal fun messageFor(result: ImportResult): SettingsMessage = when (result) {
    is ImportResult.Merged -> when {
        result.added > 0 -> SettingsMessage(R.string.settings_import_done, listOf(result.added, result.read - result.added))
        result.read > 0 -> SettingsMessage(R.string.settings_import_nothing_new)
        else -> SettingsMessage(R.string.settings_import_empty)
    }

    ImportResult.Refused.NotAnExport -> SettingsMessage(R.string.settings_error_import_unreadable)

    is ImportResult.Refused.Damaged -> SettingsMessage(R.string.settings_error_import_unreadable)

    // Intact, merely newer. Telling someone their only backup is damaged when
    // the fix is to update the app would be a lie with consequences.
    is ImportResult.Refused.FromANewerVersion -> SettingsMessage(R.string.settings_error_import_newer)
}

/**
 * The name the save dialog opens on.
 *
 * A suggestion and not a path: the picker lets the user rename it and choose
 * where it goes, and the app is never told where that was.
 *
 * [today] is the device's wall-clock date and deliberately **not** the logical
 * date. The day cutoff decides which day a completion belongs to
 * (architecture §5); it has no business deciding what a file is called, and
 * someone exporting at 00:30 under an 03:00 cutoff would otherwise find
 * yesterday's name on today's backup. ISO order so a folder of these sorts
 * chronologically.
 */
internal fun exportFileName(today: LocalDate): String = "gawi-export-${DateTimeFormatter.ISO_LOCAL_DATE.format(today)}.json"

/**
 * The CSV row's help line.
 *
 * **No nudge branch, and that is the decision rather than an omission.** The
 * 30-day nudge asks whether a copy of the *log* exists, and a CSV is not one —
 * it holds no events, so nothing can be rebuilt from it. A row that offered to
 * settle that warning with a spreadsheet would be telling the user something
 * false about their own data, which is what docs/ux/settings.md §8 means when it
 * says the CSV must never be described as a recovery path. Nothing here reads
 * [ExportRecency] at all, so it cannot acquire one by accident.
 */
@StringRes
internal fun csvHelp(activity: RowActivity): Int =
    if (activity == RowActivity.Running) R.string.settings_export_csv_running else R.string.settings_export_csv_help

/**
 * What a completed CSV export is worth saying.
 *
 * **[rows] decides which sentence, and never appears in one.** Putting the
 * number in the copy would mean a noun governed by a count — "1 completions" —
 * and therefore a `<plurals>`, whose id cannot travel through
 * [SettingsMessage] because the Route resolves that through `getString`. The
 * rule this file already follows for the import message is to write copy that
 * needs no quantity resource, and the one thing a count says that copy cannot
 * is that the file came out empty, which the other branch says outright.
 *
 * The copy says what the file is and not that a backup was made. That is the
 * whole distinction the row exists to keep.
 */
internal fun csvMessageFor(rows: Int): SettingsMessage = if (rows == 0) {
    SettingsMessage(R.string.settings_export_csv_empty)
} else {
    SettingsMessage(R.string.settings_export_csv_done)
}

/**
 * The name the CSV save dialog opens on.
 *
 * A different stem from [exportFileName] rather than a different extension
 * alone, so the two files are told apart in a folder listing by the part a
 * person reads first. "completions" is also the more honest word: it names what
 * is in the file, where "export" in this app means the log.
 */
internal fun csvFileName(today: LocalDate): String = "gawi-completions-${DateTimeFormatter.ISO_LOCAL_DATE.format(today)}.csv"
