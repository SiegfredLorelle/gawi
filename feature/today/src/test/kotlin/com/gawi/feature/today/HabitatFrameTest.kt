package com.gawi.feature.today

import com.gawi.core.domain.mascot.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tank life's frame maths — what moves when — as docs/ux/momo.md §4 states
 * it. Plain JVM: the frame is a pure function of the mood and the clock, so
 * nothing here needs a bitmap; `HabitatRenderTest` draws the pixels.
 */
class HabitatFrameTest {

    @Test
    fun `the same mood at the same second is the same frame`() {
        Mood.entries.forEach { mood ->
            assertEquals(HabitatFrame.at(mood, 0f), HabitatFrame.at(mood, 0f))
            assertEquals(HabitatFrame.at(mood, 2.3f), HabitatFrame.at(mood, 2.3f))
        }
    }

    @Test
    fun `four weeds sway out of phase, and only regenerating leans and drains`() {
        Mood.entries.forEach { mood ->
            val frame = HabitatFrame.at(mood, 0f)
            assertEquals(4, frame.weeds.size)
            assertEquals("$mood weeds move as one", 4, frame.weeds.toSet().size)
            val regenerating = mood == Mood.REGENERATING
            if (regenerating) assertTrue(frame.droop > 0f) else assertEquals(0f, frame.droop)
            assertEquals(if (regenerating) 1f else 0f, frame.drained)
        }
    }

    @Test
    fun `a faster tempo completes a sway sooner`() {
        // One full period at tempo 1 is the canvas's 5.2 s; thriving's 0.6 gets
        // there in 3.12 s and content has not.
        val period = HabitatFrame.WEED_PERIOD
        val thrivingLater = HabitatFrame.at(Mood.THRIVING, period * 0.6f)
        val thrivingStart = HabitatFrame.at(Mood.THRIVING, 0f)
        thrivingLater.weeds.zip(thrivingStart.weeds) { a, b -> assertEquals(a, b, 1e-3f) }
        val contentLater = HabitatFrame.at(Mood.CONTENT, period * 0.6f)
        val contentStart = HabitatFrame.at(Mood.CONTENT, 0f)
        assertTrue(contentLater.weeds.zip(contentStart.weeds).any { (a, b) -> kotlin.math.abs(a - b) > 1f })
    }

    @Test
    fun `bubbles rise on four lanes except while regenerating`() {
        assertEquals(4, HabitatFrame.at(Mood.CONTENT, 1f).bubbles.size)
        assertEquals(listOf(0, 1, 2, 3), HabitatFrame.at(Mood.CONTENT, 1f).bubbles.map { it.lane })
        assertTrue(HabitatFrame.at(Mood.REGENERATING, 1f).bubbles.isEmpty())
        // Progress advances with the clock and wraps.
        val early = HabitatFrame.at(Mood.CONTENT, 0.5f).bubbles[0].progress
        val later = HabitatFrame.at(Mood.CONTENT, 1.5f).bubbles[0].progress
        assertTrue(later > early)
        assertTrue(HabitatFrame.at(Mood.CONTENT, 7.4f).bubbles[0].progress < early)
    }

    @Test
    fun `a bubble fades in, dims, and is gone at the top`() {
        fun alphaAt(p: Float) = HabitatFrame.at(Mood.CONTENT, p * 7.4f).bubbles[0].alpha
        assertEquals(0f, alphaAt(0f), 1e-6f)
        assertTrue(alphaAt(0.12f) > alphaAt(0.05f))
        assertTrue(alphaAt(0.5f) < alphaAt(0.12f))
        assertTrue(alphaAt(0.99f) < alphaAt(0.8f))
    }

    @Test
    fun `between interpolates the weeds and crossfades the bubbles`() {
        val from = HabitatFrame.at(Mood.CONTENT, 2f)
        val to = HabitatFrame.at(Mood.REGENERATING, 2f)
        val start = HabitatFrame.between(from, to, 0f)
        assertEquals(from.weeds, start.weeds)
        assertEquals(from.droop, start.droop)
        assertEquals(to, HabitatFrame.between(from, to, 1f).copy(bubbles = to.bubbles))
        val mid = HabitatFrame.between(from, to, 0.5f)
        assertTrue(mid.droop > from.droop && mid.droop < to.droop)
        assertEquals(0.5f, mid.drained, 1e-6f)
        // Content's four bubbles at half strength; regenerating brings none.
        assertEquals(4, mid.bubbles.size)
        mid.bubbles.zip(from.bubbles) { a, b -> assertEquals(b.alpha / 2f, a.alpha, 1e-6f) }
        // Two bubbling moods overlap during the change: eight bubbles, not four.
        val both = HabitatFrame.between(from, HabitatFrame.at(Mood.THRIVING, 2f), 0.5f)
        assertEquals(8, both.bubbles.size)
    }
}
