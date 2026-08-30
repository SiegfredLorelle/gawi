package com.gawi.core.data.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.gawi.core.data.backup.AppVersion
import com.gawi.core.data.db.DATABASE_NAME
import com.gawi.core.data.db.GawiDatabase
import com.gawi.core.data.db.Migrations
import com.gawi.core.data.settings.SETTINGS_NAME
import com.gawi.core.data.settings.settingsDataStore
import com.gawi.core.domain.id.UuidV7Generator
import com.gawi.core.domain.serialization.EventCodec
import com.gawi.core.domain.serialization.export.EventLogCodec
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
        // log, which is the only copy of the user's history. Every migration is
        // declared instead, which is what makes that absence safe rather than
        // merely principled.
        with(Migrations) {
            Room.databaseBuilder(context, GawiDatabase::class.java, DATABASE_NAME)
                .addGawiMigrations()
                .build()
        }

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

    @Provides
    @Singleton
    fun eventLogCodec(payloads: EventCodec): EventLogCodec = EventLogCodec(payloads)

    /**
     * Stamped on an export so that a file which will not import can be traced
     * to what wrote it — provenance for a human holding a broken backup, and the
     * only thing there is to go on when `allowBackup` is off and that file is
     * the only copy. The Settings screen's About section shows the same value
     * (docs/ux/settings.md §9), so the app does read it back now; nothing
     * *decides* on it.
     */
    @Provides
    @Singleton
    fun appVersion(@ApplicationContext context: Context): AppVersion {
        val manager = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            manager.getPackageInfo(context.packageName, 0)
        }
        return AppVersion(info.versionName ?: UNKNOWN_APP_VERSION)
    }

    /** What an export says when the platform will not name the build. */
    private const val UNKNOWN_APP_VERSION = "unknown"
}
