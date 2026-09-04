package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import com.gawi.core.testing.broken
import com.gawi.core.testing.running
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import com.gawi.core.ui.streak.StreakUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streak widget's pure half: what a snapshot becomes, and what a size decides.
 *
 * Plain JUnit, no Robolectric — everything here is arithmetic over data classes,
 * which is the property that made a separate `StreakUiState` worth having.
 * `StreakLabelTest` covers the half that needs resources.
 */
class StreakUiStateTest {

    private val daily = Schedule.Daily
    private val weekly = Schedule.Weekly(timesPerWeek = 3)

    // -------- toStreakState --------

    @Test
    fun `a daily habit's run is counted in days and a weekly one in weeks`() {
        val state = todaySnapshot(
            habits = listOf(
                todayHabit(id = habitId(1), name = "read", schedule = daily, streak = running(4)),
                todayHabit(id = habitId(2), name = "gym", schedule = weekly, streak = running(4)),
            ),
        ).toStreakState()

        assertEquals(StreakUi.Days(4), state.rows[0].streak)
        assertEquals(StreakUi.Weeks(4), state.rows[1].streak)
    }

    /**
     * The invariant three files state and this is the widget's copy of: the same
     * integer must not become the same rendering. If this ever passes with the two
     * equal, `StreakUi`'s reason for being sealed has been lost.
     */
    @Test
    fun `the same count in different units is not the same streak`() {
        val state = todaySnapshot(
            habits = listOf(
                todayHabit(id = habitId(1), schedule = daily, streak = running(3)),
                todayHabit(id = habitId(2), schedule = weekly, streak = running(3)),
            ),
        ).toStreakState()

        assertTrue("a 3-day run and a 3-week run became the same value", state.rows[0].streak != state.rows[1].streak)
    }

    @Test
    fun `a break keeps what it cost, and a habit with no history does not`() {
        val state = todaySnapshot(
            habits = listOf(
                todayHabit(id = habitId(1), name = "broken", schedule = daily, streak = broken(previous = 9)),
                todayHabit(id = habitId(2), name = "fresh", schedule = daily, streak = StreakSnapshot.NONE),
            ),
        ).toStreakState()

        assertEquals(StreakUi.Broken(previous = 9, weekly = false), state.rows[0].streak)
        assertSame(StreakUi.None, state.rows[1].streak)
    }

    /** Query order, not streak order — [toStreakState] carries why. */
    @Test
    fun `rows keep query order even when that is not descending`() {
        val state = todaySnapshot(
            habits = listOf(
                todayHabit(id = habitId(1), name = "small", streak = running(1)),
                todayHabit(id = habitId(2), name = "big", streak = running(40)),
                todayHabit(id = habitId(3), name = "middling", streak = running(7)),
            ),
        ).toStreakState()

        assertEquals(listOf("small", "big", "middling"), state.rows.map { it.name })
    }

    @Test
    fun `the state carries the snapshot's logical date, not today's`() {
        val state = todaySnapshot(habits = listOf(todayHabit())).toStreakState()
        assertEquals(FIXED_DATE, state.asOf)
    }

    @Test
    fun `an empty log yields no rows`() {
        assertEquals(emptyList<StreakRow>(), todaySnapshot().toStreakState().rows)
    }

    // -------- the size gate --------

    @Test
    fun `the smallest widget draws the compact form`() {
        assertEquals(StreakLayout.Compact, layoutAt(180.dp, 110.dp))
    }

    @Test
    fun `a widget clearing both thresholds draws the full form`() {
        assertEquals(StreakLayout.Full, layoutAt(250.dp, 200.dp))
    }

    /** Inclusive on both edges, pinned so the comparison cannot silently become strict. */
    @Test
    fun `the full form starts exactly at the thresholds`() {
        assertEquals(StreakLayout.Full, layoutAt(FULL_MIN_WIDTH.dp, FULL_MIN_HEIGHT.dp))
        assertEquals(StreakLayout.Compact, layoutAt((FULL_MIN_WIDTH - 1).dp, FULL_MIN_HEIGHT.dp))
        assertEquals(StreakLayout.Compact, layoutAt(FULL_MIN_WIDTH.dp, (FULL_MIN_HEIGHT - 1).dp))
    }

    /**
     * Both dimensions, unlike Momo's gate. A tall narrow widget must not try the
     * unit word: `3 weeks` ellipsised to `3 we…` is worse than the `3w` the
     * compact form draws deliberately.
     */
    @Test
    fun `a tall narrow widget stays compact`() {
        assertEquals(StreakLayout.Compact, layoutAt(180.dp, 400.dp))
    }

    /**
     * The room is dp and the text is sp, so a 200% scale halves the room measured
     * in text. A widget that would take the unit word at scale 1 must not try it
     * at scale 2 — that is the `3 we…` the compact form exists to avoid, arriving
     * by the one setting the gate used to ignore.
     */
    @Test
    fun `a large widget drops to compact at a doubled font scale`() {
        assertEquals(StreakLayout.Full, layoutAt(250.dp, 200.dp, fontScale = 1f))
        assertEquals(StreakLayout.Compact, layoutAt(250.dp, 200.dp, fontScale = 2f))
    }

    /** Room enough for the word even at 200%, so the gate is scaling and not just refusing. */
    @Test
    fun `a very large widget still takes the full form at a doubled font scale`() {
        assertEquals(StreakLayout.Full, layoutAt(460.dp, 320.dp, fontScale = 2f))
    }

    /** Shrinking the text does not make the widget's cells wider in any way a user asked for. */
    @Test
    fun `a font scale below one buys no extra room`() {
        assertEquals(StreakLayout.Compact, layoutAt(180.dp, 110.dp, fontScale = 0.5f))
    }

    // -------- the non-row bodies --------

    @Test
    fun `nothing is drawn before the first read arrives`() {
        assertSame(StreakBodyContent.Blank, StreakContent.Loading.body(DpSize(250.dp, 200.dp), SCALE_1))
    }

    @Test
    fun `a failed read and an empty log say different things`() {
        val unavailable = StreakContent.Unavailable.body(DpSize(250.dp, 200.dp), SCALE_1)
        val empty = StreakContent.Ready(todaySnapshot().toStreakState()).body(DpSize(250.dp, 200.dp), SCALE_1)

        assertTrue(unavailable is StreakBodyContent.Copy)
        assertTrue(empty is StreakBodyContent.Copy)
        assertTrue(
            "a broken read and an empty log drew the same copy",
            (unavailable as StreakBodyContent.Copy).text != (empty as StreakBodyContent.Copy).text,
        )
    }

    /** No number, so nothing to date — the "as of" line would be dating nothing. */
    @Test
    fun `the copy states carry no date at any size`() {
        for (size in listOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 200.dp))) {
            assertTrue(StreakContent.Unavailable.body(size, SCALE_1) is StreakBodyContent.Copy)
            assertTrue(StreakContent.Ready(todaySnapshot().toStreakState()).body(size, SCALE_1) is StreakBodyContent.Copy)
        }
    }

    private fun layoutAt(
        width: androidx.compose.ui.unit.Dp,
        height: androidx.compose.ui.unit.Dp,
        fontScale: Float = SCALE_1,
    ): StreakLayout {
        val body = StreakContent.Ready(todaySnapshot(habits = listOf(todayHabit())).toStreakState())
            .body(DpSize(width, height), fontScale)
        return (body as StreakBodyContent.Rows).layout
    }

    private companion object {
        /** The device default, spelled out where a test is not about font scaling. */
        const val SCALE_1 = 1f
    }
}
