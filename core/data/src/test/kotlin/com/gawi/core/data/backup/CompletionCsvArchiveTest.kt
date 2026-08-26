package com.gawi.core.data.backup

import android.net.Uri
import com.gawi.core.data.db.dao.CompletionExportDao
import com.gawi.core.data.db.dao.CompletionExportRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * What actually reaches the document.
 *
 * **The class this covers used to have no JVM test**, on the stated grounds that
 * substituting a `ContentResolver` needs a Robolectric shadow nothing in the
 * project used. A PR reviewer pointed out that the cost of starting is low —
 * `robolectric` is already on this module's test classpath and
 * `ShadowContentResolver` implements the very two-argument
 * `openOutputStream(Uri, String)` overload the archive calls — so the grounds
 * were true about the repo and wrong about the difficulty.
 *
 * What this can check: the bytes, the returned count, that the stream is closed,
 * and **that the document is not opened until the rows are in hand**.
 *
 * What it deliberately cannot, so that nothing here implies otherwise:
 *
 * - **The `"wt"` mode.** The shadow hands back whatever was registered and does
 *   not record the mode it was asked for, so "truncate, never append" stays a
 *   device check. That matters: plain `"w"` is not required to truncate.
 * - **`NonCancellable`.** Proving that needs a cancelled caller scope racing a
 *   real provider, which is the shape `docs/running.md` §4 covers.
 * - **The `?: throw IOException` guard on a null stream.** A real provider may
 *   return null from `openOutputStream`; this shadow never does. Measured both
 *   ways — an unregistered `Uri` throws `FileNotFoundException`, and a supplier
 *   registered to return null throws the same rather than handing the null back.
 *   A mutation replacing that `throw` with a silent `0` therefore survives every
 *   test here, which is recorded rather than papered over with a test whose name
 *   would claim coverage it does not have.
 *
 * The same shadow would now work for [ContentResolverEventArchive], which still
 * has no behavioural test of its own — recorded as follow-up in
 * docs/ux/settings.md §8, because that class is the recovery path and deserves
 * its own change rather than a rider on this one.
 */
@RunWith(RobolectricTestRunner::class)
class CompletionCsvArchiveTest {

    private val context = RuntimeEnvironment.getApplication()

    /** Robolectric, not a plain JVM test: off it, `Uri.parse` returns null here. */
    private val destination: Uri = Uri.parse("content://documents/gawi-completions.csv")

    private val document = RecordingDocument()

    /** How many times the provider was asked to open the document for writing. */
    private val opens = AtomicInteger()

    private fun archiveOver(dao: CompletionExportDao): CompletionCsvArchive {
        shadowOf(context.contentResolver).registerOutputStreamSupplier(destination) {
            opens.incrementAndGet()
            document
        }
        return ContentResolverCompletionCsvArchive(context, dao)
    }

    @Test
    fun `the encoded csv is what lands in the document`() = runTest {
        val rows = listOf(
            CompletionExportRow("read", "2026-08-20", null),
            CompletionExportRow("=1+1", "2026-08-21", "note, with a comma"),
        )

        val written = archiveOver(FakeCompletionExportDao(rows)).exportTo(destination)

        assertEquals(rows.size, written)
        // Compared against the codec rather than against a literal, so this test
        // pins the plumbing and CompletionCsvTest keeps owning the format.
        assertEquals(CompletionCsv.encode(rows).toList(), document.toByteArray().toList())
        assertTrue("the mark must survive the round trip", document.toByteArray().take(3) == listOf<Byte>(-17, -69, -65))
    }

    @Test
    fun `the stream is closed`() = runTest {
        archiveOver(FakeCompletionExportDao(listOf(CompletionExportRow("read", "2026-08-20", null)))).exportTo(destination)

        assertTrue("a leaked stream may never flush to the provider", document.closed)
    }

    @Test
    fun `an empty log still writes the header and reports nought`() = runTest {
        val written = archiveOver(FakeCompletionExportDao(emptyList())).exportTo(destination)

        assertEquals(0, written)
        assertEquals(CompletionCsv.encode(emptyList()).toList(), document.toByteArray().toList())
    }

    /**
     * **Encode before open, which nothing else covers.**
     *
     * Opening the document is the destructive step — `"wt"` truncates it the
     * instant it opens — so a read that throws must happen while the file the
     * user picked is still untouched. Asserted on the *open count* rather than on
     * the bytes: a zero-length document would look identical whether it was
     * opened and left empty or never opened at all, and only the second is safe.
     */
    @Test
    fun `a failed read never opens the document`() = runTest {
        val archive = archiveOver(FakeCompletionExportDao(failure = IllegalStateException("the database is corrupt")))

        val thrown = runCatching { archive.exportTo(destination) }.exceptionOrNull()

        assertTrue("expected the read failure to propagate, got $thrown", thrown is IllegalStateException)
        assertEquals("the document must not have been opened", 0, opens.get())
        assertFalse(document.closed)
        assertEquals(0, document.toByteArray().size)
    }

    /**
     * A provider that refuses surfaces as `IOException` and not as a silent
     * nought.
     *
     * Note what this does *not* reach: the class's own
     * `?: throw IOException(...)`. Measured — an unregistered `Uri` makes the
     * shadow throw `FileNotFoundException`, and registering a supplier that
     * returns null makes it throw the same thing rather than handing the null
     * back. So the elvis stays uncovered here; see the class KDoc.
     */
    @Test
    fun `a provider that throws surfaces as an IO failure`() = runTest {
        val elsewhere: Uri = Uri.parse("content://documents/refused.csv")
        val archive = ContentResolverCompletionCsvArchive(context, FakeCompletionExportDao(emptyList()))

        val thrown = runCatching { archive.exportTo(elsewhere) }.exceptionOrNull()

        assertTrue("expected an IO failure, got $thrown", thrown is IOException)
    }

    /**
     * Records what was written and whether it was closed.
     *
     * `ByteArrayOutputStream.close()` is a no-op and `toByteArray()` keeps
     * working afterwards, so overriding it costs nothing.
     */
    private class RecordingDocument : ByteArrayOutputStream() {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    /** Hand-written, like every fake here — this project has no mocking library. */
    private class FakeCompletionExportDao(
        private val rows: List<CompletionExportRow> = emptyList(),
        private val failure: Throwable? = null,
    ) : CompletionExportDao {

        override suspend fun all(): List<CompletionExportRow> = failure?.let { throw it } ?: rows
    }
}
