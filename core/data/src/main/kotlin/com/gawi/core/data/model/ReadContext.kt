package com.gawi.core.data.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The two values every dated read is bound to: which day it is, and where a
 * week begins.
 *
 * Both come from settings the user can change (architecture §5's day cutoff and
 * week start), and they are handed out **together** on purpose. A screen that
 * took the date from one flow and the week start from another would hold two
 * independently-deduped copies of a pair the read model keeps in step — which is
 * the disagreement `OfflineFirstHabitRepository.readContext` exists to prevent.
 *
 * A screen cannot resolve either value for itself: that needs a clock, a zone
 * and the cutoff, and a stale answer lands inside the retro window, which
 * *accepts* it rather than refusing it.
 */
data class ReadContext(val today: LocalDate, val weekStart: DayOfWeek)
