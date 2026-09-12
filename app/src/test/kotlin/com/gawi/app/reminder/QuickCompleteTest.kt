package com.gawi.app.reminder

import com.gawi.core.data.reminder.OutstandingHabit
import com.gawi.core.data.reminder.ReminderDecision
import com.gawi.core.domain.command.CommandError
import com.gawi.core.domain.command.CommandResult
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
 * than producing a date that happens to match. The read it *is* allowed —
 * `observeCompletionDatesByHabit` — takes the date it is given and resolves
 * none.
 */
class QuickCompleteTest {

    private val read = OutstandingHabit(habitId(1), "read")
    private val swim = OutstandingHabit(habitId(2), "swim")
    private val journal = OutstandingHabit(habitId(3), "journal")

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
     * The two-taps-in-flight case, and the reason ordering alone would not have
     * fixed it: this button's list was filled in before the other tap wrote, so
     * it still names a habit that is now done. The log is what settles it.
     */
    @Test
    fun `a habit completed by someone else loses its button`() = runTest {
        val habits = habits()
        habits.completionsByHabit = mapOf(swim.id to setOf(FIXED_DATE))

        tap(habits, read, listOf(read, swim, journal))

        assertEquals(listOf(journal), reposted?.outstanding)
    }

    /** Every carried habit already done, so there is nothing left to say. */
    @Test
    fun `a day the log says is finished takes the notification down`() = runTest {
        val habits = habits()
        habits.completionsByHabit = mapOf(swim.id to setOf(FIXED_DATE))

        tap(habits, read, listOf(read, swim))

        assertTrue(cancelled)
        assertNull(reposted)
    }

    /**
     * The read asks about the day the button carried, not about today.
     *
     * The fixture date is deliberately not today's, so swapping the carried date
     * for a resolved one reddens this rather than passing by coincidence.
     */
    @Test
    fun `the log is asked about the carried date`() = runTest {
        val habits = habits()

        tap(habits, read, listOf(read, swim), logicalDate = FIXED_DATE.minusDays(1))

        assertEquals(listOf(FIXED_DATE.minusDays(1)..FIXED_DATE.minusDays(1)), habits.ranges)
    }

    /**
     * A refused write must not look like a successful one.
     *
     * The reachable case is a notification that outlived architecture §5's
     * three-day retroactive window: the domain refuses the date, and dropping
     * the button anyway would say the habit was logged when nothing was.
     */
    @Test
    fun `a refused write leaves the notification alone`() = runTest {
        val habits = habits()
        habits.result = CommandResult.Rejected(CommandError.RetroWindowExceeded)

        tap(habits, read, listOf(read, swim))

        assertNull(reposted)
        assertTrue("the notification was taken down on a refused write", !cancelled)
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
