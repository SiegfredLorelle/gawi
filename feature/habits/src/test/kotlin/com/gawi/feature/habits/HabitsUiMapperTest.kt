package com.gawi.feature.habits

import androidx.compose.ui.graphics.Color
import com.gawi.core.domain.model.Schedule
import com.gawi.core.ui.theme.HabitPalette
import com.gawi.feature.habits.testsupport.habitId
import com.gawi.feature.habits.testsupport.habitState
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
}
