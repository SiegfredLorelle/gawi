package com.gawi.core.ui.component

import com.gawi.core.domain.mascot.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame maths — what moves when — as docs/ux/momo.md §3 states it.
 *
 * Plain JVM, beside the geometry it covers, so the coverage travels with
 * `:core:ui` rather than with one consumer: `:widget` will draw
 * [MomoFrame.rest] and must not depend on `:feature:today`, where the pixel
 * tests have to live because pixels need Robolectric and this module does not
 * take it (`MomoRenderTest` says why).
 */
class MomoFrameTest {

    @Test
    fun `the same mood at the same second is the same frame`() {
        Mood.entries.forEach { mood ->
            assertEquals(MomoFrame.at(mood, 0f), MomoFrame.at(mood, 0f))
            assertEquals(MomoFrame.at(mood, 1.37f), MomoFrame.at(mood, 1.37f))
        }
    }

    @Test
    fun `worried fidgets in place and content floats`() {
        val worried = MomoFrame.at(Mood.WORRIED, 0.425f)
        val content = MomoFrame.at(Mood.CONTENT, 2.1f)
        assertTrue(worried.dx != 0f)
        assertEquals(0f, content.dx)
        assertTrue(content.dy < 0f)
        assertEquals(MomoMotion.WORRIED.gillDrop, worried.gillDrop)
        assertEquals(0f, content.gillDrop)
    }

    @Test
    fun `only moods with a blink period blink`() {
        val mid = 0.97f * MomoMotion.CONTENT.blinkPeriod!!
        assertTrue(MomoFrame.at(Mood.CONTENT, mid).eyeOpen < 1f)
        assertEquals(1f, MomoFrame.at(Mood.THRIVING, mid).eyeOpen)
    }

    @Test
    fun `the six gills start out of phase with each other`() {
        val gills = MomoFrame.at(Mood.CONTENT, 0f).gills
        assertEquals(6, gills.size)
        assertEquals(6, gills.toSet().size)
    }

    /**
     * The second sparkle lags the first by a third of a cycle and moves as
     * smoothly. The first cut derived it from the first sparkle's *value* with a
     * modulo, which snapped twice a cycle wherever the value crossed a third;
     * this walks the whole cycle in small steps and refuses any jump.
     */
    @Test
    fun `the second sparkle lags smoothly`() {
        val step = 1f / 60f
        var previous = MomoFrame.at(Mood.THRIVING, 0f)
        var t = step
        while (t < 2.2f) {
            val frame = MomoFrame.at(Mood.THRIVING, t)
            val jump = kotlin.math.abs(frame.sparkleLag - previous.sparkleLag)
            assertTrue("sparkleLag jumped by $jump at t=$t", jump < 0.06f)
            previous = frame
            t += step
        }
        // And it is a lag, not a copy: at t = 0 the first sparkle is at its
        // trough and the second is well up its curve.
        val rest = MomoFrame.at(Mood.THRIVING, 0f)
        assertEquals(0f, rest.sparkle, 1e-6f)
        assertNotEquals(rest.sparkle, rest.sparkleLag)
    }

    @Test
    fun `saturated keeps the encoded lightness`() {
        val body = MomoPalette.Body
        val drained = body.saturated(MomoMotion.REGENERATING.saturation)
        fun grey(c: androidx.compose.ui.graphics.Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
        assertEquals(grey(body), grey(drained), 0.002f)
        assertTrue(spread(drained) < spread(body))
    }

    private fun spread(c: androidx.compose.ui.graphics.Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
}
