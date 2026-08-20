package com.gawi.core.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject

/**
 * [EventArchive] over the Storage Access Framework.
 *
 * Holds no state and keeps no `Uri`: the grant the picker returns belongs to
 * the activity that asked for it, and this reads or writes inside the call
 * rather than saving it for later. That is why nothing here takes a persistable
 * permission.
 */
internal class ContentResolverEventArchive @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val archive: EventLogArchive,
) : EventArchive {

    override suspend fun exportTo(destination: Uri) {
        // "wt" and never "w". Plain "w" is not required to truncate, and some
        // document providers do not, so overwriting a longer file leaves the
        // old tail behind — producing JSON that fails to parse at the very end,
        // in a file the user believes is their backup.
        val stream = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("the document provider would not open $destination for writing")
        stream.use { archive.export(it) }
    }

    override suspend fun importFrom(source: Uri): ImportResult {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw IOException("the document provider would not open $source for reading")
        return stream.use { archive.import(it) }
    }
}
