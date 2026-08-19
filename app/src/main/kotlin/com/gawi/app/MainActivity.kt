package com.gawi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gawi.core.ui.theme.GawiTheme
import com.gawi.feature.today.TodayRoute
import dagger.hilt.android.AndroidEntryPoint

/**
 * The one screen there is.
 *
 * No navigation host. Architecture §2 gives :app the navigation graph, and a
 * graph with one destination is a router with one route — it would add a
 * back-stack model and a route vocabulary that nothing here can exercise, so
 * nothing here could get them right or wrong. The second screen is the first
 * change that asks a real navigation question, and it can be answered then.
 *
 * @AndroidEntryPoint is load-bearing for more than this class: its generated
 * superclass supplies the default ViewModel factory that lets the Today view
 * resolve a @HiltViewModel through plain viewModel(), with no navigation
 * library involved.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GawiTheme {
                TodayRoute()
            }
        }
    }
}
