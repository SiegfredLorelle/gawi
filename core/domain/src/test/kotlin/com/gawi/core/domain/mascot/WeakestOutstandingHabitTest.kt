package com.gawi.core.domain.mascot

import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.testing.habitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * docs/ux/momo.md §3's "the one about to break": which habit's spare lives
 * Momo's right gills carry while nothing is regenerating.
 *
 * `RecentlyBrokenHabitsTest` covers the other half of the same rule — the
 * habit the regenerating line names — and the two never answer at once,
 * because a broken run is deliberately not a candidate here.
 */
class WeakestOutstandingHabitTest {

    // A Monday, matching the other mascot tests so the dates read the same way.
    private val today = LocalDate.parse("2026-08-17")

    /** A run still going, carrying [spare] gills. */
    private fun live(spare: Int, current: Int = 9) = StreakSnapshot(current = current, previous = 0, brokenOn = null, spare = spare)

    /** A run that has already gone, which always reports zero spare. */
    private fun brokeOn(day: LocalDate) = StreakSnapshot(current = 0, previous = 4, brokenOn = day, spare = 0)

    private fun habit(
        n: Int,
        streak: StreakSnapshot,
        archived: Boolean = false,
        completedToday: Boolean = false,
        schedule: Schedule = Schedule.Daily,
    ) = HabitMoodState(
        id = habitId(n),
        schedule = schedule,
        archived = archived,
        completedToday = completedToday,
        completionsThisWeek = 0,
        streak = streak,
    )

    private fun weakest(vararg habits: HabitMoodState): HabitId? = Mascot.weakestOutstandingHabit(
        MoodInputs(
            habits = habits.toList(),
            today = today,
            now = LocalDateTime.of(today, LocalTime.of(9, 0)),
            reminderTime = LocalTime.of(21, 0),
            dayCutoff = LocalTime.MIDNIGHT,
            weekStart = DayOfWeek.MONDAY,
        ),
    )

    @Test
    fun `no habits is nobody to name`() {
        assertNull(weakest())
    }

    @Test
    fun `the habit with the fewest spare lives is the one at risk`() {
        assertEquals(habitId(2), weakest(habit(1, live(spare = 3)), habit(2, live(spare = 1)), habit(3, live(spare = 2))))
    }

    @Test
    fun `habits level on spare keep the callers own order`() {
        assertEquals(habitId(2), weakest(habit(1, live(spare = 3)), habit(2, live(spare = 1)), habit(3, live(spare = 1))))
    }

    @Test
    fun `a habit already done today is not at risk today`() {
        assertEquals(habitId(1), weakest(habit(1, live(spare = 2)), habit(2, live(spare = 0), completedToday = true)))
    }

    @Test
    fun `an archived habit is nobody's business`() {
        assertEquals(habitId(1), weakest(habit(1, live(spare = 2)), habit(2, live(spare = 0), archived = true)))
    }

    @Test
    fun `a run that has already broken is not about to break`() {
        // Otherwise an abandoned habit reports zero spare for ever and pins the
        // drawing to nothing left, naming the same habit every evening.
        assertEquals(habitId(1), weakest(habit(1, live(spare = 2)), habit(2, brokeOn(today.minusDays(9)))))
    }

    @Test
    fun `every habit broken leaves nobody at risk`() {
        assertNull(weakest(habit(1, brokeOn(today.minusDays(2)))))
    }

    @Test
    fun `a young run with nothing banked yet is at risk, and says so`() {
        // Zero spare here is a run too young to have earned its first gill, not
        // a break: one miss from losing a real streak.
        assertEquals(habitId(2), weakest(habit(1, live(spare = 1)), habit(2, live(spare = 0, current = 3))))
    }

    @Test
    fun `a habit with no completions at all has no run to lose`() {
        // StreakSnapshot.NONE reports zero spare like a spent run does, so
        // without the live-run filter a habit added this morning would win
        // outright and have Momo mourn a streak that never existed.
        assertEquals(habitId(1), weakest(habit(1, live(spare = 0, current = 2)), habit(2, StreakSnapshot.NONE)))
    }

    @Test
    fun `a fresh install has nobody at risk`() {
        assertNull(weakest(habit(1, StreakSnapshot.NONE), habit(2, StreakSnapshot.NONE)))
    }

    @Test
    fun `a weekly habit still comfortably ahead is not outstanding`() {
        val weekly = habit(2, live(spare = 0), schedule = Schedule.Weekly(timesPerWeek = 2))
        assertEquals(habitId(1), weakest(habit(1, live(spare = 2)), weekly))
    }
}
