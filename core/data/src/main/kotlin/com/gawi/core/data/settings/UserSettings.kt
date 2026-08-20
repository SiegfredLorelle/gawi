package com.gawi.core.data.settings

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The device-local preferences: the day boundary that decides which logical
 * date a tap belongs to, the week start that buckets weekly habits, and the
 * reminder time the end-of-day notification fires at.
 *
 * Settings are not events (architecture §3) — they never sync and never enter
 * the log. Defaults come from the PRD: midnight, Monday, and 21:00.
 *
 * [reminderTime] has two readers, which is why one threshold serves both: the
 * end-of-day reminder, and the mascot's `nearBoundary` mood input
 * (docs/ux/today-view.md §4). A second "the day is nearly over" setting would
 * be one more thing to keep in sync for no gain the user can see.
 *
 * Timezone behaviour, the fourth preference the PRD lists, is still absent on
 * purpose: it is "use the device zone", which
 * [com.gawi.core.data.time.DeviceClock] already supplies per call.
 *
 * The 30-day nudge's `lastExportedAt` is deliberately not a fourth field here
 * either, and that reverses what docs/ux/settings.md §7 predicted. Two reasons,
 * both in [com.gawi.core.data.backup.ExportJournal]'s KDoc: this type is
 * dedupe-compared to decide whether to re-run the Today query, and the nudge
 * needs a second signal that is not a preference at all. These three are what
 * the user set; when an export last happened is a record of what the app did.
 */
data class UserSettings(
    val dayCutoff: LocalTime = LocalTime.MIDNIGHT,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val reminderTime: LocalTime = DEFAULT_REMINDER_TIME,
) {

    companion object {
        /** The PRD's own example, and late enough to leave the evening room. */
        val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(21, 0)
    }
}
