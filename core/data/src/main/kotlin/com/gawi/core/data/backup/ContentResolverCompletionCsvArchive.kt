package com.gawi.core.data.backup

import android.content.Context
import android.net.Uri
import com.gawi.core.data.db.dao.CompletionExportDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * [CompletionCsvArchive] over the Storage Access Framework.
 *
 * Keeps no `Uri`, for the reason [ContentResolverEventArchive] gives: the grant
 * the picker returns belongs to the activity that asked for it, and this writes
 * inside the call rather than saving it for later. No permission, no
 * `FileProvider`, no `<queries>`, so the manifest is untouched again.
 *
 * **Three rules are shared with [ContentResolverEventArchive] and deliberately
 * duplicated rather than extracted.** They are `NonCancellable`, the `"wt"`
 * mode, and encoding before the document is opened. A reviewer will reasonably
 * ask why there is no shared writer; two reasons, and the second is the one
 * that decided it:
 *
 * That class has **no JVM test at all** — substituting a `ContentResolver`
 * needs a Robolectric shadow nothing in this project uses — so refactoring it
 * is verified only on a device. Reaching into the one untested class on the
 * recovery path, to serve a caller whose stakes are lower, is the wrong
 * direction.
 *
 * And its reasoning is **about the backup**, at length and for good cause: a
 * truncated JSON is a file the user will later trust with their whole history.
 * A truncated CSV costs a second tap. Generalising forty lines of argument
 * about the only recovery path, so that a spreadsheet convenience could share
 * it, would make that argument less true rather than more reusable.
 *
 * So the rules are restated here with what differs about them:
 *
 * **`NonCancellable`.** `viewModelScope` dies when the settings destination
 * leaves the back stack, and `"wt"` has already truncated the document by then.
 * Same mechanism as the backup, smaller consequence — what is left is a
 * half-written spreadsheet rather than a plausibly-named empty backup. It stays
 * because a partial file is still a worse answer than a complete one, and
 * because the same caveat applies: this cannot protect work that never starts,
 * so leaving the screen fast enough still leaves a zero-length file (see
 * docs/ux/settings.md §7).
 *
 * **`"wt"` and never `"w"`.** Plain `"w"` is not required to truncate and some
 * providers do not, so overwriting a longer file leaves the old tail behind —
 * here that means rows from a previous export appearing under the new ones,
 * which is worse in a spreadsheet than in JSON because it parses fine.
 *
 * **The rows are read and encoded before the document is opened**, because
 * opening it is the destructive step. A read that throws then leaves the file
 * the user picked untouched.
 *
 * **No `ExportJournal`.** Not an omission — see [CompletionCsvArchive]. This
 * class cannot stamp the last-export time because it was never given the thing
 * that stamps it.
 */
internal class ContentResolverCompletionCsvArchive @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val completions: CompletionExportDao,
) : CompletionCsvArchive {

    override suspend fun exportTo(destination: Uri): Int = withContext(NonCancellable + Dispatchers.IO) {
        val rows = completions.all()
        val bytes = CompletionCsv.encode(rows)
        val stream = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("the document provider would not open $destination for writing")
        stream.use {
            it.write(bytes)
            it.flush()
        }
        rows.size
    }
}
