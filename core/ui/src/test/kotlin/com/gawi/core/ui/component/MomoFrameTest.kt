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

    /**
     * The sparkles drift as well as pulse: an orbit of ±5 px over 6.3 s, three
     * times the pulse period so the two cycles never beat against each other
     * (docs/ux/momo.md §3).
     *
     * The periods are asserted through the behaviour that makes the ratio worth
     * having — the pair returns to where it started once per orbit and not
     * once per pulse — rather than by reading the two constants back.
     */
    @Test
    fun `the sparkles orbit three times slower than they pulse`() {
        val start = MomoFrame.at(Mood.THRIVING, 0f)
        val orbit = MomoFrame.at(Mood.THRIVING, 6.3f)
        assertEquals(start.sparkleDrift.x, orbit.sparkleDrift.x, 1e-3f)
        assertEquals(start.sparkleDrift.y, orbit.sparkleDrift.y, 1e-3f)

        // A pulse later it is a third of the way round, which is what stops the
        // two reading as one motion. A distance and not assertNotEquals: an
        // orbit that had wrongly taken the pulse's own period would come back
        // to within a rounding error of the start and satisfy that.
        val pulse = MomoFrame.at(Mood.THRIVING, 2.1f)
        val moved = kotlin.math.hypot(pulse.sparkleDrift.x - start.sparkleDrift.x, pulse.sparkleDrift.y - start.sparkleDrift.y)
        assertTrue("a pulse in, the drift had moved $moved px", moved > 1f)

        // Five px from the centre, wherever it is on the way round.
        listOf(0f, 1.1f, 3.15f, 5.4f).forEach { t ->
            val d = MomoFrame.at(Mood.THRIVING, t).sparkleDrift
            assertEquals("radius at t=$t", 5f, kotlin.math.hypot(d.x, d.y), 1e-3f)
        }
    }

    /** The second sparkle rides the same orbit, at the lag the pulse already uses. */
    @Test
    fun `the second sparkle drifts behind the first`() {
        val frame = MomoFrame.at(Mood.THRIVING, 0f)
        assertNotEquals(frame.sparkleDrift, frame.sparkleDriftLag)
        assertEquals(frame.sparkleDriftLag, MomoFrame.at(Mood.THRIVING, 0.7f).sparkleDrift)
    }

    /**
     * A mood change is one animal: the body's fields run from one mood's frame
     * to the other's, meeting each end exactly, while the face is the
     * destination's from the start (docs/ux/momo.md §3).
     */
    @Test
    fun `between meets both ends and averages the body in the middle`() {
        val from = MomoFrame.at(Mood.CONTENT, 1.1f)
        val to = MomoFrame.at(Mood.THRIVING, 1.1f)
        val start = MomoFrame.between(from, to, 0f)
        assertEquals(from.dy, start.dy)
        assertEquals(from.tilt, start.tilt)
        assertEquals(from.gills, start.gills)
        assertEquals(from.breatheY, start.breatheY)
        assertEquals(to.eyeOpen, start.eyeOpen)
        assertEquals(to, MomoFrame.between(from, to, 1f))
        val mid = MomoFrame.between(from, to, 0.5f)
        assertEquals((from.dy + to.dy) / 2f, mid.dy, 1e-6f)
        mid.gills.forEachIndexed { i, g -> assertEquals((from.gills[i] + to.gills[i]) / 2f, g, 1e-6f) }
    }

    @Test
    fun `between drains and undrains the colour gradually`() {
        val full = MomoFrame.at(Mood.CONTENT, 0f)
        val drained = MomoFrame.at(Mood.REGENERATING, 0f)
        val half = MomoFrame.between(full, drained, 0.5f).saturation
        assertTrue(half < full.saturation && half > drained.saturation)
        assertEquals(MomoMotion.WORRIED.gillDrop / 2f, MomoFrame.between(full, MomoFrame.at(Mood.WORRIED, 0f), 0.5f).gillDrop, 1e-6f)
    }

    @Test
    fun `a worried face keeps its bead while it fades out`() {
        val worried = MomoFrame.at(Mood.WORRIED, 1f)
        val content = MomoFrame.at(Mood.CONTENT, 1f)
        assertEquals(worried.bead, MomoFrame.between(worried, content, 0.3f).bead)
        assertEquals(worried.bead, MomoFrame.between(content, worried, 0.3f).bead)
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
