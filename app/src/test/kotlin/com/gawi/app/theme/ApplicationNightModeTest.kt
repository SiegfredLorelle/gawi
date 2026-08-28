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

    /**
     * **SYSTEM is asserted last, and that ordering is the test.**
     * `MODE_NIGHT_AUTO` is nought, which is also the shadow's untouched value,
     * so asserting it first would pass against an `apply` that did nothing at
     * all — and SYSTEM is the branch whose semantics the class KDoc argues
     * about at length. Dirtying the shadow with `MODE_NIGHT_YES` first is what
     * makes the last line mean "SYSTEM was applied" rather than "nothing has
     * happened yet". Checked by inverting it: an early return for SYSTEM leaves
     * this red.
     */
    @Test
    fun `each mode maps to the platform's own name for it`() {
        assertEquals(UiModeManager.MODE_NIGHT_NO, applied(ThemeMode.LIGHT))
        assertEquals(UiModeManager.MODE_NIGHT_YES, applied(ThemeMode.DARK))
        assertEquals(UiModeManager.MODE_NIGHT_AUTO, applied(ThemeMode.SYSTEM))
    }

    /**
     * Below API 31 there is nothing to call, and calling anyway would throw.
     *
     * minSdk is 29, so this is a supported device rather than a hypothetical
     * one. What it loses is documented in docs/ux/settings.md §8 and was
     * measured on an emulator of each level on 2026-08-28: the window painted
     * before `setContent` is the system's scheme for 66–331 ms of a cold start
     * on API 30, and 317–448 ms on API 29. It is the window and essentially
     * only the window — the wider costs this KDoc used to claim did not
     * survive the measurement.
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
