package com.gawi.core.data.backup

import com.gawi.core.data.db.entity.EventEntity
import com.gawi.core.data.testsupport.TestStore
import com.gawi.core.data.testsupport.metadata
import com.gawi.core.data.testsupport.uuid
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.serialization.export.EncodedEvent
import com.gawi.core.domain.serialization.export.EventLogCodec
import com.gawi.core.domain.serialization.export.ExportRead
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * What an export puts in the file.
 *
 * These assertions read the file back through [EventLogCodec] rather than
 * parsing it, and the two that cannot do that use plain substrings. That is
 * deliberate: `:core:data` has no kotlinx-serialization on its classpath — main
 * or test — because the export format lives in `:core:domain`, and a JSON
 * dependency appearing here is the signal that the boundary has been crossed.
 * The envelope's own fields are asserted next door in `EventLogCodecTest`.
 */
@RunWith(RobolectricTestRunner::class)
class EventLogExportTest {

    private val store = TestStore.create()
    private val codec = EventLogCodec(EventCodec())

    @After
    fun tearDown() = store.close()

    @Test
    fun `export writes every event in log order`() = runTest {
        store.repository.createHabit(metadata(name = "read"))
        store.repository.createHabit(metadata(name = "stretch"))

        val exported = readBack(store.exportText())

        assertEquals(store.log().map { it.id }, exported.map { it.id.value })
    }

    @Test
    fun `export carries the stored payload bytes`() = runTest {
        store.repository.createHabit(metadata(name = "read"))

        val exported = readBack(store.exportText()).single()

        assertEquals(store.log().single().payload, exported.payload.json)
        assertEquals(store.log().single().schemaVersion, exported.payload.schemaVersion)
    }

    /**
     * A log this build cannot fully read still exports, and that is the point
     * rather than a corner case: it is exactly the log worth rescuing.
     *
     * Mutation-checked. Routing export through `toDomain(codec)` — the obvious
     * "reuse the mapper we already have" simplification — makes this throw
     * `EventCodecException` instead, and nothing else here notices. Read as a
     * substring because our own reader refuses the file too, by design: an
     * unknown type in the *log* is corruption, and refusing it on the way back
     * in is what `EventLogCodecTest` pins.
     */
    @Test
    fun `export does not decode payloads`() = runTest {
        store.repository.createHabit(metadata(name = "read"))
        store.database.eventDao().insertAll(listOf(unreadableRow()))

        val text = store.exportText()

        assertTrue(text, text.contains("HabitTeleported"))
        assertTrue(text, text.contains("\"whither\""))
    }

    @Test
    fun `an empty log exports as a file that reads back as no events`() = runTest {
        assertEquals(emptyList<EncodedEvent>(), readBack(store.exportText()))
    }

    /**
     * The only place the export's two injected seams — the clock and the
     * app version — are exercised together.
     */
    @Test
    fun `the envelope is stamped from the clock and the app version`() = runTest {
        val text = store.exportText()

        assertTrue(text, text.contains("\"exported_at\": \"${store.clock.instant}\""))
        assertTrue(text, text.contains("\"app_version\": \"${TestStore.APP_VERSION}\""))
    }

    /**
     * The stream belongs to whoever opened it — here that is the
     * `ContentResolver` wrapper, whose own `use {}` would double-close it.
     */
    @Test
    fun `export leaves the stream open`() = runTest {
        val sink = RecordingStream()

        store.archive.export(sink)

        assertFalse(sink.closed)
    }

    private fun readBack(text: String): List<EncodedEvent> = when (val read = codec.decode(text)) {
        is ExportRead.Events -> read.events
        is ExportRead.Refused -> error("the export was refused: ${read.reason}")
    }

    private fun unreadableRow() = EventEntity(
        id = uuid(9_000),
        type = "HabitTeleported",
        schemaVersion = 42,
        occurredAt = 1_760_000_000_000,
        tzOffsetMin = 0,
        payload = """{"whither":"elsewhere"}""",
    )

    private class RecordingStream : OutputStream() {
        private val bytes = ByteArrayOutputStream()
        var closed = false
            private set

        override fun write(b: Int) = bytes.write(b)

        override fun close() {
            closed = true
        }
    }
}
