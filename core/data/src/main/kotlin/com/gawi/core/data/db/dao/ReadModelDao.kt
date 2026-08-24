package com.gawi.core.data.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.gawi.core.data.db.entity.HabitEntity
import com.gawi.core.data.db.entity.TodayHabitRow
import kotlinx.coroutines.flow.Flow

/**
 * What the UI observes. Everything above `:core:data` reads these queries and
 * never learns that events exist (architecture §4).
 *
 * `today`, `weekStart` and `weekEnd` are bind parameters rather than anything
 * stored, so a day or week rollover is just re-issuing the query with new
 * arguments — no second staleness axis to keep swept, and no column that can
 * be stale in a way the caller cannot see.
 */
@Dao
internal interface ReadModelDao {

    /**
     * Every non-archived habit with today's completion state, its count for
     * the current week and its cached streak.
     *
     * Ordered by `habit_id`, which is a UUIDv7 and therefore in creation
     * order — a stable, meaningful sort that costs nothing.
     */
    @Query(
        """
        SELECT h.habit_id AS habit_id,
               h.name AS name,
               h.icon AS icon,
               h.color AS color,
               h.schedule_kind AS schedule_kind,
               h.times_per_week AS times_per_week,
               h.tag AS tag,
               h.created_on AS created_on,
               h.archived AS archived,
               (t.habit_id IS NOT NULL) AS completed_today,
               t.note AS note,
               (SELECT COUNT(*) FROM completions w
                 WHERE w.habit_id = h.habit_id
                   AND w.logical_date BETWEEN :weekStart AND :weekEnd
                   AND w.logical_date <= :today) AS week_count,
               COALESCE(s.current_streak, 0) AS current_streak,
               COALESCE(s.previous_streak, 0) AS previous_streak,
               s.broken_on AS broken_on
          FROM habits h
          LEFT JOIN completions t
                 ON t.habit_id = h.habit_id AND t.logical_date = :today
          LEFT JOIN habit_streaks s
                 ON s.habit_id = h.habit_id
         WHERE h.archived = 0
         ORDER BY h.habit_id
        """,
    )
    fun observeToday(today: String, weekStart: String, weekEnd: String): Flow<List<TodayHabitRow>>

    /**
     * One habit, archived or not, with the same shape as a Today row so the
     * detail screen and the list agree on what a habit looks like.
     */
    @Query(
        """
        SELECT h.habit_id AS habit_id,
               h.name AS name,
               h.icon AS icon,
               h.color AS color,
               h.schedule_kind AS schedule_kind,
               h.times_per_week AS times_per_week,
               h.tag AS tag,
               h.created_on AS created_on,
               h.archived AS archived,
               (t.habit_id IS NOT NULL) AS completed_today,
               t.note AS note,
               (SELECT COUNT(*) FROM completions w
                 WHERE w.habit_id = h.habit_id
                   AND w.logical_date BETWEEN :weekStart AND :weekEnd
                   AND w.logical_date <= :today) AS week_count,
               COALESCE(s.current_streak, 0) AS current_streak,
               COALESCE(s.previous_streak, 0) AS previous_streak,
               s.broken_on AS broken_on
          FROM habits h
          LEFT JOIN completions t
                 ON t.habit_id = h.habit_id AND t.logical_date = :today
          LEFT JOIN habit_streaks s
                 ON s.habit_id = h.habit_id
         WHERE h.habit_id = :habitId
        """,
    )
    fun observeHabit(habitId: String, today: String, weekStart: String, weekEnd: String): Flow<TodayHabitRow?>

    /**
     * Every habit, archived included, as it was configured — no completion
     * state, no week count and no streak.
     *
     * The management list is the one screen that has to show archived habits,
     * because unarchiving has to be reachable from somewhere. It is also the
     * one screen that shows no progress: PRD §6.6 scopes streaks to the Today
     * view and habit detail — narrowed from three surfaces, the widget having
     * been settled as minimal (docs/ux/widget.md §2) — so joining
     * for them here would buy columns nothing draws.
     *
     * Ordered by name rather than by `habit_id`, because this list is read to
     * find a habit rather than to work through one. `COLLATE NOCASE` so a
     * capitalised name does not sort into its own block, with `habit_id` as
     * the tiebreak so duplicate names hold a stable order instead of
     * swapping places between emissions.
     */
    @Query("SELECT * FROM habits ORDER BY name COLLATE NOCASE, habit_id")
    fun observeAllHabits(): Flow<List<HabitEntity>>

