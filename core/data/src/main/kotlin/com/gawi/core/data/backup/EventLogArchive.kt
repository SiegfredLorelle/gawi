package com.gawi.core.data.backup

import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.db.mapper.toEncoded
import com.gawi.core.data.repository.OfflineFirstHabitRepository
import com.gawi.core.data.time.DeviceClock
import com.gawi.core.domain.serialization.export.EventLogCodec
import com.gawi.core.domain.serialization.export.ExportMeta
import com.gawi.core.domain.serialization.export.ExportRead
import com.gawi.core.domain.serialization.export.ExportRejection
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.CharacterCodingException
import javax.inject.Inject

/**
 * The app's own version name: stamped on an export for provenance, and shown on
 * the Settings screen's About section (docs/ux/settings.md §9) — public for the
 * second reader rather than read from `PackageManager` twice.
 *
 * A data class rather than a value class: Kotlin mangles a function returning
 * one, and Hilt cannot name the result — `@Provides fun appVersion(): AppVersion`
 * fails KSP with "not a valid name: appVersion-iXto3as". A wrapper type at all,
 * rather than a bare `String`, so the graph has one unambiguous binding for it.
 */
data class AppVersion(val value: String)

/**
 * The content of an export, and what to do with one — [EventArchive] minus the
 * document.
 *
 * Split out from the `Uri` side so the part with the decisions in it is
 * testable against plain bytes. This project hand-writes every fake and has no
 * mocking library; substituting a `ContentResolver` would mean reaching for a
 * Robolectric shadow, which nothing here does yet.
 *
 * The two directions are deliberately asymmetric. [import] takes a stream and
 * neither opens nor closes it, because reading a document destroys nothing.
 * [encode] hands bytes back rather than taking a stream, because opening a
 * document for writing truncates it — so the caller has to own the moment that
 * becomes irreversible, and should leave it as late as possible.
 */
