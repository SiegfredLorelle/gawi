package com.gawi.feature.settings

/**
 * Which dialog is open, if any.
 *
 * An enum rather than a boolean per dialog, so two cannot be open at once — and
 * `rememberSaveable` stores it with no custom saver, which a nullable value
 * holder per dialog would each have needed.
 */
internal enum class SettingsDialog { None, DayCutoff, WeekStart, Reminder, Theme }
