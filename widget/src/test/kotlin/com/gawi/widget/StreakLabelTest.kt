package com.gawi.widget

import com.gawi.core.ui.streak.StreakUi
import com.gawi.widget.testsupport.MIN_CONTRAST
import com.gawi.widget.testsupport.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate
import java.util.Locale

/**
 * The half of the streak widget that needs resources: wording, plurals, the date,
 * and the ink each unit is drawn in.
 *
 * Robolectric, because every assertion here is about what the *strings* resolve
 * to. Pinning the format strings against this file's own literals would only
 * restate `strings.xml`, so what is pinned instead is the behaviour that could
 * regress silently: that one is singular, that the compact and full forms differ,
 * that a spoken streak always names its unit, and that days and weeks never come
 * out identical.
 */
@RunWith(RobolectricTestRunner::class)
class StreakLabelTest {

    private val context = RuntimeEnvironment.getApplication()

    // -------- plurals --------

    @Test
    fun `one day is singular and two are not`() {
        assertEquals("1 day", StreakUi.Days(1).label(context, StreakLayout.Full))
        assertEquals("2 days", StreakUi.Days(2).label(context, StreakLayout.Full))
    }

    @Test
    fun `one week is singular and two are not`() {
        assertEquals("1 week", StreakUi.Weeks(1).label(context, StreakLayout.Full))
        assertEquals("2 weeks", StreakUi.Weeks(2).label(context, StreakLayout.Full))
    }

    // -------- compact versus full --------

    /**
     * The compact form is the one that has to survive at 180dp, and the `w` is
     * one of only two signals left at that size (the other is the colour role).
     */
    @Test
    fun `the compact form suffixes weeks and leaves days bare`() {
        assertEquals("12", StreakUi.Days(12).label(context, StreakLayout.Compact))
        assertEquals("3w", StreakUi.Weeks(3).label(context, StreakLayout.Compact))
    }

    /** The invariant, at the one size where the unit word is not there to carry it. */
    @Test
    fun `a day count and a week count never read the same, in either form`() {
        for (layout in StreakLayout.entries) {
            assertNotEquals(
                "a 5-day run and a 5-week run render identically in $layout",
                StreakUi.Days(5).label(context, layout),
                StreakUi.Weeks(5).label(context, layout),
            )
        }
    }

    @Test
    fun `a break reads as zero when compact and says what it cost when full`() {
        assertEquals("0", StreakUi.Broken(previous = 9, weekly = false).label(context, StreakLayout.Compact))
        assertEquals("was 9", StreakUi.Broken(previous = 9, weekly = false).label(context, StreakLayout.Full))
    }

    /**
     * A break keeps its unit, because "was 12" means twelve days or twelve weeks
     * and those are not the same loss. `StreakBadge` and `HabitDetailScreen` both
     * branch here; a widget that did not would contradict them and the invariant
     * this whole type exists for. Found by review, and it had been drawing both
     * the same.
     */
    @Test
    fun `a broken weekly run does not read like a broken daily one`() {
        assertNotEquals(
            StreakUi.Broken(previous = 12, weekly = false).label(context, StreakLayout.Full),
            StreakUi.Broken(previous = 12, weekly = true).label(context, StreakLayout.Full),
        )
    }

    /** Zero is zero in either unit, so the compact form has nothing to disambiguate. */
    @Test
    fun `the compact break is the same in both units, deliberately`() {
        assertEquals(
            StreakUi.Broken(previous = 12, weekly = false).label(context, StreakLayout.Compact),
            StreakUi.Broken(previous = 12, weekly = true).label(context, StreakLayout.Compact),
        )
    }

    /** "was 12w" reads out as "was 12 w", so the spoken form spells the unit. */
    @Test
    fun `a spoken break names its unit in words`() {
        assertEquals("was 12 days", StreakUi.Broken(previous = 12, weekly = false).spokenLabel(context))
        assertEquals("was 12 weeks", StreakUi.Broken(previous = 12, weekly = true).spokenLabel(context))
    }

