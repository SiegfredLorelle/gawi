package com.gawi.core.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That a CSV export cannot reset the 30-day nudge.
 *
 * PRD §5's nudge asks whether a copy of the *log* exists, and a CSV is not one
 * — it holds no events, so nothing can be rebuilt from it. Stamping
 * `ExportJournal` from here would therefore silence the warning for thirty days
 * on the strength of a file that cannot restore anything, which is the exact
 * failure the whole nudge feature is arranged against.
 *
 * Asserted against the constructor rather than against behaviour, because the
 * guarantee *is* structural: the class cannot record a stamp it was never given
 * the means to record. A behavioural test would need a `ContentResolver` shadow
 * this project does not use, and would in any case only demonstrate that one
 * call path does not stamp, where this rules out every call path at once.
 *
 * The mirror assertion is deliberate. Without it, deleting the whole
 * constructor would pass.
 */
class CompletionCsvArchiveWiringTest {

    private val parameters: List<String> =
        ContentResolverCompletionCsvArchive::class.java.declaredConstructors
            .single()
            .parameterTypes
            .map { it.simpleName }

    @Test
    fun `the csv archive is not given the export journal`() {
        assertFalse(
            "a CSV is not a backup, so this class must not be able to stamp the last-export time: $parameters",
            parameters.contains(ExportJournal::class.java.simpleName),
        )
    }

    @Test
    fun `the csv archive is given the completions it exports`() {
        assertTrue(
            "expected the completion export dao among $parameters",
            parameters.contains("CompletionExportDao"),
        )
    }

    /** The JSON export, by contrast, is exactly the thing that does stamp. */
    @Test
    fun `the event archive is given the export journal`() {
        val json = ContentResolverEventArchive::class.java.declaredConstructors
            .single()
            .parameterTypes
            .map { it.simpleName }

        assertTrue("expected the journal among $json", json.contains(ExportJournal::class.java.simpleName))
    }
}