internal class EventLogArchive @Inject constructor(
    private val events: EventDao,
    // The concrete repository, not HabitRepository. The mutex and the
    // in-memory projection are on the implementation, and merging foreign
    // events is not "the event store seen as habits" — nothing above that
    // interface knows events exist. This is the same instance the
    // HabitRepository binding hands out *only because @Singleton is on the
    // class itself*; move it onto the @Binds and this quietly becomes a second
    // command authority, which is the failure DataModule's KDoc warns about.
    private val store: OfflineFirstHabitRepository,
    private val codec: EventLogCodec,
    private val clock: DeviceClock,
    private val appVersion: AppVersion,
) {

    /**
     * The whole log as the bytes an export file holds.
     *
     * Bytes rather than a stream, and asymmetric with [import] on purpose.
     * Reading a document destroys nothing, so an import can stream and stay
     * cancellable. Writing one truncates it the instant it opens, so this hands
     * the finished bytes back and lets the caller decide when to make that
     * irreversible — which it should do last.
     */
    suspend fun encode(): ByteArray {
        val log = events.loadAll().map { it.toEncoded() }
        return codec.encode(log, ExportMeta(clock.now(), appVersion.value)).encodeToByteArray()
    }

    /**
     * Reads an export, refusing anything too large to be one.
     *
     * The picker deliberately offers `application/octet-stream` and `text/plain`
     * as well as JSON, because an export round-tripped through a cloud drive
     * comes back mistyped and a filter that hides someone's own backup is the
     * worse failure. The cost is that it now shows essentially everything, so
     * the likeliest wrong tap is a large file — and this was the one path here
     * where that was a crash rather than a refusal, because `OutOfMemoryError`
     * is an `Error` and the guard the caller wraps this in catches `Exception`.
     *
     * The ceiling is a sanity check and **not** a memory guarantee: the parsed
     * tree is several times the size of the text, so a file just under it is
     * still heavy. What it removes is picking a video on the one screen whose
     * whole job is disaster recovery.
     */
    suspend fun import(source: InputStream): ImportResult {
        val bytes = source.readAtMost(MAX_IMPORT_BYTES)
        return if (bytes.size > MAX_IMPORT_BYTES) {
            ImportResult.Refused.Damaged("larger than ${MAX_IMPORT_BYTES / BYTES_PER_MB} MB, so it is not a Gawi export")
        } else {
            importText(bytes)
        }
    }

    /**
     * A leading byte order mark is stripped rather than refused. `EF BB BF`
     * decodes to U+FEFF without complaint and the JSON lexer treats only space,
     * tab, CR and LF as leading whitespace, so a file carrying one fails to
     * parse at offset zero — and this is reachable along exactly the path the
     * format is designed to invite, since a Windows editor saving a
     * hand-repaired export adds one by default.
     */
    private suspend fun importText(bytes: ByteArray): ImportResult {
        val text = try {
            // Strict, deliberately. The lenient default substitutes U+FFFD for
            // a bad byte, which turns an encoding problem into a baffling parse
            // error further down. Caught by name because
            // CharacterCodingException *is* an IOException, and IO is supposed
            // to propagate from here — a mis-encoded file the user picked is
            // not a disk failure.
            bytes.decodeToString(throwOnInvalidSequence = true).removePrefix(BYTE_ORDER_MARK)
        } catch (cause: CharacterCodingException) {
            return ImportResult.Refused.Damaged("not UTF-8 text: ${cause.message}")
        }
        return when (val read = codec.decode(text)) {
            is ExportRead.Refused -> read.reason.asResult()
            is ExportRead.Events -> ImportResult.Merged(read.events.size, store.mergeEvents(read.events))
        }
    }

    /**
     * Restates the codec's refusal in this module's own terms, so nothing above
     * `:core:data` has to name a `:core:domain` type to tell a user what
     * happened.
     */
    private fun ExportRejection.asResult(): ImportResult = when (this) {
        ExportRejection.NotAnExport -> ImportResult.Refused.NotAnExport

        // Only *above* ours is a newer build. A version below it is a number
        // this app has never written, so telling someone to update would be
        // advice that can never come true — and the whole point of separating
        // these cases is that the one about a working file has to be right.
        is ExportRejection.UnsupportedFormatVersion ->
            if (found > supported) {
                ImportResult.Refused.FromANewerVersion(found)
            } else {
                ImportResult.Refused.Damaged("format version $found is not one this app has ever written")
            }

        is ExportRejection.Malformed -> ImportResult.Refused.Damaged(detail)
    }

    /**
     * At most [limit] bytes, plus however much of the final chunk overshoots.
     *
     * Hand-rolled because `InputStream.readNBytes` is API 33 and this app is
     * minSdk 29 — it compiles against the current platform jar and then fails
     * lint's `NewApi`, which is an error here. The overshoot is deliberate:
     * reading one chunk past the ceiling is what lets the caller tell "exactly
     * at the limit" from "over it" without a second read.
     */
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val sink = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (sink.size() <= limit) {
            val read = read(chunk)
            if (read < 0) break
            sink.write(chunk, 0, read)
        }
        return sink.toByteArray()
    }

    internal companion object {
        /** U+FEFF, which a Windows editor prepends when it saves as UTF-8. */
        private const val BYTE_ORDER_MARK = "\uFEFF"

        private const val BYTES_PER_MB = 1_024 * 1_024

        /**
         * The largest file this will even try to read.
         *
         * Measured rather than guessed: a real 294-event export was 115,023
         * bytes, so 391 bytes an event, and the PRD's ~2k events a year puts a
         * heavy user near 780 KB a year. Thirty-two megabytes is about forty
         * years of that, so it refuses nothing anyone will ever have and
         * refuses a photo immediately.
         */
        const val MAX_IMPORT_BYTES = 32 * BYTES_PER_MB
    }
}