    /** And it is a plural, because "was 1 days" is what lint caught here. */
    @Test
    fun `a spoken break of one is singular`() {
        assertEquals("was 1 day", StreakUi.Broken(previous = 1, weekly = false).spokenLabel(context))
        assertEquals("was 1 week", StreakUi.Broken(previous = 1, weekly = true).spokenLabel(context))
    }

    /** A zero is what a break reads. Never having started is not a break. */
    @Test
    fun `no history draws a dash, not a zero`() {
        val none = StreakUi.None.label(context, StreakLayout.Compact)
        assertEquals("—", none)
        assertNotEquals(StreakUi.Broken(previous = 1, weekly = false).label(context, StreakLayout.Compact), none)
    }

    // -------- what TalkBack hears --------

    /**
     * A spoken "12" cannot say whether it counts days or weeks, so the compact
     * form is a visual compression only.
     */
    @Test
    fun `a spoken streak names its unit even while the widget draws the compact form`() {
        assertEquals("12 days", StreakUi.Days(12).spokenLabel(context))
        assertEquals("3 weeks", StreakUi.Weeks(3).spokenLabel(context))
    }

    @Test
    fun `an empty history is spoken as words, not as a dash`() {
        val spoken = StreakUi.None.spokenLabel(context)
        assertTrue("the dash reached TalkBack: $spoken", !spoken.contains("—"))
        assertTrue("nothing was said for an empty streak", spoken.isNotEmpty())
    }

    // -------- the ink --------

    /** `StreakUi`'s third signal, and the one that has to hold in both schemes. */
    @Test
    fun `each unit is drawn in its own role, and every one of them is legible`() {
        val ground = WidgetPalette.surface.getColor(context)
        val inks = mapOf(
            "days" to StreakUi.Days(1).tint(),
            "weeks" to StreakUi.Weeks(1).tint(),
            "broken" to StreakUi.Broken(previous = 1, weekly = false).tint(),
            "none" to StreakUi.None.tint(),
        )
        assertNotEquals(
            "a day streak and a week streak share an ink, so the colour signal is gone",
            inks.getValue("days").getColor(context),
            inks.getValue("weeks").getColor(context),
        )
        for ((name, ink) in inks) {
            val ratio = contrastRatio(ink.getColor(context), ground)
            assertTrue("the $name ink is $ratio:1 on the widget ground, below $MIN_CONTRAST", ratio >= MIN_CONTRAST)
        }
    }

    /** A break and an empty history are both the absence of a run, so they match. */
    @Test
    fun `a break and an empty history share the muted ink`() {
        assertEquals(
            StreakUi.Broken(previous = 3, weekly = true).tint().getColor(context),
            StreakUi.None.tint().getColor(context),
        )
    }

    // -------- the date --------

    /**
     * §7.1's mandatory line. What is pinned is that the day, the month and the
     * weekday are all present and the year is not — not the exact punctuation,
     * which is the locale's business and would make this a test of the platform's
     * pattern data.
     */
    @Test
    fun `the as-of date names a weekday, a day and a month, and no year`() {
        val formatted = formatAsOf(LocalDate.parse("2026-08-29"), Locale.UK)
        assertTrue("no day-of-month in $formatted", formatted.contains("29"))
        assertTrue("no month in $formatted", formatted.contains("Aug"))
        assertTrue("no weekday in $formatted", formatted.contains("Sat"))
        assertTrue("the year is noise on a date this recent: $formatted", !formatted.contains("2026"))
    }

    @Test
    fun `two different dates format differently`() {
        assertNotEquals(
            formatAsOf(LocalDate.parse("2026-08-29"), Locale.UK),
            formatAsOf(LocalDate.parse("2026-08-30"), Locale.UK),
        )
    }
}
