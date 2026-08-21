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

    /**
     * The reason the read retries at all. `catch` terminates a flow, so without
     * a retry one transient throw would end collection for the life of the
     * Glance session — and the push cannot repair that, because `update` on a
     * live session never re-enters `provideGlance`. A screen recovers when its
     * `WhileSubscribed` window lapses; nothing does that for a widget.
     */
    @Test
    fun `a transient failure recovers instead of sticking on unavailable`() {
        runTest {
            val habits = FakeHabitRepository(
                todaySnapshot(habits = listOf(todayHabit(id = habitId(1), name = "read"))),
                failWith = IllegalStateException("transient"),
                failTimes = 2,
            )

            val content = habits.widgetContent().first()

            assertEquals(listOf("read"), (content as WidgetContent.Ready).state.rows.map { it.name })
            assertEquals("two failures then a success is three reads", 3, habits.reads)
        }
    }

    /**
     * The far edge of the retry, and the near edge of giving up.
     *
     * Written with **literal** numbers rather than against `READ_RETRIES`,
     * because a boundary test expressed as `CONSTANT ± 1` moves with the
     * constant and pins nothing — this project has already been caught by
     * exactly that. `retryWhen`'s `attempt` is zero-based, so three retries mean
     * **four** reads, and these two cases are what say so.
     */
    @Test
    fun `three failures still recover, on the fourth read`() {
        runTest {
            val habits = FakeHabitRepository(
                todaySnapshot(habits = listOf(todayHabit(id = habitId(1), name = "read"))),
                failWith = IllegalStateException("transient"),
                failTimes = 3,
            )

            val content = habits.widgetContent().first()

            assertEquals(listOf("read"), (content as WidgetContent.Ready).state.rows.map { it.name })
            assertEquals("the first read plus three retries", 4, habits.reads)
        }
    }

    @Test
    fun `a fourth failure gives up rather than retrying on`() {
        runTest {
            val habits = FakeHabitRepository(
                failWith = IllegalStateException("persistent"),
                failTimes = 4,
            )

            assertEquals(WidgetContent.Unavailable, habits.widgetContent().first())
            assertEquals("it must stop at four reads, not keep going", 4, habits.reads)
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
