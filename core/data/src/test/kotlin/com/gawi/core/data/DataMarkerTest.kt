package com.gawi.core.data

import com.gawi.core.domain.id.EventId
import org.junit.Assert.assertEquals
import org.junit.Test

class DataMarkerTest {
    @Test
    fun `sees types from the domain module`() {
        val description = DataMarker.describe(EventId("0190163d-8694-7abc-8def-0123456789ab"))

        assertEquals("data sees domain: 0190163d-8694-7abc-8def-0123456789ab", description)
    }
}
