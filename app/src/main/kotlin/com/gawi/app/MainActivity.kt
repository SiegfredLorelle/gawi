package com.gawi.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gawi.app.navigation.GawiNavHost
import com.gawi.app.theme.ThemeViewModel
import com.gawi.app.theme.resolvesToDark
import com.gawi.core.ui.theme.GawiTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The only activity, holding the only navigation graph.
 *
 * There was deliberately no host here while Today was the only screen: a graph
 * with one destination is a router with one route, and nothing could have got
 * its back-stack model right or wrong. The habits screens are the second and
 * third destinations, so the question is now a real one and
 * [com.gawi.app.navigation.Destination] is the answer to it.
 *
 * Single-activity, and the graph stays in `:app` (architecture §2). No feature
 * module depends on navigation, so no screen can route itself; each reports
 * what happened and [GawiNavHost] decides where that goes.
 *
 * `@AndroidEntryPoint` is load-bearing for more than this class: its generated
 * superclass supplies the Hilt factory that every destination's
 * `hiltViewModel()` resolves through.
 *
 * **It is also where the theme is resolved**, since 2026-08-26. The stored mode
 * is a `:core:data` preference and the system's setting is a fact about the
 * device; only something inside a composition knows both, and this is the one
 * place the whole app is inside (docs/ux/settings.md §7).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val themeViewModel: ThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not optional, and not only about insets. targetSdk 37 makes the status
        // bar transparent with no way out, and its icon colour comes from
        // windowLightStatusBar, which neither window theme sets and which
        // defaults to light icons. The Today view's app bar paints `surface`
        // straight under them, so in light mode they would be white on white.
        //
        // Called here for the first frame and again below once the stored theme
        // is known: this call resolves the appearance from the *configuration*,
        // which is the system's setting, and the user may have chosen the other
        // one.
        enableEdgeToEdge()
        setContent {
            val mode by themeViewModel.theme.collectAsStateWithLifecycle()
            val darkTheme = mode.resolvesToDark(isSystemInDarkTheme())

            // The bars follow the theme actually drawn rather than the device's.
            // A SideEffect rather than LaunchedEffect: this is a window call, it
            // must land with the frame that changed the scheme, and re-arming it
            // with the same value is a no-op.
            SideEffect { edgeToEdge(darkTheme) }

            GawiTheme(darkTheme = darkTheme) {
                GawiNavHost()
            }
        }
    }

    /**
     * Edge-to-edge with the bar appearance pinned to [darkTheme].
     *
     * Transparent scrims for both bars, which is what `enableEdgeToEdge()`'s own
     * defaults come to on this app's minimum: the navigation-bar scrim it would
     * otherwise supply only paints below API 29, and above it the platform's own
     * contrast enforcement — which `SystemBarStyle.auto` leaves on — is what
     * keeps the bar legible.
     */
    private fun edgeToEdge(darkTheme: Boolean) = enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
        navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
    )
}
