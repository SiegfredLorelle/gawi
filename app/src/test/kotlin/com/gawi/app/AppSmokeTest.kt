package com.gawi.app

import com.gawi.core.data.model.TodayHabit
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.streak.StreakSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSmokeTest {
    @Test
    fun `app sees both core modules`() {
        val id = "0190163d-8694-7abc-8def-0123456789ab"
        val habit = HabitState(HabitId(id), "read", "book", "#aabbcc", Schedule.Weekly(3), tag = null, archived = false)

        val row = TodayHabit(habit, completedToday = true, note = null, weekCount = 2, streak = StreakSnapshot.NONE)

        assertEquals(id, row.habit.id.value)
        assertTrue(row.weekCount < (row.habit.schedule as Schedule.Weekly).timesPerWeek)
    }
}
