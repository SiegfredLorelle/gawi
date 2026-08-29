package com.gawi.core.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PRD §5's rungs and the crossing rule, in the streak's own unit. */
class MilestonesTest {

    @Test
    fun `each ladder is the PRD's`() {
        assertEquals(listOf(7, 30, 100), Milestones.ladder(weekly = false))
        assertEquals(listOf(4, 12, 52), Milestones.ladder(weekly = true))
    }

    @Test
    fun `reaching a rung exactly crosses it`() {
        assertEquals(7, Milestones.crossed(6, 7, weekly = false))
        assertEquals(30, Milestones.crossed(29, 30, weekly = false))
        assertEquals(100, Milestones.crossed(99, 100, weekly = false))
        assertEquals(4, Milestones.crossed(3, 4, weekly = true))
        assertEquals(12, Milestones.crossed(11, 12, weekly = true))
        assertEquals(52, Milestones.crossed(51, 52, weekly = true))
        assertEquals(7, Milestones.crossed(0, 7, weekly = false))
    }

    @Test
    fun `jumping past rungs reports the highest one crossed`() {
        assertEquals(7, Milestones.crossed(6, 8, weekly = false))
        assertEquals(30, Milestones.crossed(6, 31, weekly = false))
        assertEquals(100, Milestones.crossed(0, 100, weekly = false))
        assertEquals(52, Milestones.crossed(3, 60, weekly = true))
    }

    @Test
    fun `staying put, growing between rungs or falling crosses nothing`() {
        assertNull(Milestones.crossed(7, 7, weekly = false))
        assertNull(Milestones.crossed(7, 8, weekly = false))
        assertNull(Milestones.crossed(7, 6, weekly = false))
        assertNull(Milestones.crossed(1, 6, weekly = false))
        assertNull(Milestones.crossed(30, 0, weekly = false))
    }

    @Test
    fun `a rung on one ladder is no rung on the other`() {
        assertNull(Milestones.crossed(6, 7, weekly = true))
        assertNull(Milestones.crossed(3, 4, weekly = false))
    }
}
