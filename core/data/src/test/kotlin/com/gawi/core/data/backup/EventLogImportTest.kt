package com.gawi.core.data.backup

import com.gawi.core.data.testsupport.FakeDeviceClock
import com.gawi.core.data.testsupport.FakeSettingsSource
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.data.testsupport.metadata
import com.gawi.core.domain.command.CommandResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream

/**
 * What importing does to a log that is already there.
 *
 * The behaviours here are the ones a user would call data loss if they broke:
 * a merge that replaced, an import that vanished, a bad file that changed
 * something anyway.
 */
@RunWith(RobolectricTestRunner::class)
class EventLogImportTest {

    private val clock = FakeDeviceClock()
    private val settings = FakeSettingsSource()
    private val store = TestStore.create(clock, settings)

    @After
    fun tearDown() = store.close()

    /**
     * The idempotence the whole merge rests on. `Projector.apply` is documented
     * commutative and idempotent so that sync and import can do exactly this.
     */
    @Test
    fun `importing the same file twice adds nothing the second time`() = runTest {
        store.repository.createHabit(metadata(name = "read"))
        store.repository.addCompletion(habitOnScreen(), store.today())
        val file = store.exportText()
        val events = store.log().size

        val first = store.import(file)
        val afterFirst = store.snapshot()
        val second = store.import(file)

        assertEquals(ImportResult.Merged(events, 0), first)
        assertEquals(ImportResult.Merged(events, 0), second)
        assertEquals(afterFirst, store.snapshot())
        assertEquals(events, store.log().size)
    }

    @Test
    fun `importing into a non-empty log merges rather than replaces`() = runTest {
        val other = TestStore.create(clock, settings, idSeed = 99)
        try {
            other.repository.createHabit(metadata(name = "stretch"))
            val incoming = other.exportText()
            val incomingEvents = other.log().size
            store.repository.createHabit(metadata(name = "read"))
            val mine = store.log().size

            val result = store.import(incoming)

            assertEquals(ImportResult.Merged(incomingEvents, incomingEvents), result)
            assertEquals(mine + incomingEvents, store.log().size)
            assertEquals(listOf("read", "stretch"), store.repository.observeAllHabits().first().map { it.name }.sorted())
        } finally {
            other.close()
        }
    }

    /**
     * Architecture §5: the three-day retroactive window is a *command* rule and
     * does not apply to replay, because an import carries months-old events.
     *
     * Mutation-checked — routing the merge through `Commands` reddens this and
     * nothing else, and it would be an easy "consistency" refactor to make.
     */
    @Test
    fun `an imported completion older than the retro window is accepted`() = runTest {
        val other = TestStore.create(clock, settings, idSeed = 7)
        try {
            // Captured before anything moves: the two stores share one clock,
            // so reading "today" again after winding it back would ask a
            // different question than the one being tested.
            val today = store.today()
            val longAgo = today.minusDays(RETRO_DAYS)
            other.repository.createHabit(metadata(name = "read"))
            val habit = other.repository.observeAllHabits().first().single().id
            other.clock.moveTo(longAgo)
            other.repository.addCompletion(habit, longAgo)
            clock.moveTo(today)
            val incoming = other.exportText()

            store.import(incoming)

            val completed = store.repository.observeCompletedDates(habit, longAgo.minusDays(1), today).first()
            assertTrue("completed dates were $completed", completed.containsKey(longAgo))
        } finally {
            other.close()
        }
    }

    /**
     * The stale-in-memory-state case, and the reason the merge refolds instead
     * of calling `rebuildProjections()`.
     *
     * The second assertion is the one that matters. Every derived table can
     * look perfect while the in-memory projection — the command authority — is
     * a whole import behind, because `initialised()` short-circuits on a
     * non-null state. A command against an imported habit is the first thing
     * that notices, and by then the user is looking at the row.
     */
    @Test
    fun `an import is visible to a repository that already folded the log`() = runTest {
        val other = TestStore.create(clock, settings, idSeed = 11)
        try {
            other.repository.createHabit(metadata(name = "stretch"))
            val imported = other.repository.observeAllHabits().first().single().id
            val incoming = other.exportText()
            // Force this store to fold and publish a state that predates the import.
            store.repository.createHabit(metadata(name = "read"))
            store.repository.observeToday().first()

            store.import(incoming)

            val onScreen = store.repository.observeToday().first().habits.map { it.habit.name }
            assertTrue("today showed $onScreen", onScreen.contains("stretch"))
            assertTrue(store.repository.addCompletion(imported, store.today()) is CommandResult.Accepted)
        } finally {
            other.close()
        }
    }

