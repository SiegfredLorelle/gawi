package com.gawi.core.data

import com.gawi.core.domain.id.EventId

/**
 * Placeholder proving the module wiring: an Android library that sees
 * `:core:domain`. Replaced by the event store and repositories in the
 * data-layer step (architecture §4).
 */
object DataMarker {
    fun describe(id: EventId): String = "data sees domain: $id"
}
