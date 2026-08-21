package com.gawi.feature.settings.testsupport

import android.net.Uri
import com.gawi.core.data.backup.CompletionCsvArchive
import kotlinx.coroutines.CompletableDeferred

/**
 * A CSV archive a test can drive, and hold still.
 *
 * Hand-written like every other fake here — there is no mocking library in this
 * project. [gate] is what makes the in-flight state observable: leave it unset
 * and the call returns at once; set it and it does not return until the deferred
 * completes, which is the only way to catch a screen that never stops looking
 * busy. Same shape as [FakeEventArchive] on purpose, so a reader who knows one
 * knows the other.
 *
 * There is deliberately **no** export-status flow on this fake, because the real
 * interface has none: a CSV never records a stamp and never settles the 30-day
 * nudge. That absence is the point rather than an omission.
 */
class FakeCompletionCsvArchive : CompletionCsvArchive {

    /** Every destination [exportTo] was given, in order. */
    val exported = mutableListOf<Uri>()

    /** How many rows the next export reports writing. */
    var rows: Int = 1

    /** Set to make the call throw rather than return. */
    var failure: Throwable? = null

    /** Set to hold the call open until it completes. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun exportTo(destination: Uri): Int {
        exported += destination
        gate?.await()
        failure?.let { throw it }
        return rows
    }
}
