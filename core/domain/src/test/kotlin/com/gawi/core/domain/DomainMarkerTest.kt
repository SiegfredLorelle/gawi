package com.gawi.core.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainMarkerTest {
    @Test
    fun `serializes and deserializes round-trip`() {
        val marker = DomainMarker(name = "gawi")

        val json = Json.encodeToString(DomainMarker.serializer(), marker)
        val decoded = Json.decodeFromString(DomainMarker.serializer(), json)

        assertEquals(marker, decoded)
    }
}
