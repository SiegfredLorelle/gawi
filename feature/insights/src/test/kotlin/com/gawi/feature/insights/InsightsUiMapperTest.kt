package com.gawi.feature.insights

import com.gawi.core.data.model.ReadContext
import com.gawi.core.data.model.TagEffort
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.feature.insights.testsupport.TODAY
import com.gawi.feature.insights.testsupport.habitId
import com.gawi.feature.insights.testsupport.habitState
import com.gawi.feature.insights.testsupport.thisMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * What the app-wide screen makes of the three reads.
 *
 * The arithmetic worth pinning is the arithmetic that would look plausible
 * wrong: an active-day count that summed instead of unioning, a bar scaled to
 * the sum instead of the largest, an archived habit counted as adherence, and a
 * habit rated over a period it did not exist for.
 */
class InsightsUiMapperTest {

    private val context = ReadContext(today = TODAY, weekStart = DayOfWeek.MONDAY)

    private fun overview(
        period: Period = Period.MONTH,
        breakdown: Breakdown = Breakdown.HABITS,
        habits: List<HabitState> = emptyList(),
        completions: Map<HabitId, Set<LocalDate>> = emptyMap(),
        tagEffort: List<TagEffort> = emptyList(),
    ) = overviewOf(period, breakdown, context, PeriodReads(habits, completions, tagEffort))

    /**
     * Two habits done on one day is **one** active day. A sum here would count
     * the same day twice and inflate the number the whole headline rests on.
     */
    @Test
    fun `active days are a union of dates and completions are a sum`() {
        val state = overview(
            completions = mapOf(
                habitId(1) to setOf(thisMonth(3), thisMonth(4)),
                habitId(2) to setOf(thisMonth(4), thisMonth(5)),
            ),
        )

        assertEquals(3, state.activeDays)
        assertEquals(4, state.completions)
    }

    @Test
    fun `an empty period is zeroes and no rows`() {
        val state = overview()

        assertEquals(0, state.activeDays)
        assertEquals(0, state.completions)
        assertTrue(state.habits.isEmpty())
        assertTrue(state.tags.isEmpty())
    }

    // ---- the tag breakdown ----

    /**
     * Scaled to the largest, not to the sum. Career at 86 of a 230 total would
     * be a third of the track if these were shares of the whole, and the longest
     * bar being a third is the reading this deliberately does not take.
     */
    @Test
    fun `bars scale to the largest total, biggest first`() {
        val state = overview(tagEffort = listOf(TagEffort("health", 61), TagEffort("career", 86), TagEffort("mind", 43)))

        assertEquals(listOf("career", "health", "mind"), state.tags.map { it.name })
        assertEquals(1f, state.tags.first().fill)
        assertEquals(0.5f, state.tags.last().fill)
    }

    /**
     * Untagged is last however big it is. It is the residual rather than a
     * competitor, and a row called "Untagged" at the top would read as a tag
     * that beat the others.
     */
    @Test
    fun `untagged sorts last even when it is the largest`() {
        val state = overview(tagEffort = listOf(TagEffort(null, 900), TagEffort("career", 3)))

        assertEquals(listOf("career", null), state.tags.map { it.name })
        // And it still measures against the largest, so it is the full bar.
        assertEquals(1f, state.tags.last().fill)
    }

    @Test
    fun `tags with equal totals hold a stable order`() {
        val state = overview(tagEffort = listOf(TagEffort("mind", 5), TagEffort("career", 5)))

        assertEquals(listOf("career", "mind"), state.tags.map { it.name })
    }

    // ---- the habit breakdown ----

    /**
     * Archived habits are excluded here and counted in the tag totals, which is
     * the asymmetry the data layer already draws: effort spent does not stop
     * having happened, but adherence is a question in the present tense.
     */
    @Test
    fun `an archived habit is not in the adherence list`() {
        val state = overview(
            habits = listOf(
                habitState(id = habitId(1), name = "read"),
                habitState(id = habitId(2), name = "run", archived = true),
            ),
        )

        assertEquals(listOf("read"), state.habits.map { it.name })
    }

    /**
     * `hasAnyHabit` reads the **unfiltered** list, which is the whole point of it
     * being a separate field.
     *
     * An all-archived user has habits and an empty adherence list at the same
     * time, and the screen needs to tell that apart from a fresh install — it
     * showed the archived copy to new users until review caught it.
     */
    @Test
    fun `an all-archived user has habits even though the list is empty`() {
        val archived = overview(habits = listOf(habitState(archived = true)))

        assertTrue(archived.hasAnyHabit)
        assertTrue(archived.habits.isEmpty())

        val fresh = overview(habits = emptyList())

        assertEquals(false, fresh.hasAnyHabit)
        assertTrue(fresh.habits.isEmpty())
    }

    @Test
    fun `habits keep the order they arrived in, not the order of their rates`() {
        val state = overview(
            habits = listOf(habitState(id = habitId(1), name = "apples"), habitState(id = habitId(2), name = "bananas")),
            completions = mapOf(habitId(2) to setOf(thisMonth(1), thisMonth(2), thisMonth(3))),
        )

        assertEquals(listOf("apples", "bananas"), state.habits.map { it.name })
    }

    /**
     * A habit created after the period draws a dash, never a zero.
     *
     * Zero would be the screen reporting that someone failed a period they were
     * not in. This is what the projected creation date bought.
     */
    @Test
    fun `a habit newer than the period has no rate`() {
        val state = overview(
            period = Period.MONTH,
            habits = listOf(habitState(createdOn = TODAY.plusMonths(2))),
        )

        assertEquals(null, state.habits.single().percent)
    }

    /** And one created mid-period is measured from its creation, not from the 1st. */
    @Test
    fun `a habit created mid-period is not charged for the days before it`() {
        val born = habitState(createdOn = thisMonth(12))
        val since = (12..17).map { thisMonth(it) }.toSet()

        val state = overview(habits = listOf(born), completions = mapOf(born.id to since))

        assertEquals(100, state.habits.single().percent)
    }

    @Test
    fun `each habit row says what its rate is a rate of`() {
        val state = overview(
            habits = listOf(
                habitState(id = habitId(1), name = "read"),
                habitState(id = habitId(2), name = "run", schedule = Schedule.Weekly(3)),
            ),
        )

        assertEquals(ScheduleLabelUi(R.string.insights_schedule_daily, null), state.habits.first().schedule)
        assertEquals(ScheduleLabelUi(R.string.insights_schedule_weekly, 3), state.habits.last().schedule)
    }

    @Test
    fun `the period and the breakdown come through untouched`() {
        val state = overview(period = Period.YEAR, breakdown = Breakdown.TAGS)

        assertEquals(Period.YEAR, state.period)
        assertEquals(Breakdown.TAGS, state.breakdown)
    }
}
