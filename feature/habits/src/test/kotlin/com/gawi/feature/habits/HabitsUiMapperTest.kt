package com.gawi.feature.habits

import androidx.compose.ui.graphics.Color
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import com.gawi.core.testing.broken
import com.gawi.core.testing.daysAgo
import com.gawi.core.testing.habitDetail
import com.gawi.core.testing.habitState
import com.gawi.core.testing.running
import com.gawi.core.testing.todayHabit
import com.gawi.core.ui.streak.StreakUi
import com.gawi.core.ui.theme.HabitPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The habits screens' display and form decisions, asserted without a device. */
class HabitsUiMapperTest {

    @Test
    fun `no habits at all is its own state, not two empty lists`() {
        assertEquals(HabitListUiState.Empty, emptyList<Nothing>().toListUiState())
    }

    @Test
    fun `active and archived habits are separated, not flagged in one list`() {
        val state = listOf(
            habitState(id = habitId(1), name = "read"),
            habitState(id = habitId(2), name = "swim", archived = true),
        ).toListUiState() as HabitListUiState.Habits

        assertEquals(listOf("read"), state.active.map { it.name })
        assertEquals(listOf("swim"), state.archived.map { it.name })
        assertTrue(state.archived.single().archived)
        assertFalse(state.active.single().archived)
    }

    @Test
    fun `only archived habits is still a list, not the empty state`() {
        // The distinction that matters for the copy: "no habits yet" is wrong
        // for someone who has archived all of theirs, because bringing one back
        // is exactly what they came here to do.
        val state = listOf(habitState(archived = true)).toListUiState()

        assertTrue(state is HabitListUiState.Habits)
        assertEquals(1, (state as HabitListUiState.Habits).archived.size)
        assertTrue(state.active.isEmpty())
    }

    @Test
    fun `a row carries the parsed colour and falls back when it is not one`() {
        assertEquals(Color(0xFF7E57C2), habitState(color = "#7E57C2").toRowUi().iconTint)
        assertNull(habitState(color = "not a colour").toRowUi().iconTint)
    }

    @Test
    fun `a schedule keeps its weekly target through the round trip`() {
        assertEquals(ScheduleUi.Daily, Schedule.Daily.toUi())
        assertEquals(ScheduleUi.Weekly(3), Schedule.Weekly(3).toUi())
        assertEquals(Schedule.Daily, ScheduleUi.Daily.toDomain())
        assertEquals(Schedule.Weekly(3), ScheduleUi.Weekly(3).toDomain())
    }

    /**
     * The clamp, which is the whole reason [ScheduleUi] exists as a separate
     * type. `Schedule.Weekly` validates with `require`, so without coercing here
     * an out-of-range target would throw on the save button rather than being
     * saved wrong or rejected.
     */
    @Test
    fun `a weekly target out of range is clamped rather than thrown`() {
        assertEquals(Schedule.Weekly(7), ScheduleUi.Weekly(8).toDomain())
        assertEquals(Schedule.Weekly(7), ScheduleUi.Weekly(70).toDomain())
        assertEquals(Schedule.Weekly(1), ScheduleUi.Weekly(0).toDomain())
        assertEquals(Schedule.Weekly(1), ScheduleUi.Weekly(-3).toDomain())
    }

    @Test
    fun `an existing habit opens with every field filled in`() {
        val form = habitState(
            name = "read",
            icon = "📖",
            color = "#7E57C2",
            schedule = Schedule.Weekly(4),
            tag = "growth",
        ).toForm()

        assertTrue(form.editing)
        assertEquals("read", form.name)
        assertEquals("📖", form.icon)
        assertEquals("#7E57C2", form.color)
        assertEquals(ScheduleUi.Weekly(4), form.schedule)
        assertEquals("growth", form.tag)
    }

    @Test
    fun `a habit with no tag opens with an empty field, not the word null`() {
        assertEquals("", habitState(tag = null).toForm().tag)
    }

    @Test
    fun `a new habit starts savable except for its name`() {
        val form = newHabitForm()

        assertFalse(form.editing)
        assertEquals("", form.name)
        assertFalse(form.canSave)
        // Icon and colour are already chosen, so typing a name is the only thing
        // between a first run and a first habit.
        assertEquals(HabitPalette.DefaultIcon, form.icon)
        assertEquals(HabitPalette.DefaultColor, form.color)
        assertEquals(ScheduleUi.Daily, form.schedule)
    }

