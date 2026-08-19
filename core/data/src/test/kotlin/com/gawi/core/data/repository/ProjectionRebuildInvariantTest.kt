package com.gawi.core.data.repository

import com.gawi.core.data.db.entity.ProjectionMetaEntity
import com.gawi.core.data.testsupport.RepositoryCommandGenerator
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.data.testsupport.metadata
import com.gawi.core.domain.command.CommandResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.random.Random

/**
 * The oracle: rows written incrementally, one append at a time, must equal
 * rows produced by replaying the whole log from scratch.
 *
 * This is the acceptance criterion for the row-delta logic. If it holds, the
 * writer's decisions about which rows an event touched were right; if it does
 * not, some append moved a row a rebuild disagrees about, and the derived
 * tables have been quietly lying.
 *
 * **The invariant is stated with a streak refresh on both sides, and that is
 * not a weakening of it.** Streaks depend on "today", which is not in the
 * event log. An append only recomputes the habits it touched, so a habit
 * nobody touched keeps the numbers it had when it was last written, while a
 * rebuild recomputes every habit against the current date. Comparing those two
 * directly would be comparing "the streak as of whenever you last tapped this
 * habit" against "the streak as of now" — a real difference, but a difference
 * about *when*, not about *what the log says*. Sweeping both sides to the same
 * date first is what makes the comparison about the projection.
 *
 * So if this test ever fails, do not reach for the assertion. The two
 * legitimate causes are a bug in the delta logic and a streak refresh that
 * does not sweep every habit.
 */
@RunWith(RobolectricTestRunner::class)
class ProjectionRebuildInvariantTest {

    @Test
    fun `incrementally written tables equal a full rebuild for every sequence`() = runTest {
        repeat(SEQUENCES) { sequence ->
            val store = TestStore.create(idSeed = SEED + sequence)
            try {
                RepositoryCommandGenerator(Random(SEED + sequence), store).run(COMMANDS_PER_SEQUENCE)

                store.repository.refreshStreaks()
                val incremental = store.snapshot()

                store.repository.rebuildProjections()
                val rebuilt = store.snapshot()

                assertEquals("sequence $sequence diverged", incremental, rebuilt)
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun `streak rows written one append at a time equal a full rebuild`() = runTest {
        // The sibling test above sweeps every streak before comparing, which is
        // what makes a moving clock comparable at all — but that sweep would
        // also quietly repair a bug in the streak delta, so on its own it
        // leaves the delta untested. Holding the clock still removes the need
        // for the sweep: every streak row is current the moment it is written,
        // so any habit the writer failed to recompute — after a schedule change
        // that re-denominates the run, say — shows up here as a difference.
        repeat(SEQUENCES) { sequence ->
            val store = TestStore.create(idSeed = SEED + sequence)
            try {
                RepositoryCommandGenerator(Random(SEED + sequence), store, movesClock = false)
                    .run(COMMANDS_PER_SEQUENCE)

                val incremental = store.snapshot()
                store.repository.rebuildProjections()

                assertEquals("sequence $sequence diverged", incremental, store.snapshot())
            } finally {
                store.close()
            }
        }
    }

    @Test
    fun `a rebuild is idempotent`() = runTest {
        val store = TestStore.create()
        try {
            RepositoryCommandGenerator(Random(SEED), store).run(COMMANDS_PER_SEQUENCE)
            store.repository.rebuildProjections()
            val once = store.snapshot()

            store.repository.rebuildProjections()

            assertEquals(once, store.snapshot())
        } finally {
            store.close()
        }
    }

    @Test
    fun `a stale projection version repairs itself on the next start`() = runTest {
        val store = TestStore.create()
        try {
            val habit = store.repository.createHabit(metadata("read"))
            store.repository.addCompletion((habit as CommandResult.Accepted).payload, store.today())
            val healthy = store.snapshot()

            // Corrupt the derived tables the way a projection-logic change
            // would: rows still present, still schema-valid, simply not what
            // current code would have written. A Room schema version cannot
            // see this, which is the whole reason projection_meta exists.
            store.database.completionProjectionDao().deleteAll()
            store.database.habitStreakDao().deleteAll()
            store.database.projectionMetaDao().upsert(ProjectionMetaEntity(projectionVersion = -1))
            assertNotEquals(healthy, store.snapshot())

            // A fresh repository over the same database. The mismatch has to be
            // noticed on the first *read*, not the first write: someone who
            // opens the app after an upgrade and taps nothing must still see
            // repaired rows rather than stale ones.
            val restarted = TestStore.createOver(store.database)
            restarted.repository.observeToday().first().habits

            assertEquals(healthy, store.snapshot())
        } finally {
            store.close()
        }
    }

    private companion object {
        const val SEED = 20_260_819L
        const val SEQUENCES = 12
        const val COMMANDS_PER_SEQUENCE = 120
    }
}
