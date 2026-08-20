package com.gawi.feature.settings

/**
 * Which dialog is open, if any.
 *
 * An enum rather than three booleans, so two dialogs cannot be open at once —
 * and `rememberSaveable` stores it with no custom saver, which three nullable
 * value holders would each have needed.
 */
internal enum class SettingsDialog { None, DayCutoff, WeekStart, Reminder }
