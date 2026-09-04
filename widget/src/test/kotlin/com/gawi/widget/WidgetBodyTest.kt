package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which of the three things the widget draws, for each state it can be in.
 *
 * This is the branch that decides what a user sees when the database is broken
 * versus when they simply have no habits — the distinction `strings.xml` is
 * split to preserve and docs/ux/widget.md §4 spends a paragraph on. It used to
 * live inside the composable as two `Message` calls that could be swapped by a
 * one-character edit with nothing to catch it.
 *
 * Res ids are compared rather than resolved strings, so these assert the same
 * `R.string` constant the composable reads and no Android context is needed.
 *
 * Since Momo, the body also decides whether her face is drawn, from the size
 * the host reported — a value here rather than a branch in the composable, for
 * the same reason as the copy: so a one-character edit to the gate has a test to
 * fail. [SMALL] is the provider's minimum; [TALL] is two cells at the width the
 * canvas drew the large body at; [NARROW_TALL] is two cells at the provider's
 * minimum width, which is tall enough for a face and too narrow for the header
 * beside it (docs/ux/widget.md §7).
 */
class WidgetBodyTest {

    private companion object {
        val SMALL = DpSize(250.dp, 110.dp)
        val TALL = DpSize(250.dp, 220.dp)
        val NARROW_TALL = DpSize(180.dp, 220.dp)
    }

    @Test
    fun `a failed read draws the unavailable copy, not an empty list`() {
        assertEquals(WidgetBodyContent.Copy(R.string.widget_unavailable), WidgetContent.Unavailable.body(SMALL))
    }

    @Test
    fun `no habits draws the empty copy, which is not the failure copy`() {
        val body = WidgetContent.Ready(todaySnapshot().toWidgetState()).body(SMALL)

        assertEquals(WidgetBodyContent.Copy(R.string.widget_no_habits), body)
    }

    /**
     * The pair the whole extraction exists for. Stated as its own assertion
     * because the two copies being distinct is the property, not an accident of
     * which resource happens to be named first.
     */
    @Test
    fun `the empty copy and the failure copy are different strings`() {
        val empty = WidgetContent.Ready(todaySnapshot().toWidgetState()).body(SMALL)
        val failed = WidgetContent.Unavailable.body(SMALL)

        assertEquals(false, empty == failed)
    }

    @Test
    fun `habits draw as rows, in the order the query returned them`() {
        val snapshot = todaySnapshot(
            habits = listOf(todayHabit(id = habitId(1), name = "read"), todayHabit(id = habitId(2), name = "walk")),
        )

        val body = WidgetContent.Ready(snapshot.toWidgetState()).body(SMALL)

        assertEquals(listOf("read", "walk"), (body as WidgetBodyContent.Rows).rows.map { it.name })
    }

    /** The one-cell widget is the one docs/ux/widget.md §2 settled, and Momo does not change it. */
    @Test
    fun `at the provider's minimum height the rows have no face above them`() {
        val body = WidgetContent.Ready(todaySnapshot(habits = listOf(todayHabit())).toWidgetState()).body(SMALL)

        assertNull((body as WidgetBodyContent.Rows).mood)
    }

    @Test
    fun `two cells tall and narrow, the rows carry the mood the Today screen would show`() {
        val body = WidgetContent.Ready(todaySnapshot(habits = listOf(todayHabit(completedToday = true))).toWidgetState()).body(NARROW_TALL)

        assertEquals(Mood.THRIVING, (body as WidgetBodyContent.Rows).mood)
    }

    /** Two cells tall *and* wide enough for the header: the large body, with the same rows and the same mood. */
    @Test
    fun `two cells tall and wide, the large body carries the rows and the mood`() {
        val rows = listOf(todayHabit(id = habitId(1), name = "read", completedToday = true), todayHabit(id = habitId(2), name = "walk"))
        val body = WidgetContent.Ready(todaySnapshot(habits = rows).toWidgetState()).body(TALL)

        assertEquals(WidgetBodyContent.Large::class, body::class)
        assertEquals(listOf("read", "walk"), (body as WidgetBodyContent.Large).rows.map { it.name })
        assertEquals(listOf(true, false), body.rows.map { it.completed })
        assertEquals(Mood.CONTENT, body.mood)
    }

    /** Pinned at the edge, both sides, so the constant cannot drift by an off-by-one. */
    @Test
    fun `the height gate is the constant, inclusive`() {
        val ready = WidgetContent.Ready(todaySnapshot(habits = listOf(todayHabit())).toWidgetState())

        assertNull(ready.body(DpSize(180.dp, (MOMO_MIN_HEIGHT - 1).dp)).mood)
        assertEquals(Mood.CONTENT, ready.body(DpSize(180.dp, MOMO_MIN_HEIGHT.dp)).mood)
    }

    /**
     * Both gates, at both edges. Tall alone is the face above the rows; wide
     * alone is the rows alone; only both together is the header — the table
     * docs/ux/widget.md §7 states.
     */
    @Test
    fun `the large body needs both gates, each inclusive`() {
        val ready = WidgetContent.Ready(todaySnapshot(habits = listOf(todayHabit())).toWidgetState())

        assertEquals(WidgetBodyContent.Large::class, ready.body(DpSize(LARGE_MIN_WIDTH.dp, MOMO_MIN_HEIGHT.dp))::class)
        assertEquals(WidgetBodyContent.Rows::class, ready.body(DpSize((LARGE_MIN_WIDTH - 1).dp, MOMO_MIN_HEIGHT.dp))::class)
        assertEquals(WidgetBodyContent.Rows::class, ready.body(DpSize(LARGE_MIN_WIDTH.dp, (MOMO_MIN_HEIGHT - 1).dp))::class)
        assertNull(ready.body(DpSize(LARGE_MIN_WIDTH.dp, (MOMO_MIN_HEIGHT - 1).dp)).mood)
    }

    /** The band has nothing to weave with no habits, so the empty copy keeps the face-above form at every width. */
    @Test
    fun `no habits, tall and wide, is still the empty copy with a face`() {
        assertEquals(
            WidgetBodyContent.Copy(R.string.widget_no_habits, Mood.CONTENT),
            WidgetContent.Ready(todaySnapshot().toWidgetState()).body(TALL),
        )
    }

    /** Beside the no-habits copy the face is the empty state's, decorative; Mascot says CONTENT with no habits. */
    @Test
    fun `no habits, tall, draws the empty copy with a face above it`() {
        val body = WidgetContent.Ready(todaySnapshot().toWidgetState()).body(TALL)

        assertEquals(WidgetBodyContent.Copy(R.string.widget_no_habits, Mood.CONTENT), body)
    }

    /** Nothing was read, so there is no mood to draw — at any size. */
    @Test
    fun `a failed read never gets a face`() {
        assertNull(WidgetContent.Unavailable.body(TALL).mood)
    }

    /**
     * Nothing, deliberately. Loading is the first frame of a cold render and is
     * replaced as soon as the flow emits, so a line of copy here would be the
     * only text most renders ever showed.
     */
    @Test
    fun `loading draws nothing at all`() {
        assertEquals(WidgetBodyContent.Blank, WidgetContent.Loading.body(SMALL))
    }
}
