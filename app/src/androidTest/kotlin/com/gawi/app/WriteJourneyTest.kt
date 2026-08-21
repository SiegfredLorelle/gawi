package com.gawi.app

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.gawi.feature.habits.R as HabitsR
import com.gawi.feature.today.R as TodayR

/**
 * A completion, written through the real app and read back.
 *
 * **This is the test architecture §8 said could not be written**, and the source
 * set exists because of it. Under Robolectric + Hilt in `:app`, Room's
 * `InvalidationTracker` does not deliver, so a screen never re-reads after a
 * write and the tick never appears — measured, and written up in
 * `AppNavigationTest`'s KDoc. On a device it does deliver, so the loop the app
 * is actually made of (tap → event → projection → `Flow` → UI) is assertable
 * here for the first time by something other than a person looking at a screen.
 *
 * No Hilt test rule and no `HiltTestApplication`: this drives the installed
 * app's own graph and its own database, which is the point. Two consequences
 * worth stating rather than discovering:
 *
 * 1. **Running these DESTROYS the app's data on that device.** Not "adds to" —
 *    destroys. `connectedAndroidTest` uninstalls the app when it finishes, and
 *    an uninstall takes `/data/data` with it: the event log, every habit, the
 *    settings and the export stamp. Measured the hard way — a run here wiped an
 *    emulator carrying 345 events and 30 habits, and `allowBackup="false"`
 *    (architecture §6) means there is no OS copy to restore from, so
 *    export/import is the only way back.
 *
 *    **Never run `make itest` against a device holding habits you care about.**
 *    Export first, or use a throwaway AVD. The habit names are still stamped
 *    per run, which matters for the opposite reason: within a single run two
 *    tests must not read each other's rows.
 * 2. **It must not assume an empty app.** Every assertion is scoped to the row
 *    it created; nothing here counts rows or looks at the empty state.
 */
@RunWith(AndroidJUnit4::class)
class WriteJourneyTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int): String = compose.activity.resources.getString(id)

    /**
     * Waits for text before touching it.
     *
     * Compose's idling cannot see Room or DataStore, so the first read of any
     * screen — building the database, repairing the projection, reading
     * DataStore, coming back as a `Flow` emission — is still in flight while
     * `waitForIdle` reports idle. Every wait here is for a *specific* string for
     * the same reason.
     */
    private fun awaitText(text: String): SemanticsNodeInteraction {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onAllNodesWithText(text)[0]
    }

    private fun awaitDescribed(label: String): SemanticsNodeInteraction {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithContentDescription(label)
    }

    /**
     * Waits for the row to reach a toggle state, and asserts it got there.
     *
     * The wait has to be on the **state**, not on the row existing. Waiting for
     * the node reddens nothing, because the row is already on screen before the
     * tap — so the assertion would race tap to command to projection to Room
     * invalidation to recomposition, and pass only when the round trip happened
     * to win. That is the "green that means nothing" this file exists to avoid,
     * and `/code-review` found it here. Compose's idling cannot see Room, which
     * is exactly why the condition has to name what the write should produce.
     */
    private fun awaitRow(name: String, on: Boolean) {
        val state = if (on) isOn() else isOff()
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(name, substring = true) and isToggleable() and state)
                .fetchSemanticsNodes().isNotEmpty()
        }
        row(name).assert(state)
    }

    /** The row itself, not the name inside it: `HabitRow` puts the toggle on the whole row. */
    private fun row(name: String): SemanticsNodeInteraction {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodes(hasText(name, substring = true) and isToggleable()).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onAllNodes(hasText(name, substring = true) and isToggleable())[0]
    }

    /**
     * Cancel and Back are icon buttons and carry content descriptions; Save is a
     * `TextButton` and carries text. Reaching for the wrong one of those matches
     * nothing and fails as a timeout, which reads like a broken app rather than
     * a broken selector — so they are looked up the way each is actually built.
     */
    private fun createHabit(name: String) {
        awaitDescribed(string(TodayR.string.today_manage_habits)).performClick()
        awaitDescribed(string(HabitsR.string.habits_add)).performClick()
        awaitText(string(HabitsR.string.habits_new_title))

        // The label is the field's semantics text, so this resolves to the
        // OutlinedTextField itself. "Name" is unique among the three fields.
        awaitText(string(HabitsR.string.habits_name_label)).performTextInput(name)

        awaitText(string(HabitsR.string.habits_save)).performClick()
    }

    @Test
    fun aCompletionSurvivesTheRoundTripThroughTheRealDatabase() {
        val name = "itest-" + System.currentTimeMillis()

        createHabit(name)

        // Back on the habit list after saving; get to Today.
        awaitDescribed(string(HabitsR.string.habits_back)).performClick()

        // The tick appearing at all is the assertion Robolectric could not make:
        // it requires Room to have invalidated the query the screen is collecting.
        row(name).performScrollTo().assertIsOff()

        row(name).performClick()
        awaitRow(name, on = true)

        // And undo comes back, which is what the widget's toggle mirrors.
        row(name).performClick()
        awaitRow(name, on = false)
    }

    @Test
    fun theHabitJustCreatedIsVisibleOnToday() {
        val name = "itest-" + System.currentTimeMillis()

        createHabit(name)
        awaitDescribed(string(HabitsR.string.habits_back)).performClick()

        row(name).performScrollTo().assertIsDisplayed()
    }

    private companion object {
        /** Generous: the first wait covers opening the real database. */
        const val TIMEOUT_MILLIS = 15_000L
    }
}
