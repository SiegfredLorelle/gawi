package com.gawi.core.data.db

import androidx.room.Room
import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.db.mapper.toDomain
import com.gawi.core.data.db.mapper.toEntity
import com.gawi.core.data.testsupport.completionAdded
import com.gawi.core.data.testsupport.event
import com.gawi.core.data.testsupport.habitCreated
import com.gawi.core.data.testsupport.habitId
import com.gawi.core.domain.serialization.EventCodec
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Opens the real database on the JVM (architecture §8) and round-trips the
 * event log through it.
 *
 * This is also the canary for `src/test/resources/robolectric.properties`: if
 * the pinned SDK has no android-all jar, this class fails at startup rather
 * than on an assertion, and it fails before any test with real logic in it.
 */
@RunWith(RobolectricTestRunner::class)
class GawiDatabaseTest {

    private lateinit var database: GawiDatabase
    private lateinit var events: EventDao
    private val codec = EventCodec()

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GawiDatabase::class.java)
            .build()
        events = database.eventDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an appended event round-trips through its row`() = runTest {
        val original = event(1, atMillis = 1_700_000_000_000, payload = habitCreated(habitId(9), name = "stretch"))

        events.insertAll(listOf(original.toEntity(codec)))

        assertEquals(listOf(original), events.loadAll().map { it.toDomain(codec) })
    }

    @Test
    fun `the log reads back in occurred-at then id order`() = runTest {
        val habit = habitId(9)
        val third = event(1, atMillis = 300, payload = completionAdded(habit, "2026-08-19"))
        val first = event(3, atMillis = 100, payload = habitCreated(habit))
        // Same instant as `first`, so only the event id can break the tie.
        val second = event(4, atMillis = 100, payload = completionAdded(habit, "2026-08-18"))

        events.insertAll(listOf(third, second, first).map { it.toEntity(codec) })

        assertEquals(listOf(first, second, third), events.loadAll().map { it.toDomain(codec) })
    }

    @Test
    fun `every column survives the round trip`() = runTest {
        val original = event(2, atMillis = 1_700_000_000_123, payload = completionAdded(habitId(9), "2026-08-19", "note"))
            .copy(tzOffsetMin = -330)

        events.insertAll(listOf(original.toEntity(codec)))

        val row = events.loadAll().single()
        assertEquals(original.id.value, row.id)
        assertEquals("CompletionAdded", row.type)
        assertEquals(1, row.schemaVersion)
        assertEquals(1_700_000_000_123, row.occurredAt)
        assertEquals(-330, row.tzOffsetMin)
        assertEquals(original, row.toDomain(codec))
    }

    @Test
    fun `an empty log counts zero`() = runTest {
        assertEquals(0, events.count())
    }
}
