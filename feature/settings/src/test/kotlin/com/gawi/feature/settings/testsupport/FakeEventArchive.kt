package com.gawi.feature.settings.testsupport

import android.net.Uri
import com.gawi.core.data.backup.EventArchive
import com.gawi.core.data.backup.ExportStatus
import com.gawi.core.data.backup.ImportResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * An archive a test can drive, and hold still.
 *
 * Hand-written like every other fake here — there is no mocking library in this
 * project. [gate] is what makes the in-flight state observable: leave it unset
 * and both calls return at once; set it and neither returns until it is
 * completed, which is the only way to catch a screen that never stops looking
 * busy.
 */
class FakeEventArchive : EventArchive {

    /** Every destination [exportTo] was given, in order. */
    val exported = mutableListOf<Uri>()

    /** Every source [importFrom] was given, in order. */
    val imported = mutableListOf<Uri>()

    /** What the next import reports. */
    var result: ImportResult = ImportResult.Merged(read = 0, added = 0)

    /** Set to make either call throw rather than return. */
    var failure: Throwable? = null

    /** Set to hold both calls open until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    /**
     * What the export row is told about the last backup. Assign it to move the
     * clock forward, which is why it is a [MutableStateFlow] — an already
     * running collector has to see the change, the same reason
     * [FakeSettingsSource] is one.
     */
    val exportStatus = MutableStateFlow(ExportStatus(daysSinceExport = null, hasEvents = false))

    /**
     * Set to make [observeExportStatus] fail. The screen must survive it: the
     * status is a caption, and losing it must not take the recovery rows with
     * it.
     */
    var statusFailure: Throwable? = null

    override suspend fun exportTo(destination: Uri) {
        exported += destination
        await()
    }

    override suspend fun importFrom(source: Uri): ImportResult {
        imported += source
        await()
        return result
    }

    // Fails on collection and not on the call, which is where a real flow fails
    // and the only version of this the ViewModel's guard actually has to survive.
    override fun observeExportStatus(): Flow<ExportStatus> = statusFailure?.let { cause -> flow { throw cause } } ?: exportStatus

    private suspend fun await() {
        gate?.await()
        failure?.let { throw it }
    }
}
