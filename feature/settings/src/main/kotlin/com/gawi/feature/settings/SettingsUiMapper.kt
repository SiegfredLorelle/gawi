package com.gawi.feature.settings

import androidx.annotation.StringRes
import com.gawi.core.data.settings.UserSettings
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Read model to UI state, plus the two formatting decisions the three settings
// rows make. The Data section's decisions live in SettingsDataMapper.kt.
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