    @Test
    fun `a restart sees the tables the import wrote`() = runTest {
        val other = TestStore.create(clock, settings, idSeed = 13)
        try {
            other.repository.createHabit(metadata(name = "stretch"))
            store.import(other.exportText())
            val afterImport = store.snapshot()

            val restarted = TestStore.createOver(store.database, clock, settings)
            restarted.repository.observeToday().first()

            assertEquals(afterImport, store.snapshot())
        } finally {
            other.close()
        }
    }

    /**
     * Mutation-checked: it kills validating while inserting. Every event is
     * checked before the transaction opens, which is what lets the copy say
     * "nothing was changed" and mean it.
     */
    @Test
    fun `a malformed file changes nothing`() = runTest {
        store.repository.createHabit(metadata(name = "read"))
        val before = store.snapshot()
        val log = store.log()

        val result = store.import("""{"format":"gawi.event-log","format_version":1,"nonsense":true}""")

        assertTrue("$result", result is ImportResult.Refused.Damaged)
        assertEquals(before, store.snapshot())
        assertEquals(log, store.log())
    }

    /**
     * One bad event refuses the lot, including the good events before it. A
     * partial import is a valid log and therefore indistinguishable from a
     * complete one afterwards, which is what makes it worse than a refusal.
     */
    @Test
    fun `one bad event rejects the whole file`() = runTest {
        store.repository.createHabit(metadata(name = "read"))
        store.repository.createHabit(metadata(name = "stretch"))
        val good = store.exportText()
        val other = TestStore.create(clock, settings, idSeed = 17)
        try {
            val corrupted = good.replace(""""type": "HabitCreated"""", """"type": "HabitTeleported"""")
            assertTrue("the corruption did not apply", corrupted != good)

            val result = other.import(corrupted)

            assertTrue("$result", result is ImportResult.Refused.Damaged)
            assertEquals(emptyList<Any>(), other.log())
        } finally {
            other.close()
        }
    }

    @Test
    fun `a newer format version is rejected without touching the log`() = runTest {
        store.repository.createHabit(metadata(name = "read"))
        val log = store.log()

        val result = store.import(store.exportText().replace(""""format_version": 1""", """"format_version": 2"""))

        assertEquals(ImportResult.Refused.FromANewerVersion(formatVersion = 2), result)
        assertEquals(log, store.log())
    }

    /**
     * Only a version *above* ours means "update the app". A file claiming
     * version 0 is a number this app has never written, so advising an update
     * would be advice that can never come true — and separating these cases at
     * all is only worth it if the reassuring one is right.
     */
    @Test
    fun `a version below ours is damaged rather than newer`() = runTest {
        store.repository.createHabit(metadata(name = "read"))
        val log = store.log()

        val result = store.import(store.exportText().replace(""""format_version": 1""", """"format_version": 0"""))

        assertTrue("$result", result is ImportResult.Refused.Damaged)
        assertEquals(log, store.log())
    }

    /**
     * A byte order mark does not make a backup unreadable.
     *
     * `EF BB BF` decodes to U+FEFF without error and the JSON lexer does not
     * count it as whitespace, so an untouched file would fail to parse at
     * offset zero. Reachable along exactly the path the open format invites:
     * hand-repair the file on Windows and the editor adds one.
     */
    @Test
    fun `a byte order mark does not stop a file importing`() = runTest {
        val other = TestStore.create(clock, settings, idSeed = 23)
        try {
            other.repository.createHabit(metadata(name = "stretch"))
            val events = other.log().size

            val result = store.import("\uFEFF" + other.exportText())

            assertEquals(ImportResult.Merged(events, events), result)
        } finally {
            other.close()
        }
    }

    /**
     * A file too large to be an export is refused, not fatal.
     *
     * The picker shows essentially everything by design, so the likeliest wrong
     * tap is a big file — and an `OutOfMemoryError` is an `Error`, which the
     * ViewModel's guard catches `Exception` and therefore misses. Uncaught out
     * of `viewModelScope` that is process death, on the recovery screen, with
     * no message. Everything else on this path refuses as a value; this used
     * not to.
     *
     * Driven by a stream that never ends rather than a large fixture, so the
     * ceiling is what stops the read.
     */
    @Test
    fun `a file larger than the ceiling is refused rather than read`() = runTest {
        store.repository.createHabit(metadata(name = "read"))
        val log = store.log()

        val result = store.archive.import(endlessStream())

        assertTrue("$result", result is ImportResult.Refused.Damaged)
        assertEquals(log, store.log())
    }

    /** Bytes for as long as anyone keeps asking. */
    private fun endlessStream(): InputStream = object : InputStream() {
        override fun read(): Int = 'x'.code

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            b.fill('x'.code.toByte(), off, off + len)
            return len
        }
    }

    private suspend fun habitOnScreen() = store.repository.observeAllHabits().first().single().id

    private companion object {
        const val RETRO_DAYS = 90L
    }
}
