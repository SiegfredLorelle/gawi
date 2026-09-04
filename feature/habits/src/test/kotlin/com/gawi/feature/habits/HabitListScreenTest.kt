package com.gawi.feature.habits

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.testing.habitId
import com.gawi.core.ui.theme.GawiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The habit list, rendered.
 *
 * On the JVM under Robolectric, so this runs inside the existing `make test`
 * gate and architecture §8's "CI runs unit tests only" stays true. Aimed at
 * [HabitListScreen] rather than [HabitListRoute]: the stateless composable is
 * the whole of what is under test, so no Hilt graph and no ViewModel are
 * involved in a red result.
 *
 * What it catches that the mapper and ViewModel tests structurally cannot: the
 * mapper says which list a habit is in and the ViewModel says which command a
 * tap sends, and neither one can see that the archived row offers the wrong
 * word or that the two actions are wired to each other's targets.
 *
 * Strings come from resources rather than being written out here, so these
 * survive a copy edit and fail only on a behaviour change.
 */
@RunWith(RobolectricTestRunner::class)
class HabitListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // Robolectric's own accessor rather than ApplicationProvider, which would be
    // androidx.test:core — a different library, reached only transitively and
    // carrying no catalog entry to bump.
    private val resources = RuntimeEnvironment.getApplication().resources

    private fun string(id: Int): String = resources.getString(id)

    /** The Archive button's whole accessible name for one row; its drawn word is cleared, so this is what a test finds. */
    private fun archive(name: String): String = resources.getString(R.string.habits_archive_spoken, name)

    private fun bringBack(name: String): String = resources.getString(R.string.habits_unarchive_spoken, name)

    private fun render(state: HabitListUiState, actions: HabitListActions = NO_ACTIONS) {
        compose.setContent {
            GawiTheme { HabitListScreen(state, actions, SnackbarHostState()) }
        }
    }

    @Test
    fun emptyState_saysToAddOneRatherThanNothing() {
        render(HabitListUiState.Empty)

        compose.onNodeWithText(string(R.string.habits_empty_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_empty_body)).assertIsDisplayed()
    }

    @Test
    fun loading_claimsNothingEitherWay() {
        render(HabitListUiState.Loading)

        compose.onNodeWithText(string(R.string.habits_empty_title)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.habits_unavailable_title)).assertDoesNotExist()
    }

    @Test
    fun unavailable_tellsTheUserToReopen() {
        render(HabitListUiState.Unavailable)

        compose.onNodeWithText(string(R.string.habits_unavailable_title)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_unavailable_body)).assertIsDisplayed()
    }

    /**
     * The archived section is labelled when there is one.
     *
     * The heading is what makes an archived habit findable rather than merely
     * present, which is the whole reason there is no show-archived toggle.
     */
    @Test
    fun archivedSection_isLabelledAndListsWhatIsArchived() {
        render(HabitListUiState.Habits(active = listOf(READ), archived = listOf(OLD)))

        compose.onNodeWithText(string(R.string.habits_archived_header)).assertIsDisplayed()
        compose.onNodeWithText(OLD.name).assertIsDisplayed()
    }

    /** And is absent when nothing is, rather than an empty labelled section. */
    @Test
    fun archivedSection_isAbsentWhenNothingIsArchived() {
        render(HabitListUiState.Habits(active = listOf(READ, SWIM), archived = emptyList()))

        compose.onNodeWithText(string(R.string.habits_archived_header)).assertDoesNotExist()
    }

    /**
     * An archived row offers the way back, not the way in again.
     *
     * The load-bearing one. Archiving is idempotent under last-write-wins, so
     * offering "Archive" on an already-archived habit would be accepted by the
     * domain, write a redundant event, and leave the habit exactly where it was
     * — a dead button with no error to show for it.
     *
     * By the spoken name rather than the drawn word, because the drawn word is
     * cleared from semantics (HabitManageRow says why) and is found in neither
     * tree.
     */
    @Test
    fun archivedRow_offersBringBackAndActiveRowOffersArchive() {
        render(HabitListUiState.Habits(active = listOf(READ), archived = listOf(OLD)))

        compose.onNodeWithContentDescription(archive(READ.name)).assertIsDisplayed()
        compose.onNodeWithContentDescription(bringBack(OLD.name)).assertIsDisplayed()
    }

    /** The row's own archived state travels with the tap, not the screen's. */
    @Test
    fun archiveTap_reportsTheRowsOwnState() {
        val reported = mutableListOf<Pair<HabitId, Boolean>>()
        render(
            HabitListUiState.Habits(active = listOf(READ), archived = listOf(OLD)),
            NO_ACTIONS.copy(onArchiveToggle = { id, archived -> reported += id to archived }),
        )

        compose.onNodeWithContentDescription(archive(READ.name)).performClick()
        compose.onNodeWithContentDescription(bringBack(OLD.name)).performClick()

        assertEquals(listOf(READ.id to false, OLD.id to true), reported)
    }

    /**
     * Each button is named for its own row, and no bare "Archive" survives in
     * any form. Accessibility Scanner on a device listed fourteen buttons with
     * one description (docs/running.md §4, 2026-09-02). The click action is
     * asserted too: it is the reason the button takes `semantics` and not
     * `clearAndSetSemantics`, which would have taken the click with the text.
     */
    @Test
    fun archiveButtons_areNamedForTheirOwnRow() {
        render(HabitListUiState.Habits(active = listOf(READ, SWIM), archived = emptyList()))

        compose.onNodeWithContentDescription(archive(READ.name)).assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithContentDescription(archive(SWIM.name)).assertIsDisplayed().assertHasClickAction()
        compose.onAllNodesWithContentDescription(string(R.string.habits_archive)).assertCountEquals(0)
        compose.onAllNodesWithText(string(R.string.habits_archive)).assertCountEquals(0)
        // …and still drawn: the label is cleared from semantics, not from the screen.
        compose.onAllNodesWithText(string(R.string.habits_archive), useUnmergedTree = true).assertCountEquals(2)
    }

    /**
     * The spoken name begins with the drawn word (WCAG 2.5.3), and a copy edit
     * to one side alone fails here. That the word is *drawn* is the unmerged
     * assertion in [archiveButtons_areNamedForTheirOwnRow]; this compares the
     * two strings.
     */
    @Test
    fun archiveDescription_leadsWithTheDrawnWord() {
        assertTrue(archive("x").startsWith(string(R.string.habits_archive)))
        assertTrue(bringBack("x").startsWith(string(R.string.habits_unarchive)))
    }

    /**
     * The icon badge is not in the tree. On this unmerged row it was a stop of
     * its own, reading the emoji's Unicode name (docs/running.md §4,
     * 2026-09-02). Pinned here because `:core:ui`, which owns `HabitIcon`, has
     * no Compose tests.
     */
    @Test
    fun iconBadge_isDecorative() {
        render(HabitListUiState.Habits(active = listOf(READ), archived = emptyList()))

        compose.onAllNodesWithText(READ.icon).assertCountEquals(0)
        // Cleared from semantics, not from the screen — the unmerged tree keeps it.
        compose.onAllNodesWithText(READ.icon, useUnmergedTree = true).assertCountEquals(1)
    }

    /**
     * Tapping a habit opens it, and does not archive it.
     *
     * The two actions sit in the same row, so this is the pair worth pinning
     * together: a name that archived what it meant to open would lose a habit
     * off the list with no way to tell it had happened.
     *
     * The name leads to detail rather than the editor
     * (docs/ux/habits.md §6). What this asserts is unchanged either way: the tap
     * reports the row's own id on the opening action and touches nothing else.
     */
    @Test
    fun nameTap_opensTheHabitWithoutArchivingIt() {
        val opened = mutableListOf<HabitId>()
        val archived = mutableListOf<HabitId>()
        render(
            HabitListUiState.Habits(active = listOf(READ, SWIM), archived = emptyList()),
            NO_ACTIONS.copy(onOpen = { opened += it }, onArchiveToggle = { id, _ -> archived += id }),
        )

        compose.onNodeWithText(SWIM.name).performClick()

        assertEquals(listOf(SWIM.id), opened)
        assertTrue(archived.isEmpty())
    }

    @Test
    fun addButton_isNamedAndReportsAnAdd() {
        var added = 0
        render(HabitListUiState.Empty, NO_ACTIONS.copy(onAdd = { added++ }))

        compose.onNodeWithContentDescription(string(R.string.habits_add)).performClick()

        assertEquals(1, added)
    }

    /**
     * A weekly habit's schedule reads as a target and a daily one says so.
     *
     * The list is where someone checks what they set, so a weekly habit whose
     * subtitle read "Every day" would be wrong in the one place it is looked up.
     */
    @Test
    fun scheduleLine_distinguishesDailyFromWeekly() {
        render(HabitListUiState.Habits(active = listOf(READ, SWIM), archived = emptyList()))

        compose.onNodeWithText(string(R.string.habits_schedule_daily)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.habits_schedule_weekly, 3)).assertIsDisplayed()
    }

    private companion object {
        val NO_ACTIONS = HabitListActions(
            onAdd = {},
            onOpen = {},
            onArchiveToggle = { _, _ -> },
            onBack = {},
        )

        /** Daily and active. */
        val READ = HabitListRowUi(
            id = habitId(1),
            name = "read",
            icon = "📖",
            iconTint = null,
            schedule = ScheduleUi.Daily,
            archived = false,
        )

        /** Weekly and active, so the schedule line has both forms to tell apart. */
        val SWIM = HabitListRowUi(
            id = habitId(2),
            name = "swim",
            icon = "🏃",
            iconTint = null,
            schedule = ScheduleUi.Weekly(3),
            archived = false,
        )

        /** Archived, and the only row that is. */
        val OLD = HabitListRowUi(
            id = habitId(3),
            name = "journal",
            icon = "✍️",
            iconTint = null,
            schedule = ScheduleUi.Daily,
            archived = true,
        )
    }
}
