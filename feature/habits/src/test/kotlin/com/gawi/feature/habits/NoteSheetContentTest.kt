package com.gawi.feature.habits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.gawi.core.ui.theme.GawiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * What the note sheet's three buttons report.
 *
 * Aimed at the content rather than the `ModalBottomSheet` that hosts it, which
 * is why the content is a composable of its own: a sheet's animation and window
 * are not what these rules are about, and driving one under Robolectric would
 * make a red result mean something else.
 */
@RunWith(RobolectricTestRunner::class)
class NoteSheetContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val resources = RuntimeEnvironment.getApplication().resources

    private val saved = mutableListOf<String>()
    private var cancelled = 0

    private fun render(initial: String) {
        compose.setContent {
            GawiTheme {
                NoteSheetContent(
                    date = "16",
                    initial = initial,
                    onSave = { saved += it },
                    onCancel = { cancelled++ },
                )
            }
        }
    }

    private fun string(id: Int): String = resources.getString(id)

    @Test
    fun theFieldOpensOnTheNoteThatIsThere() {
        render("went far")

        compose.onNodeWithText("went far").assertIsDisplayed()
    }

    @Test
    fun saveReportsWhatWasTyped() {
        render("")

        compose.onNodeWithText(string(R.string.habits_note_label)).performTextInput("rained")
        compose.onNodeWithText(string(R.string.habits_save)).performClick()

        assertEquals(listOf("rained"), saved)
    }

    /**
     * docs/ux/today-view.md §5: "Clear note is a button, not a disabled Save."
     *
     * An empty note is a real write that wins last-write-wins (architecture §4),
     * so removing one has to be something you can ask for rather than something
     * you discover by emptying a field and finding Save greyed out.
     */
    @Test
    fun clearIsItsOwnButtonAndSaveStaysEnabled() {
        render("went far")

        compose.onNodeWithText(string(R.string.habits_note_clear)).assertIsDisplayed()

        // Emptying the field and saving is still a legal write, not a dead end.
        compose.onNodeWithText("went far").performTextClearance()
        compose.onNodeWithText(string(R.string.habits_save)).performClick()

        assertEquals(listOf(""), saved)
    }

    /**
     * Clear writes an empty note rather than dismissing.
     *
     * It reports the same write Save does with an empty field, because that is
     * what clearing a note *is* (architecture §4). What §5 asks for is the
     * button; this pins that the button actually writes.
     */
    @Test
    fun clearWritesAnEmptyNote() {
        render("went far")

        compose.onNodeWithText(string(R.string.habits_note_clear)).performClick()

        assertEquals(listOf(""), saved)
        assertEquals(0, cancelled)
    }

    /** Nothing to remove, nothing to offer: a clear here would write no change. */
    @Test
    fun clearIsAbsentWhenThereIsNoNote() {
        render("")

        compose.onNodeWithText(string(R.string.habits_note_clear)).assertDoesNotExist()
    }

    /** Cancel means nothing changed — the rule every dialog in the app follows. */
    @Test
    fun cancelWritesNothing() {
        render("went far")

        compose.onNodeWithText("went far").performTextClearance()
        compose.onNodeWithText(string(R.string.habits_cancel)).performClick()

        assertEquals(1, cancelled)
        assertTrue(saved.isEmpty())
    }
}
