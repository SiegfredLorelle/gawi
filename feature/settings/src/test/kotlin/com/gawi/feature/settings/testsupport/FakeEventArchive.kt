package com.gawi.feature.settings.testsupport

import android.net.Uri
import com.gawi.core.data.backup.EventArchive
import com.gawi.core.data.backup.ImportResult
import kotlinx.coroutines.CompletableDeferred

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

    override suspend fun exportTo(destination: Uri) {
        exported += destination
        await()
    }

    override suspend fun importFrom(source: Uri): ImportResult {
        imported += source
        await()
        return result
    }

    private suspend fun await() {
        gate?.await()
        failure?.let { throw it }
    }
}
