package com.gawi.widget

import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import org.junit.Assert.assertEquals
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
 */
class WidgetBodyTest {

    @Test
    fun `a failed read draws the unavailable copy, not an empty list`() {
        assertEquals(WidgetBodyContent.Copy(R.string.widget_unavailable), WidgetContent.Unavailable.body())
    }

    @Test
    fun `no habits draws the empty copy, which is not the failure copy`() {
        val body = WidgetContent.Ready(todaySnapshot().toWidgetState()).body()

        assertEquals(WidgetBodyContent.Copy(R.string.widget_no_habits), body)
    }

    /**
     * The pair the whole extraction exists for. Stated as its own assertion
     * because the two copies being distinct is the property, not an accident of
     * which resource happens to be named first.
     */
    @Test
    fun `the empty copy and the failure copy are different strings`() {
        val empty = WidgetContent.Ready(todaySnapshot().toWidgetState()).body()
        val failed = WidgetContent.Unavailable.body()

        assertEquals(false, empty == failed)
    }

    @Test
    fun `habits draw as rows, in the order the query returned them`() {
        val snapshot = todaySnapshot(
            habits = listOf(todayHabit(id = habitId(1), name = "read"), todayHabit(id = habitId(2), name = "walk")),
        )

        val body = WidgetContent.Ready(snapshot.toWidgetState()).body()

        assertEquals(listOf("read", "walk"), (body as WidgetBodyContent.Rows).rows.map { it.name })
    }

    /**
     * Nothing, deliberately. Loading is the first frame of a cold render and is
     * replaced as soon as the flow emits, so a line of copy here would be the
     * only text most renders ever showed.
     */
    @Test
    fun `loading draws nothing at all`() {
        assertEquals(WidgetBodyContent.Blank, WidgetContent.Loading.body())
    }
}
