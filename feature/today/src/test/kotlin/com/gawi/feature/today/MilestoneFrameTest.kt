package com.gawi.feature.today

import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.streak.StreakUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a milestone fires and what the celebration is, as docs/ux/momo.md §6
 * states it. Plain JVM, like `CelebrationFrameTest`: the trigger is a function
 * of two streaks and the frame a function of progress — `MilestoneRenderTest`
 * draws the pixels and `TodayScreenTest` holds the copy line and the badge.
 */
class MilestoneFrameTest {

    @Test
    fun `crossing a rung fires it, from the count below or from nothing`() {
        assertEquals(7, milestoneCrossed(StreakUi.Days(6), StreakUi.Days(7)))
        assertEquals(30, milestoneCrossed(StreakUi.Days(29), StreakUi.Days(30)))
        assertEquals(100, milestoneCrossed(StreakUi.Days(99), StreakUi.Days(100)))
        assertEquals(7, milestoneCrossed(StreakUi.None, StreakUi.Days(7)))
        assertEquals(7, milestoneCrossed(StreakUi.Broken(previous = 6, weekly = false), StreakUi.Days(7)))
        assertEquals(4, milestoneCrossed(StreakUi.Weeks(3), StreakUi.Weeks(4)))
        assertEquals(12, milestoneCrossed(StreakUi.Weeks(11), StreakUi.Weeks(12)))
        assertEquals(52, milestoneCrossed(StreakUi.Weeks(51), StreakUi.Weeks(52)))
    }

    @Test
    fun `jumping past a rung celebrates the highest one crossed`() {
        assertEquals(7, milestoneCrossed(StreakUi.Days(6), StreakUi.Days(8)))
        assertEquals(30, milestoneCrossed(StreakUi.Days(6), StreakUi.Days(31)))
        assertEquals(100, milestoneCrossed(StreakUi.None, StreakUi.Days(100)))
    }

    @Test
    fun `a first sighting, staying put, growing between rungs or falling never fires`() {
        assertNull(milestoneCrossed(null, StreakUi.Days(7)))
        assertNull(milestoneCrossed(StreakUi.Days(7), StreakUi.Days(7)))
        assertNull(milestoneCrossed(StreakUi.Days(7), StreakUi.Days(8)))
        assertNull(milestoneCrossed(StreakUi.Days(7), StreakUi.Days(6)))
        assertNull(milestoneCrossed(StreakUi.Days(7), StreakUi.Broken(previous = 7, weekly = false)))
        assertNull(milestoneCrossed(StreakUi.Days(7), StreakUi.None))
        assertNull(milestoneCrossed(StreakUi.Days(1), StreakUi.Days(6)))
    }

    @Test
    fun `the two ladders never meet`() {
        // Days(7) after Weeks(3) is a schedule edit, not seven days earned; and 4 is no rung for days.
        assertNull(milestoneCrossed(StreakUi.Weeks(3), StreakUi.Days(7)))
        assertNull(milestoneCrossed(StreakUi.Days(3), StreakUi.Weeks(4)))
        assertNull(milestoneCrossed(StreakUi.Days(3), StreakUi.Days(4)))
        assertNull(milestoneCrossed(StreakUi.Weeks(6), StreakUi.Weeks(7)))
    }

    @Test
    fun `rows are judged against what was seen for them, and unseen rows never fire`() {
        val seen = mapOf(READ.id to StreakUi.Days(6), WALK.id to StreakUi.Weeks(3))
        val fired = milestonesIn(listOf(READ.copy(streak = StreakUi.Days(7)), WALK.copy(streak = StreakUi.Weeks(4)), NEW), seen)
        assertEquals(listOf(Milestone(READ.id, 7, weekly = false), Milestone(WALK.id, 4, weekly = true)), fired)
        assertTrue(milestonesIn(listOf(READ.copy(streak = StreakUi.Days(7))), emptyMap()).isEmpty())
        assertEquals(0, Milestone(READ.id, 7, weekly = false).rank)
        assertEquals(2, Milestone(WALK.id, 52, weekly = true).rank)
    }

    @Test
    fun `the run starts still, hops twice, glows and ends with nothing`() {
        val start = MilestoneFrame.at(0f)
        assertEquals(0f, start.hop)
        assertEquals(0f, start.glow)
        assertTrue(start.bubbles.isEmpty())
        assertTrue(start.ring.isEmpty())
        assertEquals(1f, start.badgeScale)
        assertFalse(start.isOver)
        assertEquals(18f, MilestoneFrame.at(0.14f).hop, 1e-5f)
        assertEquals(0f, MilestoneFrame.at(0.28f).hop, 1e-5f)
        assertEquals(14.4f, MilestoneFrame.at(0.38f).hop, 1e-5f)
        assertEquals(0f, MilestoneFrame.at(0.52f).hop, 1e-5f)
        assertEquals(0.30f, MilestoneFrame.at(0.16f).glow, 1e-6f)
        assertTrue(MilestoneFrame.at(0.16f).glow > MilestoneFrame.at(0.50f).glow)
        assertEquals(MilestoneFrame.NONE, MilestoneFrame.at(1f))
        assertTrue(MilestoneFrame.at(1f).isOver)
    }

    @Test
    fun `the ring opens after the second hop and is gone before the end`() {
        assertTrue(MilestoneFrame.at(0.29f).ring.isEmpty())
        val open = MilestoneFrame.at(0.42f).ring
        assertEquals(8, open.size)
        assertEquals(8, open.map { it.angleDegrees }.toSet().size)
        assertEquals(1f, open.first().alpha, 1e-6f)
        assertTrue(MilestoneFrame.at(0.6f).ring.first().radius > open.first().radius)
        assertTrue(MilestoneFrame.at(0.86f).ring.isEmpty())
    }

    @Test
    fun `the badge swells twice in the first half and the burst is wider than the day's`() {
        assertEquals(1.28f, MilestoneFrame.at(0.10f).badgeScale, 1e-6f)
        assertEquals(1f, MilestoneFrame.at(0.24f).badgeScale, 1e-6f)
        assertEquals(1.12f, MilestoneFrame.at(0.40f).badgeScale, 1e-6f)
        assertEquals(1f, MilestoneFrame.at(0.55f).badgeScale, 1e-6f)
        assertTrue(MilestoneFrame.at(0.05f).bubbles.size in 1 until 22)
        assertEquals(22, MilestoneFrame.at(0.5f).bubbles.size)
        assertTrue(MilestoneFrame.at(0.999f).bubbles.all { it.alpha < 0.05f })
        assertTrue(MilestoneFrame.MILLIS > CelebrationFrame.MILLIS)
        assertEquals(MilestoneFrame.at(0.4f), MilestoneFrame.at(0.4f))
    }

    private companion object {
        val READ =
            HabitRowUi(
                HabitId("00000000-0000-7000-8000-000000000001"),
                "read",
                "R",
                null,
                completed = true,
                weekProgress = null,
                streak = StreakUi.Days(7),
            )
        val WALK =
            HabitRowUi(
                HabitId("00000000-0000-7000-8000-000000000002"),
                "walk",
                "W",
                null,
                completed = true,
                weekProgress = null,
                streak = StreakUi.Weeks(4),
            )
        val NEW =
            HabitRowUi(
                HabitId("00000000-0000-7000-8000-000000000003"),
                "new",
                "N",
                null,
                completed = true,
                weekProgress = null,
                streak = StreakUi.Days(30),
            )
    }
}
