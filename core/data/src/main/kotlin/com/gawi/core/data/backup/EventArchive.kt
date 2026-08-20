package com.gawi.core.data.backup

import android.net.Uri
import com.gawi.core.domain.serialization.export.ExportRejection

/**
 * Export and import of the event log (PRD §5, architecture §6).
 *
 * This is the only disaster-recovery path there is. Android Auto Backup is
 * deliberately off, so nothing else copies the log anywhere, and the log
 * cannot be reconstructed from the derived tables.
 *
 * Takes a `Uri` rather than a stream, because the caller is a screen and the
 * grant belongs to an activity: opening, truncating and closing the document
 * is one concern and it lives here, where the trap below only has to be
 * written down once. Nothing above this needs a `ContentResolver`.
 *
 * A file the user picked can be the wrong file, and that is not a failure —
 * it is a thing people do. Those come back as [ImportResult.Rejected], the
 * same way a command models refusal as a value. Exceptions stay for real
 * failures: a revoked grant, a full disk, SQLite.
 */
interface EventArchive {

    /**
     * Writes the whole log to [destination] as JSON.
     *
     * Payloads are copied rather than decoded, so a log holding an event this
     * build cannot replay still exports — which is exactly the log worth
     * getting off the device.
     */
    suspend fun exportTo(destination: Uri)

    /**
     * Merges the export at [source] into this device's log, deduping by event
     * id. Non-destructive: nothing is replaced and nothing is removed, so
     * importing the same file twice changes nothing the second time.
     *
     * All or nothing. Every event is validated before a single row is written,
     * so a file refused for one bad event leaves the log untouched.
     */
    suspend fun importFrom(source: Uri): ImportResult
}

/** What an import did, or why it did nothing. */
sealed interface ImportResult {

    /** [read] events in the file, [added] of which this device did not have. */
    data class Merged(val read: Int, val added: Int) : ImportResult

    /** The file was refused and the log was not touched. */
    data class Rejected(val reason: ExportRejection) : ImportResult
}
