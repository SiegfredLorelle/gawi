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
 * [Settings.exportRecency] is stored, but not as a setting and not by the
 * settings store — see `ExportJournal`. It arrives here already reduced to the
 * four things the row can say, because deciding which of them applies is a
 * decision and decisions live in the mapper.
 *
 * [Settings.dataTask] is the one thing here that is not a stored value, and
 * that is not a hole in the rule this screen is built on. "The store is the
 * only source of truth" is a rule about *committed settings* — it exists so
 * the screen can never draw a value the file does not hold. There is no
 * preference called "an export is running", so no write can make the screen
 * and the file disagree about it. It lives on the state rather than in the
 * screen because the work belongs to `viewModelScope`: it is the coroutine
 * finishing, not a gesture, that ends the busy state, and screen-local state
 * has no way to hear that.
 *
 * Internal throughout, like the other two: [SettingsRoute] is this module's
 * whole API.
 */
internal sealed interface SettingsUiState {

    data object Loading : SettingsUiState

    data object Unavailable : SettingsUiState

    data class Settings(
        val dayCutoff: LocalTime,
        val weekStart: DayOfWeek,
        val reminderTime: LocalTime,
        val dataTask: DataTask = DataTask.Idle,
        val exportRecency: ExportRecency = ExportRecency.NothingYet,
    ) : SettingsUiState
}

/**
 * Whether a file is being written or read right now.
 *
 * Three states rather than a boolean, because the two rows say different
 * things while they wait and both are disabled either way — exporting while an
 * import is half-applied, or the reverse, is a race with a user's only backup
 * on one side of it.
 */
internal enum class DataTask { Idle, Exporting, Importing }

/**
 * What the export row's value line says about the last backup (PRD §5).
 *
 * A type rather than a nullable day count, because the three interesting cases
 * are the ones a number cannot express. [NothingYet] is a log with no events in
 * it, where a nudge would be a warning about losing nothing; [Never] is the same
 * absent stamp over a log that does hold something, which is the case the nudge
 * exists for; and [Today] is nought days ago, which reads as arithmetic rather
 * than as an answer.
 *
 * The 30-day threshold is not in here. This says when; whether that is too long
 * ago is copy policy and lives beside the copy, in `exportHelp`.
 */
internal sealed interface ExportRecency {

    /** No events, so nothing to lose and nothing to say. */
    data object NothingYet : ExportRecency

    /** Events, and no export ever recorded. */
    data object Never : ExportRecency

    data object Today : ExportRecency

    data class DaysAgo(val days: Int) : ExportRecency
}

/**
 * Whether a row is live, the work in progress, or shut for someone else's work.
 *
 * Three named states rather than an `enabled` and a `running` boolean, and this
 * is load-bearing rather than tidy: the export row needs a label, a value, a
 * help line, an enabled flag, a running flag and a callback, which is six
 * parameters, and detekt's `LongParameterList` fires *at* six. Collapsing the
 * two flags is what keeps `SettingRow` compiling with the live-region
 * announcement four tests depend on.
 *
 * [Blocked] and [Running] both disable the row. They differ in which one is
 * being told to a screen reader.
 */
internal enum class RowActivity { Live, Running, Blocked }
