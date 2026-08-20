package com.gawi.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gawi.feature.habits.HabitEditorRoute
import com.gawi.feature.habits.HabitListRoute
import com.gawi.feature.settings.SettingsRoute
import com.gawi.feature.today.TodayRoute

/**
 * The graph. Four destinations, and every navigation decision the app makes.
 *
 * Each feature module exposes Route composables that take plain lambdas, so
 * what a screen reports is what happened to it — "the user wants to add a
 * habit" — and where that goes is decided here. That is what lets
 * `:feature:today` and `:feature:habits` have no navigation dependency at all,
 * and what makes their tests need no `NavController`.
 *
 * Saving and cancelling both pop today. They are separate callbacks anyway,
 * because they are different events and a future undo snackbar or a
 * navigate-to-the-new-habit would only need one of them.
 */
@Composable
internal fun GawiNavHost(navController: NavHostController = rememberNavController()) {
    /**
     * Every navigation goes through here, single-top.
     *
     * Without it, two quick taps on one button push two identical entries and
     * Back has to be pressed twice to undo one intent.
     */
    fun go(destination: Destination) = navController.navigate(destination) { launchSingleTop = true }

    /**
     * And every pop, guarded.
     *
     * `popBackStack()` on the last entry empties the stack and the host renders
     * nothing — a blank screen with no way out. Checking for something to
     * return to makes a duplicate pop a no-op instead.
     */
    fun back() {
        if (navController.previousBackStackEntry != null) navController.popBackStack()
    }

    NavHost(navController = navController, startDestination = Destination.Today) {
        composable<Destination.Today> {
            TodayRoute(
                onAddHabit = { go(Destination.HabitEditor()) },
                onManageHabits = { go(Destination.Habits) },
                onOpenSettings = { go(Destination.Settings) },
            )
        }

        composable<Destination.Habits> {
            HabitListRoute(
                onAddHabit = { go(Destination.HabitEditor()) },
                onEditHabit = { habitId -> go(Destination.HabitEditor(habitId)) },
                onBack = ::back,
            )
        }

        composable<Destination.Settings> {
            SettingsRoute(onBack = ::back)
        }

        composable<Destination.HabitEditor> { entry ->
            HabitEditorRoute(
                habitId = entry.toRoute<Destination.HabitEditor>().habitId,
                onSaved = ::back,
                onCancel = ::back,
            )
        }
    }
}
