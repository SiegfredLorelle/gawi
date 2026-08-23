package com.gawi.core.data.repository

import app.cash.turbine.test
import com.gawi.core.data.db.entity.CompletionEntity
import com.gawi.core.data.db.entity.HabitEntity
import com.gawi.core.data.model.TagEffort
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.data.testsupport.metadata
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The tag effort distribution's read (docs/ux/insights.md §5).
 *
 * The assertions worth reading twice are the two that pin what the query does
 * *not* drop: untagged habits stay a row, and archiving a habit does not
 * retroactively erase the effort it holds.
 */
@RunWith(RobolectricTestRunner::class)
class TagEffortQueryTest {

    private lateinit var store: TestStore

    @Before
    fun setUp() {
        store = TestStore.create()
    }

    @After
    fun tearDown() = store.close()

    private suspend fun createHabit(name: String, tag: String?): HabitId =
        (store.repository.createHabit(metadata(name, tag = tag)) as CommandResult.Accepted).payload

    /** The whole of recorded time, so a test only opts into range filtering when it means to. */
    private suspend fun effort(from: LocalDate = LocalDate.parse("2000-01-01"), to: LocalDate = LocalDate.parse("2100-01-01")) =
        store.repository.observeTagEffort(from, to).first()

    @Test
    fun `completions are totalled per tag across habits`() = runTest {
        val run = createHabit("run", tag = "health")
        val stretch = createHabit("stretch", tag = "health")
        val invoices = createHabit("invoices", tag = "work")
        val today = store.today()
        store.repository.addCompletion(run, today)
        store.repository.addCompletion(stretch, today)
        store.repository.addCompletion(invoices, today)

        assertEquals(
            listOf(TagEffort("health", 2), TagEffort("work", 1)),
            effort(),
        )
    }

    @Test
    fun `untagged habits are a row rather than a silent omission`() = runTest {
        val tagged = createHabit("run", tag = "health")
        val untagged = createHabit("floss", tag = null)
        val today = store.today()
        store.repository.addCompletion(tagged, today)
        store.repository.addCompletion(untagged, today)

        assertEquals(
            "dropping untagged would leave the shares describing a whole that is not the whole",
            listOf(TagEffort("health", 1), TagEffort(null, 1)),
            effort().sortedBy { it.tag ?: "zzz" },
        )
    }

    @Test
    fun `archiving a habit does not erase the effort it already holds`() = runTest {
        val habit = createHabit("run", tag = "health")
        store.repository.addCompletion(habit, store.today())
        val before = effort()

        // Asserted, not discarded: without this the test passes just as well on
        // an archive that silently did nothing, which is the shape of assertion
        // the neighbouring query tests are safe from only because they go on to
        // assert the habit disappeared. This one asserts the opposite.
        assertTrue(store.repository.archiveHabit(habit) is CommandResult.Accepted)
        assertTrue("the habit really did leave the active view", store.repository.observeToday().first().habits.isEmpty())

        assertEquals(before, effort())
        assertEquals(listOf(TagEffort("health", 1)), effort())
    }

    @Test
    fun `one human tag is one slice, whatever case or padding it was typed in`() = runTest {
        val a = createHabit("run", tag = "health")
        val b = createHabit("stretch", tag = "Health")
        val c = createHabit("walk", tag = "health ")
        val today = store.today()
        store.repository.addCompletion(a, today)
        store.repository.addCompletion(b, today)
        store.repository.addCompletion(c, today)

        // Tags are unnormalized free text — the editor stores `tag.ifBlank { null }`
        // with no trim and no case fold — so SQLite's default BINARY grouping
        // would split these into three slices, each understating the real share.
        val rows = effort()

        assertEquals("three spellings of one tag are one slice", 1, rows.size)
        assertEquals(3, rows.single().completions)
        assertEquals("the representative is deterministic, not whichever row won", "Health", rows.single().tag)
    }

    @Test
    fun `genuinely different tags still separate`() = runTest {
        val a = createHabit("run", tag = "health")
        val b = createHabit("invoices", tag = "work")
        val today = store.today()
        store.repository.addCompletion(a, today)
        store.repository.addCompletion(b, today)

        assertEquals(2, effort().size)
    }

    @Test
    fun `untagged habits do not fold into a tagged slice`() = runTest {
        val tagged = createHabit("run", tag = "health")
        val blank = createHabit("floss", tag = null)
        val today = store.today()
        store.repository.addCompletion(tagged, today)
        store.repository.addCompletion(blank, today)

        val rows = effort()

        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.tag == null })
    }

    @Test
    fun `completions outside the range are not counted`() = runTest {
        val habit = createHabit("run", tag = "health")
        val today = store.today()
        store.repository.addCompletion(habit, today)
        store.repository.addCompletion(habit, today.minusDays(2))

        assertEquals(listOf(TagEffort("health", 1)), effort(from = today, to = today))
        assertEquals(listOf(TagEffort("health", 2)), effort(from = today.minusDays(2), to = today))
    }

    @Test
    fun `an empty range yields no rows rather than a zero`() = runTest {
        val habit = createHabit("run", tag = "health")
        store.repository.addCompletion(habit, store.today())

        assertEquals(emptyList<TagEffort>(), effort(from = LocalDate.parse("1999-01-01"), to = LocalDate.parse("1999-12-31")))
    }

    @Test
    fun `the biggest slice leads`() = runTest {
        val small = createHabit("invoices", tag = "work")
        val big = createHabit("run", tag = "health")
        val today = store.today()
        store.repository.addCompletion(small, today)
        store.repository.addCompletion(big, today)
        store.repository.addCompletion(big, today.minusDays(1))

        assertEquals(listOf(TagEffort("health", 2), TagEffort("work", 1)), effort())
    }

    @Test
    fun `undoing a completion takes it out of the total`() = runTest {
        val habit = createHabit("run", tag = "health")
        val today = store.today()
        store.repository.addCompletion(habit, today)

        store.repository.observeTagEffort(today, today).test {
            assertEquals(listOf(TagEffort("health", 1)), awaitItem())

            store.repository.undoCompletion(habit, today)

            assertEquals(emptyList<TagEffort>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Pins the inner join. A completion whose `HabitCreated` has not arrived has
     * no known tag, and the query drops it rather than guessing it into the
     * untagged slice — which would be inventing data, and would make the
     * untagged row mean two different things.
     *
     * Written against the tables directly because the repository cannot produce
     * this state: `addCompletion` rejects an unknown habit with `HabitNotFound`.
     * Phase 2's sync is where out-of-order delivery becomes reachable, and this
     * test is what will fail loudly if the decision needs revisiting then.
     */
    @Test
    fun `a completion whose habit metadata has not arrived is excluded`() = runTest {
        val habits = store.database.habitProjectionDao()
        val completions = store.database.completionProjectionDao()
        habits.upsert(
            HabitEntity(
                habitId = "known",
                name = "run",
                icon = "book",
                color = "#aabbcc",
                scheduleKind = "daily",
                timesPerWeek = null,
                tag = "health",
                archived = false,
            ),
        )
        completions.upsert(CompletionEntity(habitId = "known", logicalDate = "2026-08-17", note = null))
        completions.upsert(CompletionEntity(habitId = "orphan", logicalDate = "2026-08-17", note = null))

        val rows = store.database.readModelDao().observeTagEffort("2026-08-01", "2026-08-31").first()

        assertEquals(1, rows.size)
        assertEquals("health", rows.single().tag)
        assertEquals("the orphan must not be folded into the tagged count", 1, rows.single().completions)
        assertTrue("no untagged row should be invented for it", rows.none { it.tag == null })
    }
}
