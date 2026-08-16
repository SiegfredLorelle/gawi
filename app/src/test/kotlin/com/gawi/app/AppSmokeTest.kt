package com.gawi.app

import com.gawi.core.data.DataMarker
import com.gawi.core.domain.id.EventId
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSmokeTest {
    @Test
    fun `app sees both core modules`() {
        val id = "0190163d-8694-7abc-8def-0123456789ab"

        val description = DataMarker.describe(EventId(id))

        assertTrue(description.contains(id))
    }
}
