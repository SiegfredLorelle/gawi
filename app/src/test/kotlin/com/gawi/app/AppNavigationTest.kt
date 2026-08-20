package com.gawi.app

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.gawi.feature.habits.R as HabitsR
import com.gawi.feature.settings.R as SettingsR
import com.gawi.feature.today.R as TodayR

/**
 * The real app, launched: the Hilt graph, the navigation graph, and every route
 * leading where it says it does.
 *
 * The first test of anything above one module, and the first of the production
 * Hilt graph at all. Until now, only running the app on a device proved that
 * `DataModule` and `DataBindsModule` were satisfiable, that Room and DataStore
 * built from their real providers, and that `MainActivity` could reach a
 * composed screen. A missing binding is a runtime failure, so that was a gap
 * `make test` could not see.
 *
 * Driving [MainActivity] is not a stylistic choice. Every feature screen and
 * every ViewModel is `internal` to its own module, so `:app` cannot construct
 * one, and `hiltViewModel()` needs an `@AndroidEntryPoint` host, which the
 * compose rule's own activity is not. The real activity is both, and it is
 * already in the real manifest, so nothing test-only ships to make this work.
 *
 * Still a unit test: JVM, Robolectric, inside `make test`. Architecture §8's
 * "CI runs unit tests only" is untouched and there is still no `androidTest`
 * source set anywhere.
 *
 * Strings come from each feature module's own `R` class rather than `:app`'s,
 * because non-transitive R classes are the default — `:app`'s R holds only
 * `:app`'s resources. The values still merge, so these are the same strings the
 * screens render.
 *
 * ## What this deliberately does not test
 *
 * **Journeys that write.** Creating a habit here and asserting it appears on
 * Today looks like it works and does not: Room's `InvalidationTracker` does not
 * deliver in this setup, so a screen never re-reads after a write. Measured, not
 * assumed — injecting [com.gawi.core.data.repository.HabitRepository] and
 * calling `addCompletion` directly returns `Accepted`, and the row stays
 * unticked afterwards, across a navigation round trip. Written naively, such a
 * test passes only when a `WhileSubscribed(5_000)` window happens to lapse
 * between two assertions, which is a green that means nothing ran.
 *
 * Substituting the database would be the fix, and `@TestInstallIn` cannot reach
 * it: `DataModule` and `DataBindsModule` are `internal` to `:core:data` and
 * cannot be named from here. So write journeys stay where they are already
 * covered — `:core:data`'s own tests for the command path, the feature modules'
 * screen tests for what a state draws, and `docs/running.md` §5 on a device.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppNavigationTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // Order matters: Hilt's rule has to have run before the activity it injects
    // is launched, and the compose rule launches it on start.
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val resources get() = compose.activity.resources

    private fun string(id: Int): String = resources.getString(id)

    @Before
    fun setUp() = hilt.inject()

    /**
     * Waits for text, then returns it.
     *
     * The first read crosses a boundary Compose's idling cannot see: it builds
     * the database, repairs the projection, reads DataStore and comes back as a
     * `Flow` emission. `waitForIdle` reports idle while that is still in flight.
     */
    private fun awaitText(text: String): SemanticsNodeInteraction {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithText(text)
    }

    private fun awaitDescribed(label: String): SemanticsNodeInteraction {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithContentDescription(label)
    }

    /**
     * The app starts, and starts on Today.
     *
     * One assertion, and it covers a great deal: Hilt built the whole graph,
     * Room and DataStore came up from their production providers, the projection
     * was repaired on a first read, the `NavHost` resolved its start destination
     * and the Today route composed. Any missing binding fails here.
     */
    @Test
    fun theAppLaunchesOnTodayWithNoHabits() {
        awaitText(string(TodayR.string.today_empty_title)).assertIsDisplayed()
    }

    /** The empty state's button reaches the editor, in create mode. */
    @Test
    fun theEmptyStateLeadsToACreateForm() {
        awaitText(string(TodayR.string.today_add_habit)).performClick()

        awaitText(string(HabitsR.string.habits_new_title)).assertIsDisplayed()
        // Create, not edit: a route that passed a habit id where it meant none
        // would land on the same screen wearing the other title.
        compose.onNodeWithText(string(HabitsR.string.habits_edit_title)).assertDoesNotExist()
    }

    /** And cancelling comes back, rather than leaving the app. */
    @Test
    fun cancellingTheEditorReturnsToToday() {
        awaitText(string(TodayR.string.today_add_habit)).performClick()
        awaitText(string(HabitsR.string.habits_new_title))

        awaitDescribed(string(HabitsR.string.habits_cancel)).performClick()

        awaitText(string(TodayR.string.today_empty_title)).assertIsDisplayed()
    }

    /** The app bar reaches the habit list. */
    @Test
    fun todaysAppBarLeadsToTheHabitList() {
        awaitDescribed(string(TodayR.string.today_manage_habits)).performClick()

        awaitText(string(HabitsR.string.habits_title)).assertIsDisplayed()
        awaitText(string(HabitsR.string.habits_empty_title)).assertIsDisplayed()
    }

    /** And its back button comes back. */
    @Test
    fun theHabitListReturnsToToday() {
        awaitDescribed(string(TodayR.string.today_manage_habits)).performClick()
        awaitText(string(HabitsR.string.habits_title))

        awaitDescribed(string(HabitsR.string.habits_back)).performClick()

        awaitText(string(TodayR.string.today_empty_title)).assertIsDisplayed()
    }

    /** The list's own add button reaches the same editor. */
    @Test
    fun theHabitListLeadsToACreateForm() {
        awaitDescribed(string(TodayR.string.today_manage_habits)).performClick()

        awaitDescribed(string(HabitsR.string.habits_add)).performClick()

        awaitText(string(HabitsR.string.habits_new_title)).assertIsDisplayed()
    }

    /**
     * The app bar's other action reaches settings.
     *
     * Both app bar actions are glyphs with no text, so the content description
     * is the only thing telling them apart — and they were one symbol away from
     * being told apart wrongly, since the gear that now opens settings used to
     * open the habit list. Landing on the settings title rather than the habits
     * one is what pins that.
     */
    @Test
    fun todaysAppBarLeadsToSettings() {
        awaitDescribed(string(TodayR.string.today_settings)).performClick()

        awaitText(string(SettingsR.string.settings_title)).assertIsDisplayed()
        compose.onNodeWithText(string(HabitsR.string.habits_title)).assertDoesNotExist()
    }

    /**
     * And settings reads the real store, not a placeholder.
     *
     * The first read here goes through the production `DataStoreSettingsSource`
     * — the same path `TodayViewModel` uses for the cutoff — so a settings
     * screen that could not resolve its binding fails here rather than on a
     * device. With nothing written yet it shows the PRD's defaults, and Monday
     * is the one of the three that is a word rather than a formatted time.
     */
    @Test
    fun settingsShowsTheStoredDefaults() {
        awaitDescribed(string(TodayR.string.today_settings)).performClick()

        awaitText(string(SettingsR.string.settings_day_monday)).assertIsDisplayed()
    }

    /** And its back button comes back. */
    @Test
    fun settingsReturnsToToday() {
        awaitDescribed(string(TodayR.string.today_settings)).performClick()
        awaitText(string(SettingsR.string.settings_title))

        awaitDescribed(string(SettingsR.string.settings_back)).performClick()

        awaitText(string(TodayR.string.today_empty_title)).assertIsDisplayed()
    }

    private companion object {
        /**
         * Generous on purpose. The first wait in each test covers building the
         * database and repairing the projection, which is slower than anything
         * after it, and a flaky navigation test is worse than a slow one.
         */
        const val TIMEOUT_MILLIS = 10_000L
    }
}