    @Test
    fun `a blank name is not savable, and whitespace is still blank`() {
        assertFalse(newHabitForm().copy(name = "").canSave)
        assertFalse(newHabitForm().copy(name = "   ").canSave)
        assertTrue(newHabitForm().copy(name = "read").canSave)
    }

    @Test
    fun `an empty tag field saves as no tag rather than as an empty one`() {
        assertNull(newHabitForm().copy(name = "read", tag = "").toMetadata().tag)
        assertNull(newHabitForm().copy(name = "read", tag = "  ").toMetadata().tag)
        assertEquals("growth", newHabitForm().copy(name = "read", tag = "growth").toMetadata().tag)
    }

    /**
     * The name is submitted untrimmed, matching `Commands.createHabit`, which
     * tests `isBlank()` on whatever it is handed. Agreeing by construction
     * rather than by luck — if this trimmed, a name of only spaces would pass
     * `canSave` here and be rejected there.
     */
    @Test
    fun `the name reaches the domain exactly as it was typed`() {
        assertEquals(" read ", newHabitForm().copy(name = " read ").toMetadata().name)
    }

    /**
     * A swatch without a name would announce itself as raw hex, and the labels
     * are matched to colours by position, so the two lists have to stay level.
     */
    @Test
    fun `every palette colour has a name to be read out`() {
        assertEquals(HabitPalette.Colors.size, COLOR_LABELS.size)
        assertEquals(COLOR_LABELS.size, COLOR_LABELS.toSet().size)
    }

    @Test
    fun `a form submits every field, because an update is not a patch`() {
        val metadata = newHabitForm()
            .copy(name = "swim", icon = "🏃", color = "#26A69A", schedule = ScheduleUi.Weekly(2), tag = "health")
            .toMetadata()

        assertEquals("swim", metadata.name)
        assertEquals("🏃", metadata.icon)
        assertEquals("#26A69A", metadata.color)
        assertEquals(Schedule.Weekly(2), metadata.schedule)
        assertEquals("health", metadata.tag)
    }

    /**
     * Detail counts a daily habit in days and a weekly one in weeks.
     *
     * The rule itself lives in `:core:ui` and is asserted there; what this pins
     * is that detail passes the habit's *own* schedule to it. Passing a constant
     * would make every streak a day count, which reads plausibly and is wrong
     * for exactly the habits docs/ux/today-view.md §5 is about.
     */
    @Test
    fun `a detail streak is counted in the habit's own unit`() {
        val daily = habitDetail(todayHabit(habitState(schedule = Schedule.Daily), streak = running(4)))
        val weekly = habitDetail(todayHabit(habitState(schedule = Schedule.Weekly(3)), streak = running(4)))

        assertEquals(StreakUi.Days(4), daily.toDetailUiState().streak)
        assertEquals(StreakUi.Weeks(4), weekly.toDetailUiState().streak)
    }

    @Test
    fun `a broken detail streak keeps what was lost`() {
        val habit = habitDetail(todayHabit(habitState(schedule = Schedule.Daily), streak = broken(previous = 4)))

        assertEquals(StreakUi.Broken(previous = 4, weekly = false), habit.toDetailUiState().streak)
    }

    /** Only a weekly habit carries week progress — the Today row's rule, kept in step. */
    @Test
    fun `only a weekly habit carries week progress on detail`() {
        val weekly = habitDetail(todayHabit(habitState(schedule = Schedule.Weekly(3)), weekCount = 2))
        val daily = habitDetail(todayHabit(habitState(schedule = Schedule.Daily), weekCount = 2))

        assertEquals(HabitWeekProgress(done = 2, target = 3), weekly.toDetailUiState().weekProgress)
        assertNull(daily.toDetailUiState().weekProgress)
    }

    /**
     * A blank tag is no tag.
     *
     * `HabitMetadata.tag` is nullable and the editor writes blank-to-null, but an
     * imported log is not bound by the editor's rule. A blank that reached the
     * header would draw a lone "#".
     */
    @Test
    fun `a blank tag is dropped rather than drawn`() {
        assertNull(habitDetail(todayHabit(habitState(tag = "  "))).toDetailUiState().tag)
        assertEquals("focus", habitDetail(todayHabit(habitState(tag = "focus"))).toDetailUiState().tag)
    }

    /** Detail shows archived habits, so the flag has to survive the mapping. */
    @Test
    fun `detail carries the archived flag rather than hiding the habit`() {
        assertTrue(habitDetail(todayHabit(habitState(archived = true))).toDetailUiState().archived)
        assertFalse(habitDetail(todayHabit(habitState(archived = false))).toDetailUiState().archived)
    }

