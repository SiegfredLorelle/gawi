package com.gawi.core.data.di

import com.gawi.core.data.backup.CompletionCsvArchive
import com.gawi.core.data.backup.ContentResolverCompletionCsvArchive
import com.gawi.core.data.backup.ContentResolverEventArchive
import com.gawi.core.data.backup.EventArchive
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.data.repository.OfflineFirstHabitRepository
import com.gawi.core.data.settings.DataStoreSettingsSource
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.time.DeviceClock
import com.gawi.core.data.time.SystemDeviceClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Which implementation answers each seam the event store is built on. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindsModule {

    /**
     * Singleton because the implementation owns the in-memory projection and
     * the mutex that serialises writes to it. Two instances would be two
     * command authorities drifting apart with nothing to notice.
     */
    @Binds
    @Singleton
    abstract fun habitRepository(implementation: OfflineFirstHabitRepository): HabitRepository

    @Binds
    abstract fun settingsSource(implementation: DataStoreSettingsSource): SettingsSource

    @Binds
    abstract fun deviceClock(implementation: SystemDeviceClock): DeviceClock

    /**
     * Unscoped: it holds nothing itself. What a merge touches lives on the
     * repository singleton it delegates to, and the export stamp lives in the
     * singleton `DataStore`.
     */
    @Binds
    abstract fun eventArchive(implementation: ContentResolverEventArchive): EventArchive

    /**
     * Unscoped, and holding nothing at all: it reads a table and writes a
     * document. Deliberately not bound to the same implementation as
     * [eventArchive] — the CSV is not a recovery path and does not stamp the
     * last-export time, which is enforced by the implementation not being given
     * the journal.
     */
    @Binds
    abstract fun completionCsvArchive(implementation: ContentResolverCompletionCsvArchive): CompletionCsvArchive
}
