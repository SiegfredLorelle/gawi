package com.gawi.feature.settings.testsupport

import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * A settings store a test can drive.
 *
 * `:core:data` has one of these already and it cannot be reached: it is
 * `internal` to that module and no test-fixtures publishing is configured
 * anywhere in this build. This one is shaped for what these tests need rather
 * than being a copy of it.
 *
 * `MutableSharedFlow(replay = 0)` rather than a `MutableStateFlow`, so nothing
 * is emitted until a test says so — which is what makes the `Loading` state
 * observable at all. A replaying flow would race straight past it.
 */
class FakeSettingsSource : SettingsSource {

    private val settings = MutableSharedFlow<UserSettings>(replay = 0)

    /** Every value [update] produced, in order. */
    val writes = mutableListOf<UserSettings>()

    /** What a transform is applied to, and what the last write left behind. */
    var stored: UserSettings = UserSettings()
        private set

    /** Set to make the read path fail rather than emit. */
    var readFailure: Throwable? = null

    /** Set to make [update] throw, which is the only way a settings write fails. */
    var writeFailure: Throwable? = null

    override fun observe(): Flow<UserSettings> = readFailure?.let { cause -> flow { throw cause } } ?: settings.asSharedFlow()

    override suspend fun update(transform: (UserSettings) -> UserSettings) {
        writeFailure?.let { throw it }
        stored = transform(stored)
        writes += stored
        settings.emit(stored)
    }

    /**
     * Emit a value to whoever is collecting.
     *
     * Waits for a subscriber first. Without that the emission is dropped on a
     * flow with no replay, and the test then passes or fails on scheduling —
     * which is a green that means nothing ran.
     */
    suspend fun emit(value: UserSettings) {
        stored = value
        settings.subscriptionCount.first { it > 0 }
        settings.emit(value)
    }
}
