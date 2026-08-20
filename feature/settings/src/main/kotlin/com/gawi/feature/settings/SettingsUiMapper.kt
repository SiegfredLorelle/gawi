package com.gawi.feature.settings

import androidx.annotation.StringRes
import com.gawi.core.data.backup.ExportStatus
import com.gawi.core.data.backup.ImportResult
import com.gawi.core.data.settings.UserSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Read model to UI state, plus the two formatting decisions this screen makes.
//
// Here rather than in the composables, for the reason HabitsUiMapper gives:
// these are decisions, and a decision made in a composable can only be got
// wrong in a screenshot. Both are covered by SettingsUiMapperTest.

/**
 * The stored settings, as the screen draws them.
 *
 * [dataTask] and [exportRecency] are not settings and both are defaulted, so
 * every caller that does not care about a file being written reads exactly as it
 * did before. Their defaults are the states that draw the screen as it drew
 * before either existed.
 */
internal fun UserSettings.toUiState(
    dataTask: DataTask = DataTask.Idle,
    exportRecency: ExportRecency = ExportRecency.NothingYet,
): SettingsUiState = SettingsUiState.Settings(
    dayCutoff = dayCutoff,
    weekStart = weekStart,
    reminderTime = reminderTime,
    dataTask = dataTask,
    exportRecency = exportRecency,
)

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
 * Both rows go dead while either runs. Exporting midway through an import reads
 * a log that is half-merged; importing during an export writes a file that is
 * half-written. Neither is worth allowing to save a tap. What differs is which
 * row's help line has become a status, which is the only part a screen reader
 * needs telling about.
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
 * The name of a day, as a string resource.
 *
 * Resources rather than `DayOfWeek.getDisplayName`, which would be shorter.
 * Three reasons, and the third is the one that decided it: the tests can then
 * assert against the same `R.string` the screen renders, the copy is
 * translatable in the one place every other string in this app is, and
 * `getDisplayName` varies with the JVM's locale data, so what the picker reads
 * would depend on which machine rendered it.
 *
 * Exhaustive rather than defaulted, so a `when` here is a compile error if
 * `java.time` ever grows an eighth day — the same bet [messageFor] makes about
 * `ImportResult`, and it costs nothing to hold.
 */
@StringRes
internal fun labelFor(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.settings_day_monday
    DayOfWeek.TUESDAY -> R.string.settings_day_tuesday
    DayOfWeek.WEDNESDAY -> R.string.settings_day_wednesday
    DayOfWeek.THURSDAY -> R.string.settings_day_thursday
    DayOfWeek.FRIDAY -> R.string.settings_day_friday
    DayOfWeek.SATURDAY -> R.string.settings_day_saturday
    DayOfWeek.SUNDAY -> R.string.settings_day_sunday
}

/**
 * A time, in the device's own convention.
 *
 * [is24Hour] is passed in rather than read here, because it is a fact about the
 * device and this file is a pure one — which is what lets both forms be tested
 * without a device to set the flag on.
 *
 * [Locale.ROOT] rather than the default locale, so that what the test asserts
 * and what the screen renders cannot come apart on a machine set to something
 * else. That also fixes the meridiem to AM/PM, which is a real limitation and
 * is recorded in docs/ux/settings.md §5 rather than left to be discovered.
 */
internal fun formatTime(time: LocalTime, is24Hour: Boolean): String = (if (is24Hour) TIME_24_HOUR else TIME_12_HOUR).format(time)

/** Every day, in the order the picker offers them — the ISO week, Monday first. */
internal val WEEK_START_OPTIONS: List<DayOfWeek> = DayOfWeek.entries

private val TIME_24_HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private val TIME_12_HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT)

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
