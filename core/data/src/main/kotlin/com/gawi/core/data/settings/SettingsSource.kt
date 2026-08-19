package com.gawi.core.data.settings

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
 * Replaced by a DataStore-backed implementation when settings become editable
 * (architecture §3 puts them in DataStore, out of the log).
 */
interface SettingsSource {

    suspend fun current(): UserSettings
}

@Singleton
class DefaultSettingsSource @Inject constructor() : SettingsSource {

    override suspend fun current(): UserSettings = UserSettings()
}
