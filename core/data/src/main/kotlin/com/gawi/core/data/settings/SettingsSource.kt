package com.gawi.core.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Where [UserSettings] is read and written.
 *
 * An interface rather than the store itself, because it is what lets the
 * repository's tests drive a non-midnight cutoff — the logical-date boundary is
 * the most bug-prone rule in the app, and a hardcoded midnight would leave it
 * untested at this level.
 *
 * A [Flow] rather than a plain read. The read path binds the day cutoff and the
 * week start into its queries, so anything holding one value for the life of a
 * collection would keep answering with it after an edit while the streak rows
 * joined into the same query had already been recomputed under the new one. It
 * is also the shape DataStore hands out natively.
 *
 * [update] takes a transform rather than a setter per field: a preferences file
 * is read-modify-write, and a setter per field would be a chance each to lose a
 * concurrent edit to a different one.
 */
interface SettingsSource {

    /** The current settings, re-emitted whenever they change. */
    fun observe(): Flow<UserSettings>

    /** The settings as of now, for the command path, which is not a flow. */
    suspend fun current(): UserSettings = observe().first()

    /** Applies [transform] to the stored settings and persists the result. */
    suspend fun update(transform: (UserSettings) -> UserSettings)
}
