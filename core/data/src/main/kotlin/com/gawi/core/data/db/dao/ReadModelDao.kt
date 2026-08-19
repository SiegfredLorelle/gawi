package com.gawi.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
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
}

/** A completed cell and the note showing on it. */
internal data class CompletedDateRow(
    @androidx.room.ColumnInfo(name = "logical_date") val logicalDate: String,
    @androidx.room.ColumnInfo(name = "note") val note: String?,
    @androidx.room.ColumnInfo(name = "habit_id") val habitId: String,
)
