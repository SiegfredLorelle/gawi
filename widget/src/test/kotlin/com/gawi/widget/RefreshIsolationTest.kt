package com.gawi.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * One provider's failure does not stop the others.
 *
 * **What this closes, and why it needed a new seam.** While the module published
 * one widget, `onProjectionChanged` could wrap its single `updateAll` in one
 * `try` and nothing was at risk. The streak widget made it a loop, and the loop
 * went *inside* that same `try` — so a throw from the first provider silently
 * skipped every provider after it. That is the exact freeze
 * [GlanceProjectionListener]'s KDoc calls indistinguishable from a widget nobody
 * placed, reached by the mechanism meant to prevent it.
 *
 * Review caught it, and made the sharper point: `ProjectionRefreshTest` pins
 * *which* widgets are refreshed but never that one can fail alone, so it would
 * have stayed green. A real `GlanceAppWidget` cannot be made to throw from a JVM
 * test, hence [refreshEach] taking plain suspend lambdas.
 */
class RefreshIsolationTest {

    @Test
    fun `a failing update does not stop the ones after it`() = runTest {
        val ran = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()

        refreshEach(
            listOf(
                suspend { ran += 1 },
                suspend { throw IOException("the first provider is broken") },
                suspend { ran += 3 },
            ),
            onFailure = { failures += it },
        )

        assertEquals("the update after the failing one did not run", listOf(1, 3), ran)
        assertEquals("expected exactly one reported failure", 1, failures.size)
    }

    /**
     * The measured failure this module already had once: a `NoClassDefFoundError`
     * is an `Error`, so it walks past `catch (Exception)`. The catch here is
     * deliberately `Throwable` and this is what pins that.
     */
    @Test
    fun `an Error is reported rather than escaping`() = runTest {
        val ran = mutableListOf<Int>()
        val failures = mutableListOf<Throwable>()

        refreshEach(
            listOf(
                suspend { throw NoClassDefFoundError("androidx/work/CoroutineWorker") },
                suspend { ran += 2 },
            ),
            onFailure = { failures += it },
        )

        assertEquals(listOf(2), ran)
        assertTrue("an Error was not reported: $failures", failures.single() is NoClassDefFoundError)
    }

    /** Every update runs, and each failure is reported once — not once for the batch. */
    @Test
    fun `each failure is reported separately`() = runTest {
        val failures = mutableListOf<Throwable>()

        refreshEach(
            List(3) { suspend { throw IOException("provider $it") } },
            onFailure = { failures += it },
        )

        assertEquals(3, failures.size)
    }

    @Test
    fun `an empty list is a no-op`() = runTest {
        val failures = mutableListOf<Throwable>()
        refreshEach(emptyList(), onFailure = { failures += it })
        assertEquals(emptyList<Throwable>(), failures)
    }

    /**
     * A failing update is not a reason to abandon the rest; a **cancelled scope**
     * is. `ensureActive()` is what separates the two, and this is the assertion
     * that keeps the widened `catch (Throwable)` from swallowing cancellation.
     */
    @Test
    fun `a cancelled scope propagates instead of being reported`() = runTest {
        val failures = mutableListOf<Throwable>()
        val started = CompletableDeferred<Unit>()

        val job = launch {
            refreshEach(
                listOf(
                    suspend {
                        started.complete(Unit)
                        // Suspends until cancelled, so the cancellation lands
                        // inside refreshEach's catch rather than before the loop.
                        awaitCancellation()
                    },
                    suspend { error("must never be reached after cancellation") },
                ),
                onFailure = { failures += it },
            )
        }

        started.await()
        job.cancelAndJoin()

        assertTrue("the cancellation did not propagate out of refreshEach", job.isCancelled)
        assertEquals("cancellation was reported as a provider failure", emptyList<Throwable>(), failures)
    }
}
