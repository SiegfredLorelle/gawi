package com.gawi.widget

import com.gawi.core.domain.mascot.Mood
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Momo widget's whole read is a pure function, so no Glance and no Robolectric. */
class MomoContentTest {

    /** The mood is Mascot's, so the widget's face and the Today screen's cannot disagree. */
    @Test
    fun `the content carries the mood the Today screen would show`() {
        assertEquals(Mood.CONTENT, todaySnapshot(habits = listOf(todayHabit())).toMomoContent().mood)
        assertEquals(Mood.THRIVING, todaySnapshot(habits = listOf(todayHabit(completedToday = true))).toMomoContent().mood)
    }

    @Test
    fun `no habits is empty, and still has a face`() {
        val content = todaySnapshot().toMomoContent()

        assertTrue(content.empty)
        assertEquals(Mood.CONTENT, content.mood)
    }

    @Test
    fun `any habit at all is not empty`() {
        assertFalse(todaySnapshot(habits = listOf(todayHabit(id = habitId(1)))).toMomoContent().empty)
    }

    /** Four moods, four words — a mapper that reused one would pass a weaker test. */
    @Test
    fun `every mood has its own word`() {
        assertEquals(Mood.entries.size, Mood.entries.map { it.caption() }.toSet().size)
    }
}
