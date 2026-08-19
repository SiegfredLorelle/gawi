package com.gawi.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.gawi.core.data.db.DATABASE_NAME
import com.gawi.core.data.db.GawiDatabase
import com.gawi.core.data.db.dao.CompletionProjectionDao
import com.gawi.core.data.db.dao.EventDao
import com.gawi.core.data.db.dao.HabitProjectionDao
import com.gawi.core.data.db.dao.HabitStreakDao
import com.gawi.core.data.db.dao.ProjectionMetaDao
import com.gawi.core.data.db.dao.ReadModelDao
import com.gawi.core.data.settings.SETTINGS_NAME
import com.gawi.core.data.settings.settingsDataStore
import com.gawi.core.domain.id.UuidV7Generator
import com.gawi.core.domain.serialization.EventCodec
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wiring for the event store.
 *
 * Three of the singleton scopes here are correctness, not performance.
 *
 * [UuidV7Generator] must be the only one in the process: its monotonicity is
 * per instance, and two instances seed their counter randomly, so they can
 * collide outright — on the id that future sync dedupes by.
 *
 * The repository itself is a singleton for the same class of reason; that one
 * is documented on the implementation, which is where someone tempted to
 * unscope it would be looking.
 *
 * So is the settings [DataStore], and that one at least fails loudly rather
 * than silently: two instances over one file throw outright.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GawiDatabase = // No fallbackToDestructiveMigration, ever: this file holds the event
        // log, which is the only copy of the user's history.
        Room.databaseBuilder(context, GawiDatabase::class.java, DATABASE_NAME).build()

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

    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        settingsDataStore { context.preferencesDataStoreFile(SETTINGS_NAME) }

    @Provides
    @Singleton
    fun uuidGenerator(): UuidV7Generator = UuidV7Generator()

    @Provides
    @Singleton
    fun eventCodec(): EventCodec = EventCodec()
}
