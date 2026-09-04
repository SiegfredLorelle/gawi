package com.gawi.core.data.db

import com.gawi.core.data.db.mapper.toEncoded
import com.gawi.core.data.db.mapper.toEntity
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.testing.completionAdded
import com.gawi.core.domain.testing.event
import com.gawi.core.domain.testing.habitId
import com.gawi.core.domain.testing.metadata
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * What the CSV of completions is built from.
 *
 * The rows themselves are written by the projection and covered by
 * `ProjectionWriterTest`; what this file pins is the *shape* the export reads
 * them in — the order, the join, and the two cases where the obvious query
 * would silently lose a day the user logged.
 */
@RunWith(RobolectricTestRunner::class)
class CompletionExportDaoTest {

    private val store = TestStore.create()
    private val export = store.database.completionExportDao()

    @After
    fun tearDown() = store.close()

    private suspend fun createHabit(name: String, schedule: Schedule = Schedule.Daily): HabitId =
        (store.repository.createHabit(metadata(name, schedule)) as CommandResult.Accepted).payload

    /**
     * Two habits whose names sort against their dates, so this pins the date as
     * the *primary* key rather than merely observing that one habit's rows come
     * out sorted — ordering by name first would pass that weaker assertion.
     */
    @Test
    fun `every logged day is listed oldest first`() = runTest {
        val late = createHabit("apple")
        val early = createHabit("zebra")
        // Inside the three-day retroactive window, which is a command rule the
        // clock here sits at the near end of: today is 2026-08-17.
        store.repository.addCompletion(late, LocalDate.parse("2026-08-17"))
        store.repository.addCompletion(early, LocalDate.parse("2026-08-15"))
        store.repository.addCompletion(early, LocalDate.parse("2026-08-16"))

        assertEquals(
            listOf("2026-08-15" to "zebra", "2026-08-16" to "zebra", "2026-08-17" to "apple"),
            export.all().map { it.logicalDate to it.habit },
        )
    }

    @Test
    fun `habits sharing a day are ordered by name regardless of case`() = runTest {
        // "Read" and "exercise" discriminate the collation rather than merely
        // exercising it: under SQLite's default binary collation 'R' (82) sorts
        // before 'e' (101), so dropping COLLATE NOCASE reverses this.
        val capitalised = createHabit("Read")
        val lowercase = createHabit("exercise")
        val day = LocalDate.parse("2026-08-16")
        store.repository.addCompletion(capitalised, day)
        store.repository.addCompletion(lowercase, day)

        assertEquals(listOf("exercise", "Read"), export.all().map { it.habit })
    }

    @Test
    fun `an archived habit keeps every day it was logged on`() = runTest {
        val habit = createHabit("stretch")
        store.repository.addCompletion(habit, LocalDate.parse("2026-08-16"))
        store.repository.archiveHabit(habit)

        val rows = export.all()

        assertEquals(1, rows.size)
        assertEquals("stretch", rows.single().habit)
    }

    /**
     * The case the `LEFT JOIN` exists for, produced the way a device produces
     * it: a merge delivering a completion whose `HabitCreated` is not in the
     * file. `ProjectionWriter` keeps the cell and drops the habit row, so an
     * inner join would lose a day the user really logged.
     */
    @Test
    fun `a completion whose habit never arrived falls back to its id`() = runTest {
        val unknown = habitId(99)
        val incoming = event(id = 50, atMillis = 1_755_000_000_000, payload = completionAdded(unknown, "2026-08-16"))

        val added = store.repository.mergeEvents(listOf(incoming.toEntity(EventCodec()).toEncoded()))

        assertEquals(1, added)
        assertEquals(listOf(unknown.value), export.all().map { it.habit })
    }

    @Test
    fun `a note travels with the day it was written on`() = runTest {
        val habit = createHabit("read")
        store.repository.addCompletion(habit, LocalDate.parse("2026-08-16"), note = "20 pages")
        store.repository.addCompletion(habit, LocalDate.parse("2026-08-17"))

        val rows = export.all()

        assertEquals("20 pages", rows.first().note)
        assertEquals(null, rows.last().note)
    }

    @Test
    fun `nothing logged reads as an empty list rather than nothing at all`() = runTest {
        createHabit("read")

        assertEquals(emptyList<Any>(), export.all())
    }
}
