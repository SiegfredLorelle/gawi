package com.gawi.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gawi.feature.habits.HabitEditorRoute
import com.gawi.feature.habits.HabitListRoute
import com.gawi.feature.today.TodayRoute

/**
 * The graph. Three destinations, and every navigation decision the app makes.
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
    NavHost(navController = navController, startDestination = Destination.Today) {
        composable<Destination.Today> {
            TodayRoute(
                onAddHabit = { navController.navigate(Destination.HabitEditor()) },
                onManageHabits = { navController.navigate(Destination.Habits) },
            )
        }

        composable<Destination.Habits> {
            HabitListRoute(
                onAddHabit = { navController.navigate(Destination.HabitEditor()) },
                onEditHabit = { habitId -> navController.navigate(Destination.HabitEditor(habitId)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Destination.HabitEditor> { entry ->
            HabitEditorRoute(
                habitId = entry.toRoute<Destination.HabitEditor>().habitId,
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
