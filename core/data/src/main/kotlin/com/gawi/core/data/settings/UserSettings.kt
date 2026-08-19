package com.gawi.core.data.settings

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The device-local preferences the event store needs to do its job: the day
 * boundary that decides which logical date a tap belongs to, and the week
 * start that buckets weekly habits.
 *
 * Settings are not events (architecture §3) — they never sync and never enter
 * the log. Defaults come from the PRD: midnight, and Monday.
 *
 * The reminder time and timezone behaviour the PRD also lists are absent on
 * purpose. Nothing in this module reads them yet; the reminder time is a mood
 * and notification input, and timezone behaviour is "use the device zone",
 * which [com.gawi.core.data.time.DeviceClock] already supplies.
 */
data class UserSettings(val dayCutoff: LocalTime = LocalTime.MIDNIGHT, val weekStart: DayOfWeek = DayOfWeek.MONDAY)
