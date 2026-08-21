package com.gawi.feature.settings

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * What the settings screen can do, as one parameter.
 *
 * A holder rather than eight lambdas in the signature, for the same reason
 * `TodayActions` and `HabitListActions` are holders: it keeps the composable
 * inside detekt's parameter limit — which fires *at* six, not above it — and
 * adding a fourth setting does not re-thread every call site and test.
 *
 * **This used to say six properties was the last one that fits, because the
 * constructor threshold is seven. That was false for this declaration and the
 * correction is worth keeping.** The threshold is indeed seven, but detekt's
 * `LongParameterList` sets `ignoreDataClasses` true by default, so the rule
 * never applies to a `data class` at all. Measured rather than reasoned about:
 * seven properties and eight both pass, and the same seven fire the rule the
 * moment `data` is removed from the declaration. So the CSV row's action is a
 * seventh property here and needs no nested holder — which is what the plan for
 * it had assumed, on the strength of this comment.
 *
 * What remains true is the reason there is no eighth for a dismiss. The 30-day
 * nudge has no "not now": a nudge that can be dismissed for thirty days is a
 * nudge that says nothing, and there is no second surface to dismiss it from.
 * It is a caption on the row that fixes it.
 *
 * Each change carries the whole new value rather than a delta. `update` on the
 * store is a read-modify-write over one transform, so a partial edit has
 * nowhere to be half-applied.
 */
internal data class SettingsActions(
    val onDayCutoffChange: (LocalTime) -> Unit,
    val onWeekStartChange: (DayOfWeek) -> Unit,
    val onReminderTimeChange: (LocalTime) -> Unit,
    /**
     * The user asked to export; the Route turns that into a save dialog.
     *
     * Nullary, and that is what keeps the screen free of a `Uri`, an `Intent`
     * and a launcher — the same way `is24Hour` keeps it free of a `Context`.
     * The picker's answer goes straight from the Route to the ViewModel and
     * never passes through here.
     */
    val onExport: () -> Unit,
    /**
     * The user asked for the CSV of completions.
     *
     * Its own action rather than a parameter on [onExport], because the two
     * launch different pickers with different types and different default
     * names, and because what they produce are different kinds of thing — one
     * is the backup and one is not.
     */
    val onExportCompletions: () -> Unit,
    val onImport: () -> Unit,
    /**
     * The user asked for the reminder to be able to appear at all.
     *
     * Nullary for the reason [onExport] is: the Route turns it into a permission
     * request, or — when that request would be a silent no-op because the user has
     * already refused it once for good — into the system's own notification
     * settings for this app. Neither an `Intent` nor a launcher reaches the
     * screen, which is what keeps `SettingsScreenTest` able to render both states.
     *
     * The eighth property, and safe: this declaration's `data` keyword is what
     * exempts it from `LongParameterList` entirely, per the measurement above.
     */
    val onEnableNotifications: () -> Unit,
    val onBack: () -> Unit,
)
