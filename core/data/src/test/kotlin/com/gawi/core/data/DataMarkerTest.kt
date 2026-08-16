package com.gawi.core.data

import com.gawi.core.domain.DomainMarker
import org.junit.Assert.assertEquals
import org.junit.Test

class DataMarkerTest {
    @Test
    fun `sees types from the domain module`() {
        val description = DataMarker.describe(DomainMarker(name = "gawi"))

        assertEquals("data sees domain: gawi", description)
    }
}
