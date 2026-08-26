package com.gawi.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The codes are the file format, so this pins them by value: a reordering of
 * the enum that left `ordinal` doing the work would pass every other test.
 */
class ThemeModeTest {

    @Test
    fun `the codes are the ones already written to disk`() {
        assertEquals(0, ThemeMode.SYSTEM.code)
        assertEquals(1, ThemeMode.LIGHT.code)
        assertEquals(2, ThemeMode.DARK.code)
    }

    @Test
    fun `every mode round-trips through its code`() {
        ThemeMode.entries.forEach { mode -> assertEquals(mode, ThemeMode.fromCode(mode.code)) }
    }

    @Test
    fun `an absent or unknown code is null rather than a guess`() {
        assertNull(ThemeMode.fromCode(null))
        assertNull(ThemeMode.fromCode(-1))
        assertNull(ThemeMode.fromCode(ThemeMode.entries.size))
    }
}
