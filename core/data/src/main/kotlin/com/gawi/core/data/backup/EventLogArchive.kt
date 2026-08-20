package com.gawi.core.data.backup

import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.db.mapper.toEncoded
import com.gawi.core.data.repository.OfflineFirstHabitRepository
import com.gawi.core.data.time.DeviceClock
import com.gawi.core.domain.serialization.export.EventLogCodec
import com.gawi.core.domain.serialization.export.ExportMeta
import com.gawi.core.domain.serialization.export.ExportRead
import com.gawi.core.domain.serialization.export.ExportRejection
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.CharacterCodingException
import javax.inject.Inject

/**
 * The app's own version name, stamped on an export for provenance.
 *
 * A data class rather than a value class: Kotlin mangles a function returning
 * one, and Hilt cannot name the result — `@Provides fun appVersion(): AppVersion`
 * fails KSP with "not a valid name: appVersion-iXto3as". A wrapper type at all,
 * rather than a bare `String`, so the graph has one unambiguous binding for it.
 */
internal data class AppVersion(val value: String)

/**
 * Export and import over plain streams — [EventArchive] minus the document.
 *
 * Split out from the `Uri` side so the part with the decisions in it is
 * testable against a `ByteArrayOutputStream`. This project hand-writes every
 * fake and has no mocking library; substituting a `ContentResolver` would mean
 * reaching for a Robolectric shadow, which nothing here does yet.
 *
 * Streams are neither opened nor closed here. The caller owns them.
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

    suspend fun export(destination: OutputStream) {
        val log = events.loadAll().map { it.toEncoded() }
        val text = codec.encode(log, ExportMeta(clock.now(), appVersion.value))
        destination.write(text.encodeToByteArray())
        destination.flush()
    }

    /**
     * Reads an export.
     *
     * A leading byte order mark is stripped rather than refused. `EF BB BF`
     * decodes to U+FEFF without complaint and the JSON lexer treats only space,
     * tab, CR and LF as leading whitespace, so a file carrying one fails to
     * parse at offset zero — and this is reachable along exactly the path the
     * format is designed to invite, since a Windows editor saving a
     * hand-repaired export adds one by default.
     */
    suspend fun import(source: InputStream): ImportResult {
        val bytes = source.readBytes()
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

    private companion object {
        /** U+FEFF, which a Windows editor prepends when it saves as UTF-8. */
        const val BYTE_ORDER_MARK = "\uFEFF"
    }
}
