package com.gawi.core.ui.streak

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * docs/ux/today-view.md §5's streak rules, asserted once for both surfaces.
 *
 * These moved here with [toUi] when habit detail became the second screen to
 * draw a streak (PRD §6.6). Plain JVM, no Robolectric: the mapping is a `when`
 * over two domain values and touches no Android type.
 */
class StreakUiTest {

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
}
