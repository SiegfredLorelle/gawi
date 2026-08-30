package com.gawi.feature.settings

import androidx.annotation.StringRes
import com.gawi.core.data.settings.ThemeMode
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.ui.date.weekdayName
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Read model to UI state, plus the formatting and naming decisions the settings
// rows make. The Data section's decisions live in SettingsDataMapper.kt.
//
// Here rather than in the composables, for the reason HabitsUiMapper gives:
// these are decisions, and a decision made in a composable can only be got
// wrong in a screenshot. All of them are covered by SettingsUiMapperTest.

/**
 * The stored settings, as the screen draws them.
 *
 * [dataTask] and [exportRecency] are not settings and both are defaulted, so
 * every caller that does not care about a file being written reads exactly as it
 * did before. Their defaults are the states that draw the screen as it drew
 * before either existed.
 */
internal fun UserSettings.toUiState(
    version: String,
    dataTask: DataTask = DataTask.Idle,
    exportRecency: ExportRecency = ExportRecency.NothingYet,
): SettingsUiState = SettingsUiState.Settings(
    dayCutoff = dayCutoff,
    weekStart = weekStart,
    reminderTime = reminderTime,
    theme = theme,
    version = version,
    dataTask = dataTask,
    exportRecency = exportRecency,
)

/**
 * The name of a day, as a string resource — the spelled-out form.
 *
 * `:core:ui`'s, since 2026-08-24: the seven names were here and the history
 * grid's spoken cell labels needed the same seven, and copy two features draw
 * belongs in `:core:ui` (AGENTS.md). The reasoning for resources over
 * `DayOfWeek.getDisplayName` moved with them, including the one that decided it
 * — `getDisplayName` varies with the JVM's locale *data*, so what the picker
 * read would depend on which machine rendered it.
 *
 * Kept as a name here rather than calling [weekdayName] at the two call sites.
 * It says which of the two forms this screen wants, and it is what the tests
 * assert through, so neither has to name another module's `R` class.
 */
@StringRes
internal fun labelFor(day: DayOfWeek): Int = weekdayName(day)

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

/**
 * The name of a theme mode, as a string resource.
 *
 * This module's own strings rather than `:core:ui`'s, unlike [labelFor] above:
 * nothing else draws these three words, and the mode itself is a `:core:data`
 * type that `:core:ui` has no reason to know about.
 */
@StringRes
internal fun labelFor(theme: ThemeMode): Int = when (theme) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

/** Every day, in the order the picker offers them — the ISO week, Monday first. */
internal val WEEK_START_OPTIONS: List<DayOfWeek> = DayOfWeek.entries

/** The three modes, in the order the picker offers them — the default first. */
internal val THEME_OPTIONS: List<ThemeMode> = ThemeMode.entries

private val TIME_24_HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

private val TIME_12_HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT)
