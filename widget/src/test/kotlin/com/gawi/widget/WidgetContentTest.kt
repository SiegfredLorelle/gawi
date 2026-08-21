package com.gawi.widget

import com.gawi.widget.testsupport.FakeHabitRepository
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * The widget's read, including the branch that only a failure reaches.
 *
 * Worth its own file because the `catch` is the half of this flow that no device
 * check will exercise on purpose: it needs the database or the settings store to
 * fail, which is exactly what `FakeHabitRepository(failWith = …)` is for.
 */
class WidgetContentTest {

    @Test
    fun `a good read maps the snapshot to rows`() {
        runTest {
            val habits = FakeHabitRepository(todaySnapshot(habits = listOf(todayHabit(id = habitId(1), name = "read"))))

            val content = habits.widgetContent().first()

            assertEquals(listOf("read"), (content as WidgetContent.Ready).state.rows.map { it.name })
        }
    }

    /**
     * The failure resolves to `Unavailable` rather than to an empty list, which
     * is what stops a broken database from looking like a user with no habits.
     * `SQLiteException` is a `RuntimeException` unrelated to `IOException`, so
     * both kinds have to be absorbed — the `catch` is untyped for that reason.
     */
    @Test
    fun `a failed read becomes unavailable, not an empty list`() {
        runTest {
            val habits = FakeHabitRepository(failWith = IOException("disk"))

            assertEquals(WidgetContent.Unavailable, habits.widgetContent().first())
        }
    }

    @Test
    fun `a runtime failure is absorbed too, not only an IOException`() {
        runTest {
            val habits = FakeHabitRepository(failWith = IllegalStateException("corrupt"))

            assertEquals(WidgetContent.Unavailable, habits.widgetContent().first())
        }
    }
}
