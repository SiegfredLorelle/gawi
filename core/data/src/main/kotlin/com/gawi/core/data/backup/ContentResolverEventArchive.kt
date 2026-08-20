package com.gawi.core.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * [EventArchive] over the Storage Access Framework.
 *
 * Holds no state and keeps no `Uri`: the grant the picker returns belongs to
 * the activity that asked for it, and this reads or writes inside the call
 * rather than saving it for later. That is why nothing here takes a persistable
 * permission.
 *
 * **This is where main-safety is provided**, and it is not optional. A caller is
 * a ViewModel on `Dispatchers.Main.immediate`, and the document behind a `Uri`
 * may live in a cloud provider — so opening it, reading it and writing it are
 * all binder IPC that can block on a network fetch. Wrapping here rather than
 * in [EventLogArchive] keeps that core dispatcher-neutral and testable, and
 * puts the switch in the one class that actually touches the platform.
 */
internal class ContentResolverEventArchive @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val archive: EventLogArchive,
) : EventArchive {

    override suspend fun exportTo(destination: Uri) = withContext(Dispatchers.IO) {
        // "wt" and never "w". Plain "w" is not required to truncate, and some
        // document providers do not, so overwriting a longer file leaves the
        // old tail behind — producing JSON that fails to parse at the very end,
        // in a file the user believes is their backup.
        val stream = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("the document provider would not open $destination for writing")
        stream.use { archive.export(it) }
    }

    override suspend fun importFrom(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw IOException("the document provider would not open $source for reading")
        stream.use { archive.import(it) }
    }
}
