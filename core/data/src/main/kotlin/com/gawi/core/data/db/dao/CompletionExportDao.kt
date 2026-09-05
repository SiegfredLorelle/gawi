package com.gawi.core.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/**
 * The completions projection as a flat table for a spreadsheet (PRD §5).
 *
 * Its own dao rather than a query on [ReadModelDao], which is "what the UI
 * observes": every query there returns a `Flow` and serves a screen, and this
 * one is a suspending read that `:core:data` makes for itself on the way to
 * writing a file. Nothing above this module calls it.
 *
 * This is a **view of history and not a backup**. It carries no events, no
 * habit configuration and no settings, so nothing here can rebuild anything —
 * `EventArchive` is the only path that can (architecture §6). The CSV is
 * therefore never stamped by `ExportJournal` and never resets the 30-day
 * nudge; see docs/ux/settings.md §6.
 */
@Dao
internal interface CompletionExportDao {

    /**
     * Every completed cell, oldest first, with the name of the habit it belongs
     * to.
     *
     * Three things here are decisions rather than style:
     *
     * **`LEFT JOIN`, not an inner one.** [com.gawi.core.data.db.entity.CompletionEntity]
     * deliberately has no foreign key to `habits`, because a completion can
     * legitimately arrive before its `HabitCreated` under a merge — and
     * `ProjectionWriter.writeHabit` deletes the `habits` row while
     * `writeCell` keeps the completion, so a cell with no habit row is a state
     * this database really reaches. An inner join would drop those rows, which
     * would mean a day the user logged silently missing from their own export.
     *
     * **`COALESCE(h.name, c.habit_id)`** so such a row still identifies itself.
     * A blank first column would be worse than an unfriendly one.
     *
     * **Archived habits are included.** Their completions are history and this
     * is a view of history.
     *
     * Ordered by date first because that is how a spreadsheet is read, then by
     * name `COLLATE NOCASE` so a capitalised habit does not sort into its own
     * block, then by id so two habits sharing a name hold a stable order
     * instead of swapping places between exports.
     */
    @Query(
        """
        SELECT COALESCE(h.name, c.habit_id) AS habit,
               c.logical_date AS logical_date,
               c.note AS note
          FROM completions c
          LEFT JOIN habits h ON h.habit_id = c.habit_id
         ORDER BY c.logical_date,
                  COALESCE(h.name, c.habit_id) COLLATE NOCASE,
                  c.habit_id
        """,
    )
    suspend fun all(): List<CompletionExportRow>
}

/**
 * One line of the CSV, before any of it is quoted or escaped.
 *
 * [habit] is a name and therefore free text the user wrote; [note] is too.
 * Both are hostile input as far as the file format is concerned — see
 * `CompletionCsv`.
 */
internal data class CompletionExportRow(
    @ColumnInfo(name = "habit") val habit: String,
    @ColumnInfo(name = "logical_date") val logicalDate: String,
    @ColumnInfo(name = "note") val note: String?,
)
