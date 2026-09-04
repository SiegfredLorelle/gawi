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
 * **`NonCancellable`, `"wt"` and encoding before the document opens are shared
 * with [ContentResolverEventArchive] and deliberately duplicated rather than
 * extracted.** That class is argued at length about the *backup*, where a
 * truncated file is one the user later trusts with their whole history;
 * generalising that argument so a spreadsheet convenience could share it would
 * make it less true rather than more reusable. It also has no JVM test, so the
 * shared writer is a refactor with no safety net until the JSON path gets a
 * behavioural test of its own (docs/ux/settings.md §8).
 *
 * What differs is only the consequence. Leaving the screen mid-write leaves a
 * half-written spreadsheet rather than a plausibly-named empty backup, and a
 * provider that does not truncate leaves last export's rows under this one's —
 * worse in a spreadsheet than in JSON, because it parses fine.
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
