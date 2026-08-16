package com.gawi.core.domain.projection

import com.gawi.core.domain.testsupport.RandomEventGenerator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * The standing test oracle (architecture §4): incremental application and a
 * full rebuild must produce identical state — over hostile seeded-random
 * logs full of duplicate adds, dangling and early references, timestamp
 * ties, and backwards clocks. Order-independence is asserted by folding
 * unsorted shuffles, and self-union of the log must be a no-op (the §1
 * sync property in miniature).
 */
class ProjectionInvariantTest {

    private companion object {
        const val SEED = 20_260_817L
        const val SEQUENCES = 50
        const val EVENTS_PER_SEQUENCE = 250
        const val SHUFFLES = 3
        const val PREFIX_SAMPLE_STEP = 25
        val TODAY: LocalDate = LocalDate.parse("2026-08-17")
    }

    @Test
    fun `incremental application equals full rebuild for every sequence`() {
        val rng = Random(SEED)

        repeat(SEQUENCES) { run ->
            val events = RandomEventGenerator(rng, TODAY).sequence(EVENTS_PER_SEQUENCE)

            val incremental = events.fold(ProjectedState.EMPTY, Projector::apply)
            val rebuilt = Projector.rebuild(events)

            assertEquals("run $run diverged", rebuilt, incremental)
        }
    }

    @Test
    fun `incremental state never diverges mid-stream`() {
        val rng = Random(SEED + 1)
        val events = RandomEventGenerator(rng, TODAY).sequence(EVENTS_PER_SEQUENCE)

        var state = ProjectedState.EMPTY
        events.forEachIndexed { index, event ->
            state = Projector.apply(state, event)
            if ((index + 1) % PREFIX_SAMPLE_STEP == 0) {
                assertEquals(
                    "prefix of length ${index + 1} diverged",
                    Projector.rebuild(events.take(index + 1)),
                    state,
                )
            }
        }
    }

    @Test
    fun `any arrival order converges to the same state`() {
        val rng = Random(SEED + 2)

        repeat(SEQUENCES) { run ->
            val events = RandomEventGenerator(rng, TODAY).sequence(EVENTS_PER_SEQUENCE)
            val reference = Projector.rebuild(events)

            repeat(SHUFFLES) { shuffle ->
                val unsortedFold = events.shuffled(rng).fold(ProjectedState.EMPTY, Projector::apply)
                assertEquals("run $run shuffle $shuffle diverged", reference, unsortedFold)
            }
        }
    }

    @Test
    fun `merging the log with itself is a no-op`() {
        val rng = Random(SEED + 3)

        repeat(SEQUENCES) { run ->
            val events = RandomEventGenerator(rng, TODAY).sequence(EVENTS_PER_SEQUENCE)
            val reference = Projector.rebuild(events)

            val selfUnion = (events + events.shuffled(rng)).fold(ProjectedState.EMPTY, Projector::apply)

            assertEquals("run $run self-union changed state", reference, selfUnion)
        }
    }
}
