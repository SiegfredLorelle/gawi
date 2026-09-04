package com.gawi.app.theme

import android.app.UiModeManager
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.settings.ThemeMode
import com.gawi.core.data.settings.UserSettings
import com.gawi.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The theme, from the store to the two places it has to reach.
 *
 * Robolectric only for the `Context` [ApplicationNightMode] needs — the real
 * one is used rather than a fake, because the mapping it performs is the half
 * of this that a fake would assert nothing about.
 */
@RunWith(RobolectricTestRunner::class)
class ThemeViewModelTest {

    private val context = RuntimeEnvironment.getApplication()
    private val stored = MutableStateFlow(UserSettings())

    private class Source(private val settings: Flow<UserSettings>) : SettingsSource {
        override fun observe(): Flow<UserSettings> = settings

        override suspend fun update(transform: (UserSettings) -> UserSettings) = error("not used")
    }

    private fun viewModel(settings: Flow<UserSettings> = stored) = ThemeViewModel(Source(settings), ApplicationNightMode(context))

    private val appliedNightMode: Int
        get() = shadowOf(context.getSystemService(UiModeManager::class.java)).applicationNightMode

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    @Test
    fun `the stored mode is what the app draws`() = runTest {
        stored.value = UserSettings(theme = ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, viewModel().theme.value)
    }

    @Test
    fun `the platform is told as well as Compose`() = runTest {
        stored.value = UserSettings(theme = ThemeMode.LIGHT)

        viewModel()

        assertEquals(UiModeManager.MODE_NIGHT_NO, appliedNightMode)
    }

    /**
     * A flow that has not answered yet is not an answer.
     *
     * `null` rather than `SYSTEM`, so that the Activity's first frame is known
     * to be drawing the device's own scheme rather than a decision.
     */
    @Test
    fun `nothing is claimed before the store answers`() = runTest {
        assertNull(viewModel(settings = flow { }).theme.value)
    }

    /**
     * A read that is not IO is a bug, and a bug must not take the colours away.
     *
     * `observe()` already absorbs an unreadable file into the defaults, so this
     * is the other kind of failure — and the app still has to be drawable.
     */
    @Test
    fun `a broken read falls back to following the system`() = runTest {
        val broken = flow<UserSettings> { error("a bug") }

        assertEquals(ThemeMode.SYSTEM, viewModel(settings = broken).theme.value)
    }

    /**
     * And that fallback is **not** written to the platform.
     *
     * The whole reason `catch` sits downstream of the side effect. Applied, it
     * would clear a persisted override the user set — the platform would say
     * "follow the system" while the preferences file and the settings row both
     * still said Dark, and nothing would put them back. So the stored DARK is
     * what the platform keeps, even though this process draws SYSTEM.
     *
     * Found by `/code-review`.
     */
    @Test
    fun `a broken read does not clear the platform's override`() = runTest {
        stored.value = UserSettings(theme = ThemeMode.DARK)
        viewModel()
        assertEquals(UiModeManager.MODE_NIGHT_YES, appliedNightMode)

        val broken = flow<UserSettings> { error("a bug") }
        assertEquals(ThemeMode.SYSTEM, viewModel(settings = broken).theme.value)

        assertEquals(UiModeManager.MODE_NIGHT_YES, appliedNightMode)
    }
}
