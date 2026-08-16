package com.gawi.core.domain

import kotlinx.serialization.Serializable

/**
 * Placeholder proving the module wiring: pure Kotlin/JVM with
 * kotlinx-serialization. Replaced by real event types in the domain-core
 * step (architecture §3).
 */
@Serializable
data class DomainMarker(val name: String)
