package com.gawi.widget

import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's whole read, which is a pure function — so this needs no Glance,
 * no device and no Robolectric shadow.
 *
 * Note what is deliberately **not** tested here: that archived habits are
 * absent. `observeToday()` has already dropped them and this mapper does not
 * filter, so a fixture with `archived = true` would be mapped like any other and
 * an "archived rows are hidden" test would pass for the wrong layer's reason —
 * the shadow's reason, not its own. Adding a second filter here would be a
 * second rule that can disagree with the query's.
 */
class WidgetUiStateTest {

    @Test
    fun `an empty log maps to no rows`() {
        assertTrue(todaySnapshot().toWidgetState().rows.isEmpty())
    }

    @Test
    fun `a row carries the habit name and whether today is done`() {
        val state = todaySnapshot(habits = listOf(todayHabit(name = "read", completedToday = true))).toWidgetState()

        assertEquals(1, state.rows.size)
        assertEquals("read", state.rows.single().name)
        assertTrue(state.rows.single().completed)
    }

    @Test
    fun `a completed habit still gets a row, so undo stays reachable`() {
        val state = todaySnapshot(
            habits = listOf(todayHabit(id = habitId(1), completedToday = true), todayHabit(id = habitId(2), completedToday = true)),
        ).toWidgetState()

        assertEquals(2, state.rows.size)
        assertTrue(state.rows.all { it.completed })
    }

    @Test
    fun `rows keep the order the query returned them in`() {
        val state = todaySnapshot(
            habits = listOf(todayHabit(id = habitId(1), name = "read"), todayHabit(id = habitId(2), name = "walk")),
        ).toWidgetState()

        assertEquals(listOf("read", "walk"), state.rows.map { it.name })
    }

    @Test
    fun `the id travels as its canonical string, which is what a tap is given`() {
        val state = todaySnapshot(habits = listOf(todayHabit(id = habitId(9)))).toWidgetState()

        assertEquals(habitId(9).value, state.rows.single().habitId)
    }

    /**
     * The mood is Mascot's, not a rule of this module's: one habit outstanding
     * is CONTENT, none outstanding is THRIVING, and the widget only carries the
     * answer. Two cases so a mapper that hardcoded one face would fail.
     */
    @Test
    fun `the state carries the mood the Today screen would show`() {
        assertEquals(Mood.CONTENT, todaySnapshot(habits = listOf(todayHabit())).toWidgetState().mood)
        assertEquals(Mood.THRIVING, todaySnapshot(habits = listOf(todayHabit(completedToday = true))).toWidgetState().mood)
    }
}
