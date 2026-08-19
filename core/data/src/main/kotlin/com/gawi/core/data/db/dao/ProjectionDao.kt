package com.gawi.core.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.gawi.core.data.db.entity.CompletionEntity
import com.gawi.core.data.db.entity.HabitEntity
import com.gawi.core.data.db.entity.HabitStreakEntity
import com.gawi.core.data.db.entity.ProjectionMetaEntity

/**
 * Writes to the derived tables, plus the point reads the projection writer
 * needs to skip writes that would not change anything.
 *
 * That skip matters more than it looks. Room invalidates per table, not per
 * row, so an upsert that writes the value already there still wakes every
 * `Flow` observing that table. Reading first costs one indexed lookup and is
 * what keeps an idempotent no-op genuinely free.
 */
@Dao
internal interface HabitProjectionDao {

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE habit_id = :habitId")
    suspend fun find(habitId: String): HabitEntity?

    @Query("DELETE FROM habits WHERE habit_id = :habitId")
    suspend fun delete(habitId: String)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()

    /**
     * The whole table in a deterministic order. Read by the invariant test
     * that compares incrementally written rows against a full rebuild — the
     * assertion that this projection is correct at all.
     */
    @Query("SELECT * FROM habits ORDER BY habit_id")
    suspend fun all(): List<HabitEntity>
}

@Dao
internal interface CompletionProjectionDao {

    @Upsert
    suspend fun upsert(completion: CompletionEntity)

    @Query("SELECT * FROM completions WHERE habit_id = :habitId AND logical_date = :logicalDate")
    suspend fun find(habitId: String, logicalDate: String): CompletionEntity?

    @Query("DELETE FROM completions WHERE habit_id = :habitId AND logical_date = :logicalDate")
    suspend fun delete(habitId: String, logicalDate: String)

    @Query("DELETE FROM completions")
    suspend fun deleteAll()

    /** See [HabitProjectionDao.all]. */
    @Query("SELECT * FROM completions ORDER BY habit_id, logical_date")
    suspend fun all(): List<CompletionEntity>
}

@Dao
internal interface HabitStreakDao {

    @Upsert
    suspend fun upsert(streak: HabitStreakEntity)

    @Query("SELECT * FROM habit_streaks WHERE habit_id = :habitId")
    suspend fun find(habitId: String): HabitStreakEntity?

    @Query("DELETE FROM habit_streaks WHERE habit_id = :habitId")
    suspend fun delete(habitId: String)

    @Query("DELETE FROM habit_streaks")
    suspend fun deleteAll()

    /** See [HabitProjectionDao.all]. */
    @Query("SELECT * FROM habit_streaks ORDER BY habit_id")
    suspend fun all(): List<HabitStreakEntity>
}

@Dao
internal interface ProjectionMetaDao {

    @Upsert
    suspend fun upsert(meta: ProjectionMetaEntity)

    @Query("SELECT projection_version FROM projection_meta WHERE id = :id")
    suspend fun projectionVersion(id: Int = ProjectionMetaEntity.SINGLETON_ID): Int?
}
