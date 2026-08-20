package com.gawi.feature.settings

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * What the settings screen draws.
 *
 * Three branches rather than the four the other two screens have, and the
 * missing one is [SettingsUiState.Loading]'s usual companion: there is no
 * `Empty`. Settings always exist — an unwritten preferences file reads as the
 * PRD's defaults rather than as nothing — so a screen that said "no settings
 * yet" would be describing a state the data layer cannot produce.
 *
 * [Unavailable] is rarer here than on the other screens and worth being precise
 * about. `SettingsSource.observe()` catches `IOException` and emits defaults,
 * deliberately, so an unreadable preferences file shows midnight, Monday and
 * 21:00 rather than an error. What reaches this branch is a failure that is not
 * IO, which is a bug rather than a bad disk. Nothing is silently overwritten by
 * the difference: a write goes through `DataStore.edit`, which reads first and
 * throws on the same unreadable file, so the user gets the error on the write
 * instead of losing a setting to it.
 *
 * [Settings] carries the stored values, not formatted text. Which words a day
 * or a time is drawn with is a decision, so it lives in the mapper next to the
 * others; what travels here is what the picker has to open on.
 *
 * Internal throughout, like the other two: [SettingsRoute] is this module's
 * whole API.
 */
internal sealed interface SettingsUiState {

    data object Loading : SettingsUiState

    data object Unavailable : SettingsUiState

    data class Settings(val dayCutoff: LocalTime, val weekStart: DayOfWeek, val reminderTime: LocalTime) : SettingsUiState
}
