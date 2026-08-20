package com.gawi.core.data.backup

import android.net.Uri
import kotlinx.coroutines.flow.Flow

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
 * **Both calls are main-safe**: they move themselves off the caller's
 * dispatcher. A document can live in a cloud provider, so reading or writing
 * one is IO that may block on the network, and the caller is a ViewModel on the
 * main thread.
 *
 * A file the user picked can be the wrong file, and that is not a failure —
 * it is a thing people do. Those come back as [ImportResult.Refused], the
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

    /**
     * What is known about the last export, for the 30-day nudge (PRD §5).
     *
     * A flow rather than a one-shot read, because a successful export has to
     * change the row that offered it while the user is still looking at it.
     */
    fun observeExportStatus(): Flow<ExportStatus>
}

/**
 * When the log was last exported, and whether there is a log to export.
 *
 * A hint on a row and never an input to a command, which is what lets an
 * unreadable file answer this rather than refuse it — see `ExportJournal`.
 *
 * [daysSinceExport] is null when no export has ever been recorded, and counts
 * whole wall-clock days otherwise, so a backup taken an hour ago and one taken
 * this morning both read as nought. [hasEvents] is what keeps a fresh install
 * from being nudged about losing nothing.
 */
data class ExportStatus(val daysSinceExport: Long?, val hasEvents: Boolean)

/** What an import did, or why it did nothing. */
sealed interface ImportResult {

    /** [read] events in the file, [added] of which this device did not have. */
    data class Merged(val read: Int, val added: Int) : ImportResult

    /**
     * The file was refused and the log was not touched.
     *
     * Spelled out here rather than handing back the codec's own rejection type,
     * so that a caller can tell a user *which* of these happened without
     * knowing that `:core:domain` exists. The three are genuinely different
     * things to be told: the wrong file, a file from the future, and a file
     * that is broken.
     */
    sealed interface Refused : ImportResult {

        /** Readable, but not one of ours. Usually the wrong file in the picker. */
        data object NotAnExport : Refused

        /**
         * A Gawi export written by a newer build. The file is intact and the
         * fix is to update the app, so this must never be reported as damage.
         */
        data class FromANewerVersion(val formatVersion: Int) : Refused

        /** Ours, and unreadable. [detail] names the offending event. */
        data class Damaged(val detail: String) : Refused
    }
}
