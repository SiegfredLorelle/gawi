package com.gawi.core.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * [EventArchive] over the Storage Access Framework.
 *
 * Keeps no `Uri`: the grant the picker returns belongs to the activity that
 * asked for it, and this reads or writes inside the call rather than saving it
 * for later. That is why nothing here takes a persistable permission.
 *
 * **This is where main-safety and write-safety are provided**, and neither is
 * optional. A caller is a ViewModel on `Dispatchers.Main.immediate`, and the
 * document behind a `Uri` may live in a cloud provider — so opening it, reading
 * it and writing it are all binder IPC that can block on a network fetch.
 * Wrapping here rather than in [EventLogArchive] keeps that core
 * dispatcher-neutral and testable, and puts both guarantees in the one class
 * that actually touches the platform.
 */
internal class ContentResolverEventArchive @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val archive: EventLogArchive,
    private val journal: ExportJournal,
) : EventArchive {

    /**
     * Writes the whole log to [destination].
     *
     * **Non-cancellable, and that is most of the point of this wrapper.** The
     * caller is `viewModelScope`, which dies when the settings destination
     * leaves the back stack — so without this, pressing Back mid-export
     * abandons the work at the log read, the one interior suspension point, by
     * which time `"wt"` has already truncated the document. What is left is a
     * zero-length file under a name the user will read as their backup, on the
     * only recovery path there is (architecture §6).
     *
     * The usual objection to `NonCancellable` does not apply here.
     * `openOutputStream`, `write` and `close` are blocking binder calls rather
     * than suspension points, so a hung provider pins this thread with or
     * without it. All cancellation ever bought was the chance to give up at the
     * one moment where giving up does the damage.
     *
     * **The log is read and encoded before the document is opened**, because
     * opening it is the destructive step. Doing that first means a read that
     * *throws* — SQLite, a full disk — leaves an empty file behind, and if the
     * user picked an existing backup to replace, replaces it with nothing.
     * Neither escape hatch works: `DocumentsContract.deleteDocument` would
     * delete that very backup and needs `FLAG_SUPPORTS_DELETE`, and
     * temp-then-rename needs a tree grant, which is a far larger permission
     * than a backup button earns. Ordering costs nothing and needs neither.
     *
     * What this does not survive is the process dying mid-write. See
     * docs/ux/settings.md §8.
     *
     * **The export is recorded after the stream closes, and the ordering is the
     * whole meaning of the stamp**: it has to say "a file landed", not "a write
     * was attempted", because what it drives is the nudge telling the user they
     * still have no backup. Recording before the write would silence that for
     * thirty days on the strength of a document that was truncated and never
     * filled. Inside the non-cancellable region for the same reason the write
     * is, so leaving the screen cannot separate the two.
     *
     * This ordering is the one thing here that no JVM test covers — substituting
     * a `ContentResolver` means a Robolectric shadow, which nothing in this
     * project does yet, and [EventLogArchive] is split out precisely so the
     * decisions are testable without one. It is checked on a device instead
     * (docs/running.md §4).
     */
    override suspend fun exportTo(destination: Uri) = withContext(NonCancellable + Dispatchers.IO) {
        val bytes = archive.encode()
        // "wt" and never "w". Plain "w" is not required to truncate, and some
        // document providers do not, so overwriting a longer file leaves the
        // old tail behind — producing JSON that fails to parse at the very end,
        // in a file the user believes is their backup.
        val stream = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("the document provider would not open $destination for writing")
        stream.use {
            it.write(bytes)
            it.flush()
        }
        journal.record()
    }

    override suspend fun importFrom(source: Uri): ImportResult = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(source)
            ?: throw IOException("the document provider would not open $source for reading")
        stream.use { archive.import(it) }
    }

    // Straight through: the journal is already the flow this is meant to be,
    // and there is no document involved in reading it.
    override fun observeExportStatus(): Flow<ExportStatus> = journal.observe()
}
