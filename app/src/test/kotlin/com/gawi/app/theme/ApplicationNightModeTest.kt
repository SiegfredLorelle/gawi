package com.gawi.app.theme

import android.app.UiModeManager
import com.gawi.core.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * That the platform is told the same thing Compose was told.
 *
 * Worth a test rather than trusted: the mapping is three constants, and getting
 * one wrong is invisible in the app itself — Compose would still draw the
 * chosen scheme, and only the pre-`setContent` window and the system bars would
 * disagree, which is exactly the flash this class exists to remove.
 */
@RunWith(RobolectricTestRunner::class)
class ApplicationNightModeTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun applied(mode: ThemeMode): Int {
        ApplicationNightMode(context).apply(mode)
        return shadowOf(context.getSystemService(UiModeManager::class.java)).applicationNightMode
    }

    @Test
    fun `each mode maps to the platform's own name for it`() {
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, applied(ThemeMode.SYSTEM))
        assertEquals(UiModeManager.MODE_NIGHT_NO, applied(ThemeMode.LIGHT))
        assertEquals(UiModeManager.MODE_NIGHT_YES, applied(ThemeMode.DARK))
    }

    /**
     * Below API 31 there is nothing to call, and calling anyway would throw.
     *
     * minSdk is 29, so this is a supported device rather than a hypothetical
     * one. What it loses is documented in docs/ux/settings.md §7: the window
     * painted before Compose runs still comes from the system's qualifier.
     *
     * `MODE_NIGHT_AUTO` is also the shadow's untouched value, which is worth
     * saying rather than leaving to be discovered: the assertion is "nothing
     * was set", and the reason it discriminates is that the mode applied here
     * is `DARK`, which would read back as `MODE_NIGHT_YES`.
     */
    @Test
    @Config(sdk = [30])
    fun `below API 31 nothing is applied and nothing throws`() {
        ApplicationNightMode(context).apply(ThemeMode.DARK)

        val manager = context.getSystemService(UiModeManager::class.java)
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, shadowOf(manager).applicationNightMode)
    }
}
