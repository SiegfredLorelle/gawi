package com.gawi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gawi.app.navigation.GawiNavHost
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
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not optional, and not only about insets. targetSdk 37 makes the status
        // bar transparent with no way out, and its icon colour comes from
        // windowLightStatusBar, which neither window theme sets and which
        // defaults to light icons. The Today view's app bar paints `surface`
        // straight under them, so in light mode they would be white on white.
        // This resolves the appearance from the actual theme, both ways round.
        enableEdgeToEdge()
        setContent {
            GawiTheme {
                GawiNavHost()
            }
        }
    }
}
