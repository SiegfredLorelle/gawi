package com.gawi.feature.today

import com.gawi.core.domain.mascot.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the day is celebrated and what the celebration is, as docs/ux/momo.md
 * §6 states it. Plain JVM: the trigger is a function of two moods and the
 * frame a function of progress, so nothing here needs a composition —
 * `CelebrationRenderTest` draws the pixels and `TodayScreenTest` holds the
 * animations-off path.
 */
class CelebrationFrameTest {

    @Test
    fun `entering thriving from any other mood celebrates`() {
        Mood.entries.filter { it != Mood.THRIVING }.forEach { from ->
            assertTrue("$from -> thriving", celebrates(from, Mood.THRIVING))
        }
    }

    @Test
    fun `a first composition, staying thriving, or leaving it never celebrates`() {
        assertFalse(celebrates(null, Mood.THRIVING))
        assertFalse(celebrates(Mood.THRIVING, Mood.THRIVING))
        Mood.entries.filter { it != Mood.THRIVING }.forEach { to ->
            assertFalse("thriving -> $to", celebrates(Mood.THRIVING, to))
            assertFalse("content -> $to", celebrates(Mood.CONTENT, to))
        }
    }

    @Test
    fun `the run starts still, hops, glows and ends with nothing`() {
        val start = CelebrationFrame.at(0f)
        assertEquals(0f, start.hop)
        assertEquals(0f, start.glow)
        assertTrue(start.bubbles.isEmpty())
        assertFalse(start.isOver)
        val peak = CelebrationFrame.at(0.28f)
        assertEquals(14f, peak.hop, 1e-6f)
        assertTrue(CelebrationFrame.at(0.30f).glow > CelebrationFrame.at(0.6f).glow)
        assertEquals(0f, CelebrationFrame.at(0.52f).hop, 1e-5f)
        assertEquals(CelebrationFrame.NONE, CelebrationFrame.at(1f))
        assertTrue(CelebrationFrame.at(1f).isOver)
    }

    @Test
    fun `the burst staggers fourteen bubbles and every one is gone by the end`() {
        assertTrue(CelebrationFrame.at(0.05f).bubbles.size in 1 until 14)
        assertEquals(14, CelebrationFrame.at(0.5f).bubbles.size)
        assertEquals(14, CelebrationFrame.at(0.5f).bubbles.map { it.lane }.toSet().size)
        assertTrue(CelebrationFrame.at(0.999f).bubbles.all { it.alpha < 0.05f })
        assertEquals(CelebrationFrame.at(0.4f), CelebrationFrame.at(0.4f))
    }
}