    /**
     * The strip is five cells: the writable window, plus the day drawn shut.
     *
     * docs/ux/today-view.md §5 wants the limit readable before it is hit, which
     * needs one refused day on screen. Four cells would draw only legal writes
     * and say nothing about the edge; six would start being the Phase 1 heatmap.
     */
    @Test
    fun `the strip runs from the shut day to today`() {
        val strip = habitDetail().toDetailUiState().strip

        assertEquals((0L..4L).map { daysAgo(it) }.reversed(), strip.map { it.date })
        assertEquals(FIXED_DATE, strip.last().date)
        assertTrue(strip.last().isToday)
    }

    /**
     * Exactly the oldest cell is shut, and the boundary is the domain's own.
     *
     * `Commands.addCompletion` accepts `today - 3` and rejects `today - 4`, so
     * those are the two cells worth naming: one either side of the rule the
     * strip exists to make visible.
     */
    @Test
    fun `only the day outside the retro window is drawn shut`() {
        val strip = habitDetail().toDetailUiState().strip.associateBy { it.date }

        assertFalse(strip.getValue(daysAgo(4)).open)
        assertTrue(strip.getValue(daysAgo(3)).open)
        assertTrue(strip.getValue(FIXED_DATE).open)
        assertEquals(1, strip.values.count { !it.open })
    }

    /** A refused day still reports whether it was done. It is shut, not hidden. */
    @Test
    fun `a shut day still shows that it was completed`() {
        val detail = habitDetail(recent = mapOf(daysAgo(4) to null))

        val shut = detail.toDetailUiState().strip.single { it.date == daysAgo(4) }
        assertFalse(shut.open)
        assertTrue(shut.completed)
    }

    /**
     * Absent means not completed; a null value means completed with no note.
     *
     * The read maps a cell with a cleared note to null, so conflating the two
     * would draw a day someone had deliberately un-annotated as a day they had
     * never done.
     */
    @Test
    fun `a completed day with no note is not the same as a missing day`() {
        val detail = habitDetail(recent = mapOf(daysAgo(1) to null, daysAgo(2) to "went far"))
        val strip = detail.toDetailUiState().strip.associateBy { it.date }

        assertTrue(strip.getValue(daysAgo(1)).completed)
        assertNull(strip.getValue(daysAgo(1)).note)
        assertTrue(strip.getValue(daysAgo(2)).completed)
        assertEquals("went far", strip.getValue(daysAgo(2)).note)
        assertFalse(strip.getValue(daysAgo(3)).completed)
    }

    /** Only today is today, whatever else is completed. */
    @Test
    fun `exactly one cell is today`() {
        val strip = habitDetail().toDetailUiState().strip

        assertEquals(listOf(FIXED_DATE), strip.filter { it.isToday }.map { it.date })
    }

    /**
     * An archived habit's strip is shut end to end.
     *
     * `Commands` rejects every completion write on an archived habit —
     * addCompletion, undoCompletion and updateCompletionNote alike — so a live
     * cell could only answer a tap with a refusal, which is the
     * tapped-and-refused pattern docs/ux/today-view.md §5 exists to prevent.
     * Today's cell included: it is the one most likely to look actionable.
     */
    @Test
    fun `an archived habit's cells are all shut`() {
        val strip = habitDetail(todayHabit(habitState(archived = true))).toDetailUiState().strip

        assertTrue(strip.none { it.open })
        assertEquals(5, strip.size)
        assertFalse(strip.single { it.isToday }.open)
    }

    /** And bringing it back opens the window again — the flag is the only difference. */
    @Test
    fun `an unarchived habit's window is open again`() {
        val strip = habitDetail(todayHabit(habitState(archived = false))).toDetailUiState().strip

        assertEquals(4, strip.count { it.open })
    }

    /** A shut day still reports its note, the same way it still reports the tick. */
    @Test
    fun `hasNote follows the note, on shut days too`() {
        val detail = habitDetail(
            todayHabit(habitState(archived = true)),
            recent = mapOf(daysAgo(1) to "went far", daysAgo(2) to null),
        )
        val strip = detail.toDetailUiState().strip.associateBy { it.date }

        assertTrue(strip.getValue(daysAgo(1)).hasNote)
        assertFalse(strip.getValue(daysAgo(2)).hasNote)
        assertFalse(strip.getValue(daysAgo(3)).hasNote)
    }
}
