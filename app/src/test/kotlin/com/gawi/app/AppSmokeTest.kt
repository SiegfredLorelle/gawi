package com.gawi.app

import com.gawi.core.data.DataMarker
import com.gawi.core.domain.DomainMarker
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSmokeTest {
    @Test
    fun `app sees both core modules`() {
        val description = DataMarker.describe(DomainMarker(name = "momo"))

        assertTrue(description.contains("momo"))
    }
}
