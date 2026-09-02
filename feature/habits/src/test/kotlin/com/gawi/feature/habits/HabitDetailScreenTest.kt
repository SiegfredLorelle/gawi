package com.gawi.feature.habits

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.date.weekdayLetter
import com.gawi.core.ui.streak.StreakUi
import com.gawi.core.ui.theme.GawiTheme
import com.gawi.feature.habits.testsupport.TODAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.DayOfWeek
import java.time.LocalDate
import com.gawi.core.ui.R as UiR

/**
 * What habit detail draws, and what its one action reports.
 *
 * Aimed at the stateless screen rather than the Route, so no Hilt graph and no
 * ViewModel are involved in a red result. docs/ux/today-view.md §5's streak
 * rules are the substance here: they are display decisions a reader could
 * delete by accident, and this is the second surface that has to honour them.
 */
@RunWith(RobolectricTestRunner::class)
class HabitDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // Robolectric's own accessor rather than ApplicationProvider, which would be
    // androidx.test:core — a different library, reached only transitively
    // through ui-test-junit4 and carrying no catalog entry to bump.
    private val resources = RuntimeEnvironment.getApplication().resources

    private fun render(state: HabitDetailUiState, actions: HabitDetailActions = NO_ACTIONS) {
        compose.setContent {
            GawiTheme { HabitDetailScreen(state, actions, SnackbarHostState()) }
        }
    }

    private fun string(id: Int): String = resources.getString(id)

    private fun quantity(id: Int, n: Int): String = resources.getQuantityString(id, n, n)

    /**
     * The streak's texts are read off the unmerged tree throughout: the panel
     * clears its subtree and speaks `spokenStreak`'s words instead, so what is
     * drawn and what is said are asserted separately, as on Today.
     */
    @Test
    fun aDailyStreak_isACount() {
        render(detail(streak = StreakUi.Days(12)))

        compose.onNodeWithText("12", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_detail_streak_days_caption), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(quantity(UiR.plurals.ui_streak_days_spoken, 12))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    /**
     * today-view §5: "A daily habit's streak is a count; a weekly habit's is in weeks. The
     * two must never be styled as the same number."
     *
     * The bare count is asserted absent, not just the `w` present — a weekly
     * streak that also rendered "3" somewhere would be the exact confusion today-view §5
     * forbids.
     */
    @Test
    fun aWeeklyStreak_isCountedInWeeksAndNeverAsABareNumber() {
        render(detail(streak = StreakUi.Weeks(3)))

        compose.onNodeWithText("3w", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_detail_streak_weeks_caption), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("3", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(quantity(UiR.plurals.ui_streak_weeks_spoken, 3)).assertIsDisplayed()
    }

    /**
     * today-view §5: a broken streak "keeps its old value as context (`was 4`) next to the
     * `0`, with a cut-thread glyph". All three parts, because the zero on its
     * own reads as a habit that never started.
     */
    @Test
    fun aBrokenStreak_keepsWhatWasLostBesideTheZero() {
        render(detail(streak = StreakUi.Broken(previous = 4, weekly = false)))

        compose.onNodeWithText(string(R.string.habits_detail_streak_broken), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.habits_detail_streak_was_days, 4), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_detail_streak_broken_glyph), useUnmergedTree = true).assertIsDisplayed()
        // One stop, in words — not the glyph, the zero and "was 4" as three.
        compose.onNodeWithContentDescription(quantity(UiR.plurals.ui_streak_broken_days_spoken, 4)).assertIsDisplayed()
    }

    /**
     * today-view §5 again, from the other side: never reading zero is a rule about a *live*
     * streak. A habit with no completions has nothing to draw, and a `0` here
     * would claim a break that never happened.
     */
    @Test
    fun aHabitWithNoCompletions_saysSoRatherThanDrawingZero() {
        render(detail(streak = StreakUi.None))

        compose.onNodeWithText(string(R.string.habits_detail_streak_none)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_detail_streak_broken)).assertDoesNotExist()
    }

    /**
     * Only a weekly habit draws "2/3 this week" — the Today row's rule, kept in
     * step, since a detail screen that disagreed with the row that led to it
     * would be its own bug.
     *
     * Two tests rather than one with two renders: the compose rule's activity
     * takes `setContent` once, and a second call throws rather than redrawing.
     */
    @Test
    fun weekProgress_isDrawnForAWeeklyHabit() {
        render(detail(weekProgress = HabitWeekProgress(done = 2, target = 3)))

        compose.onNodeWithText(resources.getString(R.string.habits_detail_week_progress, 2, 3)).assertIsDisplayed()
    }

    @Test
    fun weekProgress_isAbsentForADailyHabit() {
        render(detail(weekProgress = null))

        compose.onNodeWithText(resources.getString(R.string.habits_detail_week_progress, 2, 3)).assertDoesNotExist()
    }

    /**
     * Edit reports the habit the screen is showing.
     *
     * It closes over the state rather than over anything the Route captured, so
     * a screen that had re-read a different habit cannot send the old id to the
     * editor and quietly edit the wrong one.
     */
    @Test
    fun editAction_reportsTheHabitOnScreen() {
        var edited: HabitId? = null
        render(detail(id = OTHER), NO_ACTIONS.copy(onEdit = { edited = it }))

        compose.onNodeWithContentDescription(string(R.string.habits_detail_edit)).performClick()

        assertEquals(OTHER, edited)
    }

    /**
     * The history door reports the habit the screen is showing.
     *
     * Closed over the state rather than over anything the Route captured, for
     * the reason edit is: a screen that had re-read a different habit would
     * otherwise open the old one's history and look right doing it.
     *
     * `performScrollTo` because the button sits under the strip, which is off a
     * short screen at this font scale.
     */
    @Test
    fun historyAction_reportsTheHabitOnScreen() {
        var opened: HabitId? = null
        render(detail(id = OTHER), NO_ACTIONS.copy(onHistory = { opened = it }))

        compose.onNodeWithText(string(R.string.habits_detail_history)).performScrollTo().performClick()

        assertEquals(OTHER, opened)
    }

    /**
     * And is absent when there is nothing to edit.
     *
     * On `Unavailable` there is no id to hand back, so the action would either
     * navigate nowhere or need an id it does not have.
     */
    @Test
    fun editAction_isAbsentWhenThereIsNoHabit() {
        var edited: HabitId? = null
        render(HabitDetailUiState.Unavailable, NO_ACTIONS.copy(onEdit = { edited = it }))

        compose.onNodeWithContentDescription(string(R.string.habits_detail_edit)).assertDoesNotExist()
        assertNull(edited)
    }

    @Test
    fun anArchivedHabit_saysSo() {
        render(detail(archived = true))

        compose.onNodeWithText(string(R.string.habits_detail_archived)).assertIsDisplayed()
    }

    // ---- the retro strip and the honesty prompt ----

    private fun cellLabel(back: Long, shut: Boolean = false, done: Boolean = false, hasNote: Boolean = false): String {
        val day = TODAY.minusDays(back).dayOfMonth
        val id = when {
            shut -> R.string.habits_strip_shut
            done -> R.string.habits_strip_done
            else -> R.string.habits_strip_not_done
        }
        val noted = if (hasNote) ". " + string(R.string.habits_strip_has_note) else ""
        val label = resources.getString(id, day) + noted
        if (shut) return label
        val action = string(if (done) R.string.habits_strip_undo else R.string.habits_strip_complete)
        // A completed open day also offers the note, and the label says so.
        val note = if (done) ". " + string(R.string.habits_strip_note) else ""
        return "$label. $action$note"
    }

    /**
     * The day outside the window is drawn, and tapping it does nothing at all.
     *
     * docs/ux/today-view.md §5: days outside the retro window are "drawn shut,
     * not tapped and refused". Both halves are asserted, because either alone
     * passes under the wrong implementation — a cell that is absent is also
     * never tapped, and a cell that reports a rejection is also on screen.
     */
    @Test
    fun theShutDay_isDrawnAndReportsNothing() {
        val writes = mutableListOf<LocalDate>()
        render(detail(), NO_ACTIONS.copy(onToggle = { _, date, _ -> writes += date }))

        compose.onNodeWithContentDescription(cellLabel(back = 4, shut = true)).assertIsDisplayed()
        compose.onNodeWithContentDescription(cellLabel(back = 4, shut = true)).performClick()

        assertTrue(writes.isEmpty())
        compose.onNodeWithText(string(R.string.habits_retro_title)).assertDoesNotExist()
    }

    /**
     * A past day asks before it writes, and asking is not writing.
     *
     * PRD §6.4 wants retroactive edits to carry deliberate friction. The write
     * being absent at this point is the whole assertion: a prompt that appeared
     * *after* the log had already changed would be theatre.
     */
    @Test
    fun aPastDay_promptsBeforeItWrites() {
        val writes = mutableListOf<LocalDate>()
        render(detail(), NO_ACTIONS.copy(onToggle = { _, date, _ -> writes += date }))

        compose.onNodeWithContentDescription(cellLabel(back = 2)).performClick()

        compose.onNodeWithText(string(R.string.habits_retro_body)).assertIsDisplayed()
        assertTrue(writes.isEmpty())
    }

    /** Confirming writes to the cell's own date, not to today. */
    @Test
    fun confirmingThePrompt_writesToThatCellsDate() {
        var written: Pair<LocalDate, Boolean>? = null
        render(detail(), NO_ACTIONS.copy(onToggle = { _, date, completed -> written = date to completed }))

        compose.onNodeWithContentDescription(cellLabel(back = 2)).performClick()
        compose.onNodeWithText(string(R.string.habits_retro_confirm)).performClick()

        assertEquals(TODAY.minusDays(2) to false, written)
        compose.onNodeWithText(string(R.string.habits_retro_body)).assertDoesNotExist()
    }

    /**
     * Cancelling means nothing happened.
     *
     * The prompt is UI friction with nothing enforcing it (architecture §5), so
     * dismissing has to leave the log untouched rather than defer a write.
     */
    @Test
    fun dismissingThePrompt_writesNothing() {
        val writes = mutableListOf<LocalDate>()
        render(detail(), NO_ACTIONS.copy(onToggle = { _, date, _ -> writes += date }))

        compose.onNodeWithContentDescription(cellLabel(back = 2)).performClick()
        compose.onNodeWithText(string(R.string.habits_cancel)).performClick()

        assertTrue(writes.isEmpty())
        compose.onNodeWithText(string(R.string.habits_retro_body)).assertDoesNotExist()
    }

    /**
     * Un-completing a past day prompts too.
     *
     * PRD §5 says "editing a past day" triggers the confirmation, and removing a
     * completion is as much a rewrite of the record as adding one. §6.4's
     * frictionless case is same-day undo, which is the next test.
     */
    @Test
    fun undoingAPastDay_promptsAsWell() {
        var written: Pair<LocalDate, Boolean>? = null
        render(
            detail(strip = strip(completed = setOf(TODAY.minusDays(2)))),
            NO_ACTIONS.copy(onToggle = { _, date, completed -> written = date to completed }),
        )

        compose.onNodeWithContentDescription(cellLabel(back = 2, done = true)).performClick()
        compose.onNodeWithText(string(R.string.habits_retro_body)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.habits_retro_confirm)).performClick()

        // completed = true, so the ViewModel undoes rather than adds.
        assertEquals(TODAY.minusDays(2) to true, written)
    }

    /**
     * Today writes straight through, with no prompt.
     *
     * PRD §6.4: "same-day undo is frictionless". A prompt here would put
     * friction on the flow the whole app is built around.
     */
    @Test
    fun todaysCell_writesWithoutAsking() {
        var written: Pair<LocalDate, Boolean>? = null
        render(detail(), NO_ACTIONS.copy(onToggle = { _, date, completed -> written = date to completed }))

        compose.onNodeWithContentDescription(cellLabel(back = 0)).performClick()

        assertEquals(TODAY to false, written)
        compose.onNodeWithText(string(R.string.habits_retro_body)).assertDoesNotExist()
    }

    /**
     * A note is offered on a completed open day, and on nothing else.
     *
     * Read off the long-click label rather than by performing the gesture: what
     * is being pinned is which cells *offer* the action, and the label is how
     * that reaches assistive technology as well as the eye.
     */
    @Test
    fun theNoteAction_isOfferedOnlyOnACompletedOpenDay() {
        render(detail(strip = strip(completed = setOf(TODAY.minusDays(2), TODAY.minusDays(4)))))

        // Completed and open: the label ends with the note action.
        compose.onNodeWithContentDescription(cellLabel(back = 2, done = true)).assertIsDisplayed()

        // Open but not completed: nothing to annotate, so the plain label stands.
        compose.onNodeWithContentDescription(cellLabel(back = 1)).assertIsDisplayed()

        // Completed but shut: inert, so no note action either.
        compose.onNodeWithContentDescription(cellLabel(back = 4, shut = true)).assertIsDisplayed()
    }

    /**
     * An archived habit's strip answers nothing at all.
     *
     * Every write is rejected for an archived habit, so a live cell could only
     * produce a snackbar. Today's cell is the one worth naming: it is the one
     * that would otherwise still look tappable.
     */
    @Test
    fun anArchivedHabitsStrip_reportsNothing() {
        val writes = mutableListOf<LocalDate>()
        render(
            detail(archived = true, strip = strip(archived = true)),
            NO_ACTIONS.copy(onToggle = { _, date, _ -> writes += date }),
        )

        compose.onNodeWithContentDescription(cellLabel(back = 0, shut = true)).performClick()
        compose.onNodeWithContentDescription(cellLabel(back = 2, shut = true)).performClick()

        assertTrue(writes.isEmpty())
        compose.onNodeWithText(string(R.string.habits_retro_title)).assertDoesNotExist()
    }

    /**
     * A day with a note says so, out loud.
     *
     * The dot is the visual half; this is the half TalkBack gets, and the
     * sharper one — without it an annotated day and a bare one are announced
     * identically, so a note is discoverable only by long-pressing each cell.
     */
    @Test
    fun aDayWithANote_announcesIt() {
        render(detail(strip = strip(completed = setOf(TODAY.minusDays(2)), notes = mapOf(TODAY.minusDays(2) to "went far"))))

        compose.onNodeWithContentDescription(cellLabel(back = 2, done = true, hasNote = true)).assertIsDisplayed()
    }

    /** And a completed day without one does not claim to have a note. */
    @Test
    fun aCompletedDayWithoutANote_doesNotClaimOne() {
        render(detail(strip = strip(completed = setOf(TODAY.minusDays(2)))))

        compose.onNodeWithContentDescription(cellLabel(back = 2, done = true)).assertIsDisplayed()
    }

    /**
     * The marker is drawn on the annotated day and on no other.
     *
     * Counted rather than merely asserted present: a marker drawn on every cell
     * would satisfy "the annotated day has one" and tell the reader nothing.
     */
    @Test
    fun theNoteMarker_isDrawnOnlyOnAnAnnotatedDay() {
        render(
            detail(
                strip = strip(
                    completed = setOf(TODAY.minusDays(1), TODAY.minusDays(2)),
                    notes = mapOf(TODAY.minusDays(2) to "went far"),
                ),
            ),
        )

        compose.onAllNodesWithText(NOTE_MARKER, useUnmergedTree = true).assertCountEquals(1)
    }

    /** None at all when nothing is annotated. */
    @Test
    fun theNoteMarker_isAbsentWhenNoDayCarriesANote() {
        render(detail(strip = strip(completed = setOf(TODAY.minusDays(1)))))

        compose.onAllNodesWithText(NOTE_MARKER, useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * The open cell is a checkbox to assistive technology and says which way it
     * is set. Written before `cellAction` swapped `semantics` for
     * `clearAndSetSemantics` (2026-09-02): Compose clears every semantics
     * modifier *after* the clearing one in the chain, so the `combinedClickable`
     * that gives the cell its role and its click has to stay ahead of it, and
     * this is the assertion that says whether it did. Reversing the two turns
     * it red.
     */
    @Test
    fun anOpenCell_isACheckboxThatReportsItsState() {
        render(detail(strip = strip(completed = setOf(TODAY.minusDays(2)))))

        compose.onNodeWithContentDescription(cellLabel(back = 1))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsToggleable()
            .assertIsOff()
            .assertHasClickAction()
        compose.onNodeWithContentDescription(cellLabel(back = 2, done = true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsOn()
    }

    /**
     * The note is a long-press with a label, and it exists only where the
     * description promises it — a completed open day. Pinned for the reason the
     * test above gives: the label rides the `combinedClickable`.
     */
    @Test
    fun theNoteAction_isALongClickOnlyWhereItIsOffered() {
        render(detail(strip = strip(completed = setOf(TODAY.minusDays(2)))))

        val offered = compose.onNodeWithContentDescription(cellLabel(back = 2, done = true))
            .fetchSemanticsNode().config[SemanticsActions.OnLongClick]
        assertEquals(string(R.string.habits_strip_note), offered.label)
        compose.onNodeWithContentDescription(cellLabel(back = 1))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        compose.onNodeWithContentDescription(cellLabel(back = 4, shut = true))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }

    /** A shut day is not a control that is off; it is a day with nothing to press. */
    @Test
    fun theShutCell_isDisabledAndNotABox() {
        render(detail())

        compose.onNodeWithContentDescription(cellLabel(back = 4, shut = true))
            .assertIsNotEnabled()
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
    }

    /**
     * The description is the whole announcement. TalkBack 17 read a cell's four
     * drawn texts after its label (docs/running.md §4, 2026-09-02); now they are
     * drawn and not in the merged tree. The unmerged count says they are still
     * drawn — a cell that stopped painting its dot would also pass the first
     * half.
     */
    @Test
    fun aCell_speaksItsLabelAndNothingElse() {
        render(detail(strip = strip(completed = setOf(TODAY.minusDays(2)))))

        compose.onNodeWithContentDescription(cellLabel(back = 1))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        compose.onNodeWithContentDescription(cellLabel(back = 4, shut = true))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        compose.onAllNodesWithText(EMPTY_MARKER).assertCountEquals(0)
        compose.onAllNodesWithText(EMPTY_MARKER, useUnmergedTree = true).assertCountEquals(4)
    }

    private companion object {
        val HABIT = HabitId("00000000-0000-7000-8000-000000000001")
        val OTHER = HabitId("00000000-0000-7000-8000-000000000002")

        val NO_ACTIONS = HabitDetailActions(
            onEdit = {},
            onToggle = { _, _, _ -> },
            onNote = { _, _, _ -> },
            onHistory = {},
            onBack = {},
        )

        /**
         * Suppressed here for the reason the fixture builders elsewhere are: every
         * parameter is defaulted, so a test names only the field it is about.
         */
        @Suppress("LongParameterList")
        fun detail(
            id: HabitId = HABIT,
            name: String = "read",
            schedule: ScheduleUi = ScheduleUi.Daily,
            tag: String? = null,
            archived: Boolean = false,
            completedToday: Boolean = false,
            weekProgress: HabitWeekProgress? = null,
            streak: StreakUi = StreakUi.None,
            strip: List<RetroCellUi> = strip(),
        ) = HabitDetailUiState.Detail(
            id = id,
            name = name,
            icon = "📖",
            iconTint = null,
            schedule = schedule,
            tag = tag,
            archived = archived,
            completedToday = completedToday,
            weekProgress = weekProgress,
            streak = streak,
            strip = strip,
        )

        /**
         * Five cells ending on [TODAY], with `today - 4` shut, exactly as the
         * mapper builds them. Built here rather than by calling the mapper so a
         * screen test stays a statement about drawing and not about mapping.
         */
        fun strip(completed: Set<LocalDate> = emptySet(), notes: Map<LocalDate, String> = emptyMap(), archived: Boolean = false) =
            (0L..4L).reversed().map { back ->
                val date = TODAY.minusDays(back)
                RetroCellUi(
                    date = date,
                    dayLabel = weekdayLetter(DayOfWeek.MONDAY),
                    dayOfMonth = date.dayOfMonth,
                    completed = date in completed,
                    note = notes[date],
                    open = back <= RETRO_WINDOW && !archived,
                    isToday = back == 0L,
                )
            }

        const val RETRO_WINDOW = 3L

        /** Mirrors RetroStrip's NOTE_GLYPH, which is private to the composable. */
        const val NOTE_MARKER = "•"

        /** Mirrors RetroStrip's EMPTY_GLYPH, for the same reason. */
        const val EMPTY_MARKER = "·"
    }

    /**
     * A strip cell is big enough to hit: WCAG 2.5.5 and Android's minimum.
     *
     * **What this does and does not hold, measured rather than assumed.** It
     * asserts the outcome — the cell is at least 48dp tall — and *not* that
     * `RetroCell`'s `defaultMinSize` is what gets it there. Deleting that
     * modifier outright leaves this test green, because the cell's four stacked
     * lines plus its vertical padding already exceed 48dp, so the minimum never
     * binds. The modifier is correct insurance for a cell that loses content; it
     * is simply not what fails here.
     *
     * Kept anyway, because the property is worth holding whatever satisfies it:
     * a cell that shrank below the floor would fail this, and it is the only
     * assertion on the strip's size.
     *
     * Against a literal and not against `GawiSpacing.TouchTarget`, for the reason
     * `HabitEditorScreenTest` gives: a constant compared with itself cannot fail.
     *
     * Height only. The cells are `weight(1f)` across the row, so width is the
     * screen's to decide and a narrow enough phone would fail an assertion this
     * code could not satisfy.
     */
    @Test
    fun aStripCellMeetsTheTouchTargetFloor() {
        render(detail())

        compose.onNodeWithContentDescription(cellLabel(back = 2))
            .performScrollTo()
            .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }
}

/** WCAG 2.5.5, and Android's own floor. A literal on purpose — see the test above. */
private val MIN_TOUCH_TARGET = 48.dp
