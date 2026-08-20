package com.gawi.core.data.backup

import androidx.room.Room
import com.gawi.core.data.db.GawiDatabase
import com.gawi.core.data.testsupport.FakeDeviceClock
import com.gawi.core.data.testsupport.FakeSettingsSource
import com.gawi.core.data.testsupport.RepositoryCommandGenerator
import com.gawi.core.data.testsupport.TestStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.random.Random

/**
 * The disaster-recovery claim, stated as an invariant: a log exported and then
 * imported into an empty database rebuilds the same app.
 *
 * The sibling of `ProjectionRebuildInvariantTest`, and its caveat applies here
 * word for word. Streaks depend on "today", which is not in the log, so both
 * stores share one [FakeDeviceClock] and both are swept before the comparison.
 * Without that, this would be comparing "the streak as of the last tap" with
 * "the streak as of now" — a real difference, but one about *when* rather than
 * about what the log says.
 *
 * If it fails, the two legitimate causes are a lossy codec and a merge that
 * does not fold what it inserted.
 */
@RunWith(RobolectricTestRunner::class)
class EventLogRoundTripInvariantTest {

    @Test
    fun `an exported log imported into an empty database rebuilds identical tables`() = runTest {
        repeat(SEQUENCES) { sequence ->
            // One clock and one settings store for both, so "today" is the same
            // question on each side of the export.
            val clock = FakeDeviceClock()
            val settings = FakeSettingsSource()
            val source = TestStore.create(clock, settings, idSeed = SEED + sequence)
            val restored = TestStore.createOver(emptyDatabase(), clock, settings, idSeed = SEED + sequence)
            try {
                RepositoryCommandGenerator(Random(SEED + sequence), source).run(COMMANDS_PER_SEQUENCE)
                source.repository.refreshStreaks()
                val original = source.snapshot()

                val result = restored.import(source.exportText())
                restored.repository.refreshStreaks()

                assertEquals("sequence $sequence", ImportResult.Merged(source.log().size, source.log().size), result)
                assertEquals("sequence $sequence diverged", original, restored.snapshot())
            } finally {
                source.close()
                restored.close()
            }
        }
    }

    /**
     * Stronger than the snapshot and one line long: the rows themselves, not
     * just what they project to. A codec that dropped a payload key would still
     * satisfy the invariant above if nothing on screen happened to read it.
     */
    @Test
    fun `the imported event rows equal the source rows`() = runTest {
        val clock = FakeDeviceClock()
        val settings = FakeSettingsSource()
        val source = TestStore.create(clock, settings, idSeed = SEED)
        val restored = TestStore.createOver(emptyDatabase(), clock, settings, idSeed = SEED)
        try {
            RepositoryCommandGenerator(Random(SEED), source).run(COMMANDS_PER_SEQUENCE)

            restored.import(source.exportText())

            assertEquals(source.log(), restored.log())
        } finally {
            source.close()
            restored.close()
        }
    }

    private fun emptyDatabase(): GawiDatabase =
        Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), GawiDatabase::class.java).build()

    private companion object {
        const val SEED = 20_260_820L
        const val SEQUENCES = 6
        const val COMMANDS_PER_SEQUENCE = 120
    }
}
