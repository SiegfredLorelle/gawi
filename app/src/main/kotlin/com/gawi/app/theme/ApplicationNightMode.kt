package com.gawi.app.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import com.gawi.core.data.settings.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The chosen theme, told to the platform as well as to Compose.
 *
 * Compose alone would leave two things wrong, and both are outside its reach:
 *
 * - **The window before `setContent`.** `Theme.Gawi` is resolved from the
 *   `values-night` qualifier — the *system's* setting — so a cold start with
 *   Dark chosen on a light phone paints the light surface, then flips as soon
 *   as the first frame composes. `WindowBackgroundTest` exists because that
 *   flash is what a drifted colour looks like; a forced theme would produce it
 *   with no drift at all.
 * - **The system bars.** `enableEdgeToEdge()` resolves its icon appearance from
 *   the configuration too. `MainActivity` re-arms it from the resolved theme,
 *   which covers the app's own window; this covers the moment before it.
 *
 * Telling the platform fixes both at once, because it changes the
 * configuration: the qualifier picks `values-night` and the Activity is
 * recreated under it.
 *
 * **API 31 and up only.** `setApplicationNightMode` arrived in S, and there is
 * no earlier equivalent that does not drag in AppCompat — this app has no
 * AppCompat activity and `AppCompatDelegate.setDefaultNightMode` would not
 * reach a `ComponentActivity` anyway. On 29 and 30 the Compose side still draws
 * the chosen scheme correctly; what is lost is the pre-`setContent` window,
 * which flashes for one frame (docs/ux/settings.md §7).
 *
 * **The preference stays the source of truth.** The platform persists this
 * value itself, so the two could disagree — a preferences file restored onto a
 * device that was told something else. Re-applying on every process start is
 * what keeps them in step, and it is why this is called from a flow rather than
 * only from the settings write.
 */
@Singleton
class ApplicationNightMode @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Applies [mode] to the process, or does nothing below API 31.
     *
     * A `null` service is tolerated rather than asserted: this is a cosmetic
     * refinement of a theme the app has already drawn, and no colour is wrong
     * without it.
     */
    fun apply(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = context.getSystemService(UiModeManager::class.java) ?: return
        manager.setApplicationNightMode(mode.nightMode())
    }

    private fun ThemeMode.nightMode(): Int = when (this) {
        // AUTO rather than CUSTOM or NO: it means "whatever the device says",
        // which is exactly what SYSTEM means and what an unset app had before.
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO

        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO

        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }
}
