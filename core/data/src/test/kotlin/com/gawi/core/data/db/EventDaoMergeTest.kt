package com.gawi.core.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.gawi.core.data.db.dao.ROW_NOT_INSERTED
import com.gawi.core.data.db.entity.EventEntity
import com.gawi.core.data.testsupport.uuid
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The dedupe entry point a merge is built on, pinned at the DAO.
 *
 * Worth its own file because the "added" count an import reports is derived
 * from Room's row-id contract rather than from a query, and a Room upgrade that
 * changed it would silently make that number wrong rather than fail.
 */
@RunWith(RobolectricTestRunner::class)
class EventDaoMergeTest {

    private val database = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GawiDatabase::class.java)
        .build()
    private val events = database.eventDao()

    @After
    fun tearDown() = database.close()

    @Test
    fun `a skipped insert reports the sentinel and a new one reports a row id`() = runTest {
        events.insertMerging(listOf(row(1)))

        val ids = events.insertMerging(listOf(row(1), row(2)))

        assertEquals(ROW_NOT_INSERTED, ids[0])
        assertNotEquals(ROW_NOT_INSERTED, ids[1])
    }

    @Test
    fun `an id the log already holds is skipped rather than aborting`() = runTest {
        events.insertMerging(listOf(row(1)))

        events.insertMerging(listOf(row(1)))

        assertEquals(1, events.count())
    }

    /**
     * The contrast that makes the new entry point necessary at all.
     *
     * `runCatching` rather than `assertThrows`, because `assertThrows` wants a
     * non-suspending lambda and the obvious way to give it one — a nested
     * `runTest` — makes the test assert nothing at all. The inner call binds to
     * the `TestScope` extension overload, whose `enter()` throws
     * "Only a single call to runTest can be performed during one test" *before*
     * the insert runs; `assertThrows(Exception::class.java)` then catches that
     * and passes. It passed against `OnConflictStrategy.IGNORE` too, which is
     * the one thing it exists to rule out. Found in PR review.
     */
    @Test
    fun `the original insert still aborts on a repeated id`() = runTest {
        events.insertAll(listOf(row(1)))

        val failure = runCatching { events.insertAll(listOf(row(1))) }.exceptionOrNull()

        assertTrue("was $failure", failure is SQLiteConstraintException)
        assertEquals(1, events.count())
    }

    @Test
    fun `duplicates inside one batch are skipped`() = runTest {
        val ids = events.insertMerging(listOf(row(1), row(1)))

        assertEquals(ROW_NOT_INSERTED, ids[1])
        assertEquals(1, events.count())
    }

    /**
     * First writer wins, which is what "the id is the identity" means. REPLACE
     * would pass every other test here and quietly rewrite the user's history.
     */
    @Test
    fun `the row already stored is left exactly as it was`() = runTest {
        events.insertMerging(listOf(row(1, payload = """{"original":true}""")))

        events.insertMerging(listOf(row(1, payload = """{"original":false}""")))

        assertEquals("""{"original":true}""", events.loadAll().single().payload)
    }

    private fun row(n: Int, payload: String = """{"habit_id":"${uuid(500)}"}""") = EventEntity(
        id = uuid(n),
        type = "HabitCreated",
        schemaVersion = 1,
        occurredAt = 1_760_000_000_000 + n,
        tzOffsetMin = 0,
        payload = payload,
    )
}
