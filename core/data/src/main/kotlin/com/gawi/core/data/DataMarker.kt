package com.gawi.core.data

import com.gawi.core.domain.DomainMarker

/**
 * Placeholder proving the module wiring: an Android library that sees
 * `:core:domain`. Replaced by the event store and repositories in the
 * domain-core step (architecture §4).
 */
object DataMarker {
    fun describe(marker: DomainMarker): String = "data sees domain: ${marker.name}"
}
