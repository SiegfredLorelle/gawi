package com.gawi.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gawi.feature.habits.HabitDetailRoute
import com.gawi.feature.habits.HabitEditorRoute
import com.gawi.feature.habits.HabitListRoute
import com.gawi.feature.insights.HistoryRoute
import com.gawi.feature.insights.InsightsRoute
import com.gawi.feature.settings.LicencesRoute
import com.gawi.feature.settings.SettingsRoute
import com.gawi.feature.today.TodayRoute

/**
 * The graph. Eight destinations, and every navigation decision the app makes.
 *
 * Each feature module exposes Route composables that take plain lambdas, so
 * what a screen reports is what happened to it — "the user wants to add a
 * habit" — and where that goes is decided here. That is what lets
 * `:feature:today`, `:feature:habits` and `:feature:insights` have no
 * navigation dependency at all, and what makes their tests need no
 * `NavController`.
 *
 * The history destination is that rule paying for itself: habit detail's "see
 * full history" is a lambda, and it lands in a *different module's* Route
 * without either feature knowing the other exists — which is the whole argument
 * architecture §2 uses for the heatmap living outside `:feature:habits`.
 *
 * Cancelling pops, and so does saving an edit. Creating does not: it goes on to
 * the new habit's detail screen, which is what `createHabit`'s minted id is for.
 * Keeping the three as separate callbacks is what keeps where each one lands a
 * decision here rather than inside `:feature:habits`.
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

    /**
     * A new habit's detail screen, with the form it came from removed.
     *
     * `popUpTo` inclusive rather than a plain navigate: without it, Back from
     * the new habit's detail lands in the editor that made it — a filled-in
     * create form that would append a second identical habit if saved again.
     */
    fun openCreatedHabit(habitId: String) = navController.navigate(Destination.HabitDetail(habitId)) {
        popUpTo(Destination.HabitEditor()) { inclusive = true }
        launchSingleTop = true
    }

    NavHost(navController = navController, startDestination = Destination.Today) {
        composable<Destination.Today> {
            TodayRoute(
                onAddHabit = { go(Destination.HabitEditor()) },
                onManageHabits = { go(Destination.Habits) },
                onOpenInsights = { go(Destination.Insights) },
                onOpenSettings = { go(Destination.Settings) },
            )
        }

        composable<Destination.Insights> {
            InsightsRoute(onBack = ::back)
        }

        composable<Destination.Habits> {
            HabitListRoute(
                onAddHabit = { go(Destination.HabitEditor()) },
                onOpenHabit = { habitId -> go(Destination.HabitDetail(habitId)) },
                onBack = ::back,
            )
        }

        composable<Destination.Settings> {
            SettingsRoute(onBack = ::back, onOpenLicences = { go(Destination.Licences) })
        }
        composable<Destination.Licences> {
            LicencesRoute(onBack = ::back)
        }

        composable<Destination.HabitEditor> { entry ->
            HabitEditorRoute(
                habitId = entry.toRoute<Destination.HabitEditor>().habitId,
                onCreated = ::openCreatedHabit,
                onSaved = ::back,
                onCancel = ::back,
            )
        }

        composable<Destination.HabitDetail> { entry ->
            HabitDetailRoute(
                habitId = entry.toRoute<Destination.HabitDetail>().habitId,
                onEdit = { habitId -> go(Destination.HabitEditor(habitId)) },
                onOpenHistory = { habitId -> go(Destination.HabitHistory(habitId)) },
                onBack = ::back,
            )
        }

        composable<Destination.HabitHistory> { entry ->
            HistoryRoute(
                habitId = entry.toRoute<Destination.HabitHistory>().habitId,
                onBack = ::back,
            )
        }
    }
}
