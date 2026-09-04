package com.gawi.feature.insights

import com.gawi.core.data.model.ReadContext
import com.gawi.core.data.model.TagEffort
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.projection.HabitState
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import com.gawi.core.testing.habitState
import com.gawi.core.testing.thisMonth
import com.gawi.core.ui.streak.StreakUi
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

    private val context = ReadContext(today = FIXED_DATE, weekStart = DayOfWeek.MONDAY)

    /** Every parameter defaulted, so a test names only the field it is about. */
    @Suppress("LongParameterList")
    private fun overview(
        period: Period = Period.MONTH,
        breakdown: Breakdown = Breakdown.HABITS,
        habits: List<HabitState> = emptyList(),
        completions: Map<HabitId, Set<LocalDate>> = emptyMap(),
        tagEffort: List<TagEffort> = emptyList(),
        previousTagEffort: List<TagEffort> = emptyList(),
        back: Int = 0,
        today: LocalDate = FIXED_DATE,
    ) = overviewOf(
        period,
        back,
        breakdown,
        context.copy(today = today),
        PeriodReads(period.window(today, back), habits, completions, tagEffort, previousTagEffort),
    )

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
            habits = listOf(habitState(createdOn = FIXED_DATE.plusMonths(2))),
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

    // ---- the retrospective: label, best run, trend, focus ----

    @Test
    fun `the label names the calendar period the window is`() {
        assertEquals(PeriodLabelUi.Month(R.string.insights_month_august, 2026), overview().label)
        assertEquals(PeriodLabelUi.Quarter(3, 2026), overview(period = Period.QUARTER).label)
        assertEquals(PeriodLabelUi.Year(2025), overview(period = Period.YEAR, back = 1).label)
        assertEquals(false, overview().canStepLater)
        assertEquals(true, overview(back = 1).canStepLater)
    }

    @Test
    fun `a row carries its best run in the schedule's unit, and none when there was none`() {
        val read = habitState(id = habitId(1), name = "read")
        val run = habitState(id = habitId(2), name = "run", schedule = Schedule.Weekly(1))
        val idle = habitState(id = habitId(3), name = "idle")
        val state = overview(
            habits = listOf(read, run, idle),
            completions = mapOf(
                read.id to setOf(thisMonth(1), thisMonth(2), thisMonth(3), thisMonth(9), thisMonth(10)),
                // Mondays 3, 10 and 17 August: three consecutive hit weeks.
                run.id to setOf(thisMonth(3), thisMonth(10), thisMonth(17)),
            ),
        )

        assertEquals(listOf(StreakUi.Days(3), StreakUi.Weeks(3), null), state.habits.map { it.best })
    }

    @Test
    fun `the best run is measured from the habit's creation, like the rate`() {
        val born = habitState(createdOn = thisMonth(12))
        val state = overview(habits = listOf(born), completions = mapOf(born.id to (10..14).map { thisMonth(it) }.toSet()))

        assertEquals(StreakUi.Days(3), state.habits.single().best)
    }

    @Test
    fun `a month has no trend and a quarter has a point per begun month`() {
        assertTrue(overview().trend.isEmpty())

        val quarter = overview(
            period = Period.QUARTER,
            completions = mapOf(habitId(1) to setOf(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-02"), thisMonth(3))),
        )

        // July, August — September has not begun and is not a point.
        assertEquals(listOf(R.string.insights_month_july, R.string.insights_month_august), quarter.trend.map { it.monthName })
        assertEquals(listOf(2, 1), quarter.trend.map { it.activeDays })
        // July over its 31 days; August over the 18 it has had.
        assertEquals(2f / 31, quarter.trend[0].fill, 1e-6f)
        assertEquals(1f / 18, quarter.trend[1].fill, 1e-6f)
    }

    @Test
    fun `a quarter in its first month has no trend either, one point being no line`() {
        val state = overview(period = Period.QUARTER, today = LocalDate.parse("2026-07-15"))

        assertTrue(state.trend.isEmpty())
        assertTrue(overview(period = Period.YEAR, today = LocalDate.parse("2026-01-20")).trend.isEmpty())
    }

    @Test
    fun `the trend sums to the headline, future-dated completions included`() {
        val state = overview(
            period = Period.QUARTER,
            completions = mapOf(habitId(1) to setOf(LocalDate.parse("2026-07-01"), thisMonth(3), FIXED_DATE.plusDays(1))),
        )

        assertEquals(state.activeDays, state.trend.sumOf { it.activeDays })
    }

    @Test
    fun `a past year has twelve points`() {
        val state = overview(period = Period.YEAR, back = 1)

        assertEquals(12, state.trend.size)
        assertEquals(0, state.trend.sumOf { it.activeDays })
    }

    @Test
    fun `a complete period's focus is the top tagged total, before and after`() {
        val shifted = overview(
            back = 1,
            tagEffort = listOf(TagEffort("career", 9), TagEffort("health", 4)),
            previousTagEffort = listOf(TagEffort("health", 8), TagEffort("career", 1)),
        )
        assertEquals(FocusShiftUi.Shifted(from = "health", to = "career"), shifted.focus)

        val held = overview(back = 1, tagEffort = listOf(TagEffort("health", 4)), previousTagEffort = listOf(TagEffort("health", 8)))
        assertEquals(FocusShiftUi.Held("health"), held.focus)
    }

    @Test
    fun `the current period names its leader and claims no shift`() {
        val state = overview(
            tagEffort = listOf(TagEffort("career", 1)),
            previousTagEffort = listOf(TagEffort("health", 80)),
        )

        assertEquals(FocusShiftUi.SoFar("career"), state.focus)
        assertEquals(null, overview(tagEffort = listOf(TagEffort(null, 3)), previousTagEffort = listOf(TagEffort("health", 8))).focus)
    }

    @Test
    fun `stepping back stops at the oldest habit, and never starts without one`() {
        val old = habitState(id = habitId(1), createdOn = LocalDate.parse("2026-03-10"))
        val unknown = habitState(id = habitId(2), createdOn = null)

        assertEquals(false, overview().canStepEarlier)
        assertEquals(true, overview(habits = listOf(old)).canStepEarlier)
        // Q1 2026 starts before 10 March: nothing lies earlier.
        assertEquals(false, overview(period = Period.QUARTER, back = 2, habits = listOf(old)).canStepEarlier)
        assertEquals(true, overview(period = Period.QUARTER, back = 2, habits = listOf(old, unknown)).canStepEarlier)
    }

    @Test
    fun `a month that has not begun but holds a completion is a point, so the trend still sums`() {
        val state = overview(
            period = Period.QUARTER,
            completions = mapOf(habitId(1) to setOf(thisMonth(3), LocalDate.parse("2026-09-05"))),
        )

        assertEquals(
            listOf(R.string.insights_month_july, R.string.insights_month_august, R.string.insights_month_september),
            state.trend.map {
                it.monthName
            },
        )
        assertEquals(state.activeDays, state.trend.sumOf { it.activeDays })
        assertEquals(1f / 30, state.trend.last().fill, 1e-6f)
    }

    @Test
    fun `the current month's fill is capped at one`() {
        val state = overview(
            period = Period.QUARTER,
            today = LocalDate.parse("2026-08-02"),
            completions = mapOf(habitId(1) to (1..10).map { thisMonth(it) }.toSet()),
        )

        assertEquals(1f, state.trend.last().fill)
        assertEquals(10, state.trend.last().activeDays)
    }

    @Test
    fun `untagged effort is never a focus, and an untagged period says nothing`() {
        val onlyUntagged = overview(back = 1, tagEffort = listOf(TagEffort(null, 40)), previousTagEffort = listOf(TagEffort("health", 8)))
        assertEquals(null, onlyUntagged.focus)

        val outnumbered = overview(
            back = 1,
            tagEffort = listOf(TagEffort(null, 40), TagEffort("career", 2)),
            previousTagEffort = listOf(TagEffort(null, 50), TagEffort("career", 1)),
        )
        assertEquals(FocusShiftUi.Held("career"), outnumbered.focus)

        assertEquals(null, overview(back = 1, tagEffort = listOf(TagEffort("career", 2)), previousTagEffort = emptyList()).focus)
    }

    @Test
    fun `a tie resolves the way the bars sort`() {
        val state = overview(
            back = 1,
            tagEffort = listOf(TagEffort("mind", 5), TagEffort("career", 5)),
            previousTagEffort = listOf(TagEffort("mind", 5), TagEffort("career", 5)),
        )

        assertEquals(FocusShiftUi.Held("career"), state.focus)
        assertEquals("career", state.tags.first().name)
    }
}