    /**
     * The completed cells in a date range, with their displayed notes — the
     * retro strip and the note sheet read the same rows.
     */
    @Query(
        """
        SELECT * FROM completions
         WHERE habit_id = :habitId
           AND logical_date BETWEEN :from AND :to
         ORDER BY logical_date
        """,
    )
    fun observeCompletedDates(habitId: String, from: String, to: String): Flow<List<CompletedDateRow>>

    /**
     * Completions in a date range, totalled per tag — the tag effort
     * distribution's whole read (docs/ux/insights.md §5).
     *
     * Counts, never percentages: shares are the screen's arithmetic, for the
     * reason [com.gawi.core.data.model.TagEffort] gives. Untagged habits group
     * under a null `tag` and are a row like any other.
     *
     * **Grouped case- and whitespace-insensitively, which the default collation
     * would not do.** Tags are unnormalized free text — the editor stores
     * `tag.ifBlank { null }` with no trim and no case fold, and `Commands`
     * validates only the name — so `Health`, `health` and `health ` are three
     * stored values for one human tag. SQLite groups on BINARY by default, so
     * the obvious query splits them into three slices, each understating the
     * real share, and the `COLLATE NOCASE` ordering below then lands them side
     * by side where the split is most visible. `MIN` picks the representative
     * so the label is deterministic rather than whichever row the group
     * happened to yield. The better fix is normalizing on write, which is a
     * domain decision about what a tag *is* and is deliberately not made here.
     *
     * **Re-tagging a habit re-attributes its whole history, and that is the
     * decision rather than an oversight.** The join reads `habits.tag`, which is
     * current metadata under whole-record LWW, not the tag in force when each
     * completion was logged. Re-tag "run" from `health` to `fitness` and last
     * January's distribution shows `fitness`. The log could answer otherwise —
     * `HabitUpdated` carries the old value — but the read model does not keep
     * it, and the reading taken here is that a tag describes the habit rather
     * than the completion, so the current answer is the true one. Note this is a
     * narrower guarantee than the archiving paragraph above may suggest: what
     * archiving cannot do is remove effort, not that no edit can move it.
     *
     * **Archived habits are included.** Effort spent is history, and archiving
     * a habit is a decision about the future — hiding its past completions here
     * would make a period's distribution change retroactively every time
     * something was tidied away. This is deliberately the opposite of
     * [observeToday], which is about what to do now.
     *
     * An inner join, so a completion whose `HabitCreated` has not arrived is
     * not counted: it has no known tag, and guessing it into the untagged slice
     * would be inventing data. Unreachable before Phase 2 sync — locally a
     * completion cannot precede its habit — and worth revisiting when sync
     * makes out-of-order delivery real. `TagEffortQueryTest` pins the exclusion
     * so it stays a decision rather than an accident.
     *
     * Ordered by count, then tag, so the biggest slice leads and ties hold a
     * stable order between emissions instead of swapping places. `COLLATE
     * NOCASE` for the same reason [observeAllHabits] uses it.
     */
    @Query(
        """
        SELECT MIN(TRIM(h.tag)) AS tag,
               COUNT(*) AS completions
          FROM completions c
          JOIN habits h ON h.habit_id = c.habit_id
         WHERE c.logical_date BETWEEN :from AND :to
         GROUP BY TRIM(h.tag) COLLATE NOCASE
         ORDER BY completions DESC, tag COLLATE NOCASE
        """,
    )
    fun observeTagEffort(from: String, to: String): Flow<List<TagEffortRow>>
}

/** One tag's completion total over the queried range; null [tag] is untagged. */
internal data class TagEffortRow(@ColumnInfo(name = "tag") val tag: String?, @ColumnInfo(name = "completions") val completions: Int)

/** A completed cell and the note showing on it. */
internal data class CompletedDateRow(
    @ColumnInfo(name = "logical_date") val logicalDate: String,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "habit_id") val habitId: String,
)
