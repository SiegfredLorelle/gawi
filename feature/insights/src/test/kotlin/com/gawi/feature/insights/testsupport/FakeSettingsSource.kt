package com.gawi.feature.insights.testsupport

import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * A settings store a test can drive, for the one field this screen reads.
 *
 * `:core:data` has one of these and it cannot be reached — it is `internal` to
 * that module and no test-fixtures publishing is configured in this build.
 * `:feature:settings` has another, shaped for a screen that writes. This one
 * only reads.
 *
 * `MutableSharedFlow(replay = 0)` rather than a `MutableStateFlow`: the
 * ViewModel combines this with the habit read, so nothing resolves until this
 * emits — which is what makes `Loading` observable rather than raced past.
 */
class FakeSettingsSource : SettingsSource {

    private val settings = MutableSharedFlow<UserSettings>(replay = 0)

    /** Set to make the read path fail rather than emit. */
    var readFailure: Throwable? = null

    override fun observe(): Flow<UserSettings> = readFailure?.let { cause -> flow { throw cause } } ?: settings.asSharedFlow()

    /**
     * Emit a value to whoever is collecting.
     *
     * Waits for a subscriber first. Without that the emission is dropped on a
     * flow with no replay, and the test then passes or fails on scheduling —
     * a green that means nothing ran.
     */
    suspend fun emit(value: UserSettings = UserSettings()) {
        settings.subscriptionCount.first { it > 0 }
        settings.emit(value)
    }

    override suspend fun update(transform: (UserSettings) -> UserSettings): Unit = error("the history screen does not write settings")
}
