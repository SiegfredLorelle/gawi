package com.gawi.core.data.model

import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot

/**
 * A habit as a screen needs it: what it is, whether it is done for the logical
 * date being shown, how far into its week it is, and its streak.
 *
 * [habit] is the domain's own `HabitState` rather than seven copied fields, so
 * there is one definition of what a habit is. What this adds is the part
 * `HabitState` deliberately does not carry — completion and streak both depend
 * on "today", which is not in the event log.
 *
 * The detail screen reads the same shape as the list. It differs only in which
 * rows it can see: the Today list hides archived habits, and asking for one
 * habit by id does not, since unarchiving has to be reachable.
 *
 * A read-model shape, so it lives here rather than in `:core:domain`: it holds
 * only domain and `java.time` types and carries no Room annotations, but it
 * exists to serve a query.
 */
data class TodayHabit(
    val habit: HabitState,
    val completedToday: Boolean,
    val note: String?,
    val weekCount: Int,
    val streak: StreakSnapshot,
)
