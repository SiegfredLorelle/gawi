package com.gawi.app.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.util.Log
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
 * reach a `ComponentActivity` anyway.
 *
 * **What 29 and 30 lose is one flash of the starting window, measured
 * 2026-08-28 on an API 30 emulator.** With Dark chosen and the system in
 * light, nine cold starts sampled off a `screenrecord` showed the light
 * `#F4FBFA` window for 70–330 ms before the dark app replaced it. Two wider
 * claims stood here first, both reasoned from this code rather than watched,
 * and the emulator retired both. `ThemeViewModel.theme` does start `null` and
 * resolve to the *system* scheme, but the read beats the first composed frame:
 * a light-scheme content frame appeared only with the page cache dropped, once
 * each in four runs of six, and never in three warm ones. And the Recents
 * snapshot does not hold the system's scheme at all — it is a screenshot of
 * the window, so it shows what was drawn. On 31 and up none of it applies: the
 * override is already in the configuration before the process starts, so
 * `isSystemInDarkTheme()` is right on the first frame and the `null` start
 * costs nothing. docs/ux/settings.md §8 carries the numbers.
 *
 * **`MODE_NIGHT_AUTO` is how "follow the system" is expressed, and the platform
 * documents it as something else.** There is no call that *clears* a per-app
 * override — the javadoc says the mode "is persisted for this application until
 * it is either modified by the application, the user clears the data for the
 * application, or this application is uninstalled" — so the only way back from
 * a forced theme is to set another mode, and `AUTO` is the one whose AOSP
 * implementation maps to "inherit". The javadoc for it says instead that it
 * "automatically switches between night and notnight based on the device's
 * current location and certain other sensors", which is not what this app
 * means by System. **Measured on API 37**: after Dark, then System, the app
 * followed a quick-settings dark toggle in both directions, so the AOSP
 * behaviour is the inherit one. A ROM that honoured the javadoc literally
 * would leave System behaving as twilight-based dark instead, which is
 * recorded in docs/ux/settings.md §8 rather than guarded against — the
 * alternative, not calling at all for System, leaves the previous override
 * pinned forever, which is worse and is not a guess.
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
     * **Nothing here may throw, and that is load-bearing rather than
     * defensive.** This is a cosmetic refinement of a theme the app has already
     * drawn — no colour is wrong without it — but it is called from the flow
     * `MainActivity` reads its scheme from, on `viewModelScope`, which has no
     * `CoroutineExceptionHandler`. An escaping exception there is not a missing
     * refinement; it is the app failing to launch for anyone whose stored theme
     * is not the default. `setApplicationNightMode` reaches `system_server` and
     * rethrows a `RemoteException` from it, so the throw is real rather than
     * hypothetical, and a null service is the same tolerance one line earlier.
     *
     * Not covered by a JVM test: Robolectric's `UiModeManager` shadow cannot be
     * made to fail, and a seam that existed only to inject one would be a
     * bigger change than the call it guards. Logged so a device can say so.
     */
    fun apply(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val manager = context.getSystemService(UiModeManager::class.java) ?: return
        runCatching { manager.setApplicationNightMode(mode.nightMode()) }
            .onFailure { cause -> Log.w(TAG, "could not tell the platform about $mode", cause) }
    }

    private fun ThemeMode.nightMode(): Int = when (this) {
        // AUTO, for the reason the KDoc gives at length: it is the only mode
        // whose implementation means "inherit", and there is no call that
        // removes an override outright.
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO

        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO

        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }

    private companion object {
        const val TAG = "ApplicationNightMode"
    }
}
