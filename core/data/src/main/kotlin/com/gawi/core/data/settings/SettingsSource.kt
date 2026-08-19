package com.gawi.core.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the event store reads [UserSettings].
 *
 * A seam rather than a store: the settings screen does not exist yet, so
 * [DefaultSettingsSource] answers with the PRD defaults and nothing persists.
 * The seam still earns its place now, because it is what lets the repository's
 * tests drive a non-midnight cutoff — the logical-date boundary is the most
 * bug-prone rule in the app, and a hardcoded midnight would leave it untested
 * at this level.
 *
 * A [Flow] rather than a plain read, even though today's implementation only
 * ever emits once. The read path binds the day cutoff and the week start into
 * its queries, so anything holding one value for the life of a collection would
 * keep answering with it after an edit while the streak rows joined into the
 * same query had already been recomputed under the new one. It is also the
 * shape DataStore hands out natively, so the real implementation fits without
 * this changing again (architecture §3 puts settings in DataStore, out of the
 * log).
 */
interface SettingsSource {

    /** The current settings, re-emitted whenever they change. */
    fun observe(): Flow<UserSettings>

    /** The settings as of now, for the command path, which is not a flow. */
    suspend fun current(): UserSettings = observe().first()
}

@Singleton
class DefaultSettingsSource @Inject constructor() : SettingsSource {

    override fun observe(): Flow<UserSettings> = flowOf(UserSettings())
}
