package com.gawi.core.data.di

import com.gawi.core.data.db.GawiDatabase
import com.gawi.core.data.db.dao.CompletionProjectionDao
import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.db.dao.HabitProjectionDao
import com.gawi.core.data.db.dao.HabitStreakDao
import com.gawi.core.data.db.dao.ProjectionMetaDao
import com.gawi.core.data.db.dao.ReadModelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * One accessor per dao, and nothing else.
 *
 * Split out of [DataModule] when the export codec and the app version took
 * that object past detekt's function limit — the same reason
 * `SettingsPickers.kt` left `SettingsScreen.kt`. It is a better line than it
 * sounds: everything here is a mechanical delegation to the database with no
 * decision in it, while what is left next door is entirely decisions about
 * scope and construction.
 *
 * None of these is scoped. Room hands back the same dao instance every time,
 * so a `@Singleton` would be a claim about this module rather than about the
 * object it returns.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DaoModule {

    @Provides
    fun eventDao(database: GawiDatabase): EventDao = database.eventDao()

    @Provides
    fun habitProjectionDao(database: GawiDatabase): HabitProjectionDao = database.habitProjectionDao()

    @Provides
    fun completionProjectionDao(database: GawiDatabase): CompletionProjectionDao = database.completionProjectionDao()

    @Provides
    fun habitStreakDao(database: GawiDatabase): HabitStreakDao = database.habitStreakDao()

    @Provides
    fun projectionMetaDao(database: GawiDatabase): ProjectionMetaDao = database.projectionMetaDao()

    @Provides
    fun readModelDao(database: GawiDatabase): ReadModelDao = database.readModelDao()
}
