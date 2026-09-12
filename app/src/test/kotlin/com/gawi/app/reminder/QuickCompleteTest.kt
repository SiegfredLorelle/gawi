package com.gawi.app.reminder

import com.gawi.core.data.reminder.OutstandingHabit
import com.gawi.core.data.reminder.ReminderDecision
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import com.gawi.core.testing.FakeHabitRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What one quick-complete button does, as assertions.
 *
 * No Robolectric and no broadcast: [quickComplete] takes its collaborators, so
 * every rule docs/ux/reminder.md §4 states about a tap is reachable on the JVM.
 * `QuickCompleteReceiver.onReceive` is the untestable half, the way
 * `ToggleHabitAction.onAction` is in `:widget`.
 *
 * **Every fake here forbids `observeToday`**, which is the strongest form the
 * carried-date rule can be stated in: a tap that resolved its own "today" would
 * have to read the repository to do it, and reading it fails the test rather
 * than producing a date that happens to match.
 */
class QuickCompleteTest {

    private val read = OutstandingHabit(habitId(1), "read")
    private val swim = OutstandingHabit(habitId(2), "swim")

    private var reposted: ReminderDecision.Remind? = null
    private var cancelled = false

    private fun habits() = FakeHabitRepository(unreachable = setOf("observeToday"))

    private suspend fun tap(
        habits: FakeHabitRepository,
        habit: OutstandingHabit,
        outstanding: List<OutstandingHabit>,
        logicalDate: LocalDate = FIXED_DATE,
    ) = quickComplete(
        habits = habits,
        repost = { reposted = it },
        cancel = { cancelled = true },
        request = QuickCompleteRequest(habit.id.value, logicalDate, outstanding, total = 5),
    )

    /** The rule §4 calls the one correctness rule rather than a design choice. */
    @Test
    fun `the completion lands on the date the button carried`() = runTest {
        val habits = habits()

        tap(habits, read, listOf(read, swim), logicalDate = FIXED_DATE.minusDays(1))

        assertEquals(FIXED_DATE.minusDays(1), habits.completions.single().logicalDate)
        assertEquals(read.id, habits.completions.single().habitId)
    }

    /** It completes; it never toggles. Nothing about a tap can remove a completion. */
    @Test
    fun `nothing is undone`() = runTest {
        val habits = habits()

        tap(habits, read, listOf(read, swim))

        assertTrue(habits.undone.isEmpty())
    }

    @Test
    fun `the re-post drops the habit that was tapped and keeps the rest`() = runTest {
        tap(habits(), read, listOf(read, swim))

        assertEquals(ReminderDecision.Remind(FIXED_DATE, listOf(swim), total = 5), reposted)
        assertTrue("the notification was taken down instead of re-posted", !cancelled)
    }

    /** Nothing left for it to say, so the notification goes rather than saying zero. */
    @Test
    fun `the last outstanding habit takes the notification down`() = runTest {
        tap(habits(), read, listOf(read))

        assertTrue(cancelled)
        assertNull(reposted)
    }

    /**
     * A button whose habit is no longer in the carried list still writes.
     *
     * The tapped id is what the user pressed, so it is the authority; the list is
     * only what the shade should say next. Refusing the write because the two
     * disagreed would lose a completion the user asked for.
     */
    @Test
    fun `a habit missing from the carried list is still completed`() = runTest {
        val habits = habits()

        tap(habits, read, listOf(swim))

        assertEquals(read.id, habits.completions.single().habitId)
        assertEquals(listOf(swim), reposted?.outstanding)
    }
}
