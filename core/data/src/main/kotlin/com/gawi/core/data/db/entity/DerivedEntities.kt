package com.gawi.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// The read model: what the UI queries, rebuilt from the log rather than
// written to directly (architecture §4). Like EventEntity these are dumb
// column mirrors, with the domain translation living in the mappers.

/**
 * A habit as the UI sees it.
 *
 * Only habits that have metadata get a row. A habit id can appear in the log
 * before its `HabitCreated` does — sync can deliver a completion first — and
 * `ProjectedState.habit()` returns null until metadata arrives, which is
 * exactly the point at which there is something to render.
 *
 * The schedule is two columns rather than a blob so `times_per_week` is
 * available to the "2/3 this week" subtitle without decoding anything.
 */
@Entity(tableName = "habits")
internal data class HabitEntity(
    @PrimaryKey
    @ColumnInfo(name = "habit_id")
    val habitId: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "icon")
    val icon: String,
    @ColumnInfo(name = "color")
    val color: String,
    @ColumnInfo(name = "schedule_kind")
    val scheduleKind: String,
    @ColumnInfo(name = "times_per_week")
    val timesPerWeek: Int?,
    @ColumnInfo(name = "tag")
    val tag: String?,
    @ColumnInfo(name = "archived")
    val archived: Boolean,
)

/**
 * One row per *completed* cell: the row existing is the completion. Undo is a
 * delete, which keeps the row delta exact and makes weekly progress a plain
 * `COUNT(*)` over a date range.
 *
 * There is deliberately no foreign key to [HabitEntity]. A completion can
 * legitimately precede its `HabitCreated` under a merge, and a constraint here
 * would make replay fail on data that was once valid — which architecture §1.6
 * forbids.
 *
 * `logical_date` is an ISO-8601 string, so lexical order is chronological and
 * a week range is a correct `BETWEEN` that uses the index.
 */
@Entity(
    tableName = "completions",
    primaryKeys = ["habit_id", "logical_date"],
    indices = [Index(value = ["logical_date"])],
)
internal data class CompletionEntity(
    @ColumnInfo(name = "habit_id")
    val habitId: String,
    @ColumnInfo(name = "logical_date")
    val logicalDate: String,
    @ColumnInfo(name = "note")
    val note: String?,
)

/**
 * A habit's cached streak, in the schedule's own unit.
 *
 * Streaks are not projected: they depend on "today", which is not in the log,
 * so folding them into apply would break the incremental-≡-rebuild invariant
 * (`Streaks` KDoc). They are computed after each transaction and cached here.
 *
 * `computed_for_date` is what makes a row self-describing: it says which
 * "today" these numbers answer for, so a row is never silently ambiguous about
 * whether it has been swept since the day rolled over. It also participates in
 * row equality, which is what makes the rollover sweep rewrite a row whose
 * streak numbers happen to be unchanged — the values are the same but the
 * question they answer is not.
 *
 * There is no unit column. It is derivable from `habits.schedule_kind`, which
 * every query that reads this table already joins, and a second copy could
 * only ever disagree.
 */
@Entity(tableName = "habit_streaks")
internal data class HabitStreakEntity(
    @PrimaryKey
    @ColumnInfo(name = "habit_id")
    val habitId: String,
    @ColumnInfo(name = "current_streak")
    val currentStreak: Int,
    @ColumnInfo(name = "previous_streak")
    val previousStreak: Int,
    @ColumnInfo(name = "broken_on")
    val brokenOn: String?,
    @ColumnInfo(name = "computed_for_date")
    val computedForDate: String,
)

/**
 * One row, holding the projection version that produced the current derived
 * tables. A mismatch on start means the projection *logic* changed under rows
 * that are still there — something a Room schema version cannot detect,
 * because the columns did not move — and triggers a rebuild.
 */
@Entity(tableName = "projection_meta")
internal data class ProjectionMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "projection_version")
    val projectionVersion: Int,
) {

    companion object {
        /**
         * The only id this table ever holds. Room cannot emit a CHECK
         * constraint, so the single-row rule is kept by every writer going
         * through this constant.
         */
        const val SINGLETON_ID = 1
    }
}
