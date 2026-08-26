package com.gawi.app.theme

import com.gawi.core.data.settings.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one decision in the theme path that a screenshot cannot catch.
 *
 * Both halves matter: a forced mode must ignore the device, and the two
 * deferring cases — `SYSTEM`, and the `null` before the store has answered —
 * must follow it in *both* directions. A `when` that returned `false` for the
 * unread case would look correct on every light-mode device.
 */
class ThemeResolutionTest {

    @Test
    fun `a forced mode ignores what the device says`() {
        assertTrue(ThemeMode.DARK.resolvesToDark(systemDark = false))
        assertTrue(ThemeMode.DARK.resolvesToDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.resolvesToDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.resolvesToDark(systemDark = false))
    }

    @Test
    fun `following the system means both ways round`() {
        assertTrue(ThemeMode.SYSTEM.resolvesToDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.resolvesToDark(systemDark = false))
    }

    @Test
    fun `nothing read yet draws what the device is already showing`() {
        assertTrue(null.resolvesToDark(systemDark = true))
        assertFalse(null.resolvesToDark(systemDark = false))
    }
}
