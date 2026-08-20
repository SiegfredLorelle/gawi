package com.gawi.feature.settings

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * What the settings screen can do, as one parameter.
 *
 * A holder rather than six lambdas in the signature, for the same reason
 * `TodayActions` and `HabitListActions` are holders: it keeps the composable
 * inside detekt's parameter limit — which fires *at* six, not above it — and
 * adding a fourth setting does not re-thread every call site and test.
 *
 * Six properties is the last one that fits. The constructor threshold is seven,
 * so the 30-day nudge's "not now" will need a rethink rather than one more
 * line here.
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
    val onImport: () -> Unit,
    val onBack: () -> Unit,
)
