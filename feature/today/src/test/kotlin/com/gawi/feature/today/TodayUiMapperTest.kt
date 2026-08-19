package com.gawi.feature.today

import androidx.compose.ui.graphics.Color
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.feature.today.testsupport.TODAY
import com.gawi.feature.today.testsupport.habitId
import com.gawi.feature.today.testsupport.todayHabit
import com.gawi.feature.today.testsupport.todaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** docs/ux/today-view.md §5's display rules, asserted without a device. */
class TodayUiMapperTest {

    private val daily = Schedule.Daily
    private val weekly = Schedule.Weekly(3)

    @Test
    fun `a daily streak is counted in days`() {
        assertEquals(StreakUi.Days(4), StreakSnapshot(current = 4, previous = 0, brokenOn = null).toUi(daily))
    }

    @Test
    fun `a weekly streak is counted in weeks, never as the same number`() {
        // §5: "A daily habit's streak is a count; a weekly habit's is in weeks.
        // The two must never be styled as the same number."
        val snapshot = StreakSnapshot(current = 3, previous = 0, brokenOn = null)
        assertEquals(StreakUi.Weeks(3), snapshot.toUi(weekly))
        assertEquals(StreakUi.Days(3), snapshot.toUi(daily))
    }

    @Test
    fun `an unfinished day still shows its live streak`() {
        // §5: a row unchecked at 09:00 must not read 0. An unfinished current
        // day has not broken the streak, it has only not extended it.
        val row = todayHabit(completedToday = false, streak = StreakSnapshot(current = 4, previous = 0, brokenOn = null))
        assertEquals(StreakUi.Days(4), row.toRowUi().streak)
    }

    @Test
    fun `a broken streak keeps what was lost as context`() {
        // §5: the row reads 0 next to "was 4".
        val broken = StreakSnapshot(current = 0, previous = 4, brokenOn = LocalDate.parse("2026-08-16"))
        assertEquals(StreakUi.Broken(previous = 4, weekly = false), broken.toUi(daily))
        assertEquals(StreakUi.Broken(previous = 4, weekly = true), broken.toUi(weekly))
    }

    @Test
    fun `a habit with no completions has nothing to draw`() {
        assertEquals(StreakUi.None, StreakSnapshot.NONE.toUi(daily))
        assertEquals(StreakUi.None, StreakSnapshot.NONE.toUi(weekly))
    }

    @Test
    fun `only a weekly habit carries week progress`() {
        assertEquals(WeekProgress(done = 2, target = 3), todayHabit(schedule = weekly, weekCount = 2).toRowUi().weekProgress)
        assertNull(todayHabit(schedule = daily, weekCount = 2).toRowUi().weekProgress)
    }

    @Test
    fun `a habit colour is parsed in both lengths and survives anything else`() {
        // Unvalidated off the event log, so the row degrades rather than crashes.
        assertEquals(Color(0xFFAABBCC), parseHabitColor("#aabbcc"))
        assertEquals(Color(0x80AABBCC), parseHabitColor("#80aabbcc"))
        assertNull(parseHabitColor("aabbcc"))
        assertNull(parseHabitColor("#abc"))
        assertNull(parseHabitColor("#gggggg"))
        assertNull(parseHabitColor(""))
    }

    @Test
    fun `no habits at all is its own state, not an empty list`() {
        // §4's rule 0 is load-bearing: a first run must not read as thriving.
        assertEquals(TodayUiState.Empty(Mood.CONTENT), todaySnapshot().toUiState())
    }

    @Test
    fun `the remaining count follows the mood's rule, not the tick box`() {
        // A weekly habit with its target still reachable is not outstanding,
        // even though it is unticked. Counting unticked rows would say 2.
        val state = todaySnapshot(
            habits = listOf(
                todayHabit(id = habitId(1), schedule = Schedule.Daily, completedToday = false),
                todayHabit(id = habitId(2), schedule = Schedule.Weekly(3), completedToday = false, weekCount = 0),
            ),
        ).toUiState() as TodayUiState.Habits

        assertEquals(1, state.remaining)
        assertEquals(2, state.rows.size)
    }

    @Test
    fun `the mood reaches the state, decided from the same snapshot`() {
        // Past the reminder with something outstanding, which is §4's rule 3.
        val worried = todaySnapshot(
            habits = listOf(todayHabit(completedToday = false)),
            now = TODAY.atTime(21, 30),
        ).toUiState() as TodayUiState.Habits
        assertEquals(Mood.WORRIED, worried.mood)

        // Same rows, same clock, nothing outstanding — §4's rule 1.
        val thriving = todaySnapshot(
            habits = listOf(todayHabit(completedToday = true)),
            now = TODAY.atTime(21, 30),
        ).toUiState() as TodayUiState.Habits
        assertEquals(Mood.THRIVING, thriving.mood)
        assertEquals(0, thriving.remaining)
    }

    @Test
    fun `a signed hex string is not a colour`() {
        // toLongOrNull accepts a leading sign, so this is six digits that parse
        // to a negative number and mask into an arbitrary opaque colour.
        assertNull(parseHabitColor("#-abcde"))
        assertNull(parseHabitColor("#-abcdefa"))
        assertNull(parseHabitColor("#+abcde"))
    }

    @Test
    fun `an archived row is not drawn, counted, or moodful`() {
        // Mascot.mood drops archived habits itself, so the rows and the count
        // have to drop them too. Assert all three: asserting only the count is
        // what let an archived habit stay a drawn, tappable row.
        val state = todaySnapshot(
            habits = listOf(
                todayHabit(id = habitId(1), completedToday = true),
                todayHabit(id = habitId(2), completedToday = false, archived = true),
            ),
        ).toUiState() as TodayUiState.Habits

        assertEquals(listOf(habitId(1)), state.rows.map { it.id })
        assertEquals(0, state.remaining)
        assertEquals(Mood.THRIVING, state.mood)
    }

    @Test
    fun `a list of only archived habits is empty, not a list of rows`() {
        val state = todaySnapshot(habits = listOf(todayHabit(archived = true))).toUiState()

        assertEquals(TodayUiState.Empty(Mood.CONTENT), state)
    }

    @Test
    fun `the state carries the date its rows were queried for`() {
        val state = todaySnapshot(habits = listOf(todayHabit())).toUiState() as TodayUiState.Habits
        assertEquals(TODAY, state.logicalDate)
    }
}
