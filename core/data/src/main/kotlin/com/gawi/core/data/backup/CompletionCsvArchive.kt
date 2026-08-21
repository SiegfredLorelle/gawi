package com.gawi.core.data.backup

import android.net.Uri

/**
 * Writes the completions projection to a document as CSV (PRD §5).
 *
 * **Separate from [EventArchive] rather than a third method on it, and that is
 * not tidiness.** That interface's KDoc says it is "the only disaster-recovery
 * path there is", which is true and load-bearing — architecture §6 rests on it,
 * and so does every piece of copy on the settings screen. A CSV method sitting
 * beside `exportTo` and `importFrom` would make the sentence false by
 * association, and the distinction is exactly the one docs/ux/settings.md §7
 * says must never be blurred.
 *
 * What this writes is **a view of history for a spreadsheet**. It carries no
 * events, no habit configuration and no settings, so nothing can be rebuilt
 * from it and nothing here is a backup. It follows that this path never stamps
 * `ExportJournal` and never resets the 30-day nudge: the nudge asks "is there a
 * copy of the log", and a CSV is not one. The implementation is not given the
 * journal at all, so that is a fact about the object graph rather than a rule
 * someone has to remember.
 *
 * Main-safe, like [EventArchive]: a document can live in a cloud provider, so
 * writing one is IO that may block on the network, and the caller is a
 * ViewModel on the main thread.
 */
interface CompletionCsvArchive {

    /**
     * Writes every logged day to [destination], returning how many rows were
     * written.
     *
     * The count is returned so the caller can tell an empty export from a full
     * one, which is the one thing about a written file that the copy cannot say
     * for itself. Nought is a real answer and not an error: a log with nothing
     * in it produces a file holding only its column headings, and saying so is
     * more use than a success message that reads the same either way.
     *
     * It is deliberately not *shown*. A number in that sentence would govern a
     * noun and so need a quantity resource, which the settings screen's message
     * type cannot carry — see `csvMessageFor`.
     */
    suspend fun exportTo(destination: Uri): Int
}
