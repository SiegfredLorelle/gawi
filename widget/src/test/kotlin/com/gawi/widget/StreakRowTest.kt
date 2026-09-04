package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import com.gawi.core.testing.running
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import com.gawi.widget.testsupport.anyText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.seconds

/**
 * The shape of the streak widget's tree: how many strings it draws, what TalkBack
 * hears, and that the "as of" line is there at every size that draws a number.
 *
 * `StreakTextColourTest` asserts the colours; this asserts the wiring, which is
 * the half a decision-only test cannot see. Every string here is an `Image` — the
 * widget rasterises Outfit — so counting text means counting tinted images.
 */
@RunWith(RobolectricTestRunner::class)
class StreakRowTest {

    private val snapshot = todaySnapshot(
        habits = listOf(
            todayHabit(id = habitId(1), name = "read", streak = running(12)),
            todayHabit(id = habitId(2), name = "gym", schedule = Schedule.Weekly(timesPerWeek = 3), streak = running(3)),
        ),
    )

    /** Two rows, each a name and a numeral, plus the "as of" line. No header at this size. */
    @Test
    fun `the compact widget draws two strings per row and the date`() = render(COMPACT) {
        onAllNodes(anyText()).assertCountEquals(5)
    }

    /** The same, plus the header the full layout adds. */
    @Test
    fun `the full widget adds a header`() = render(FULL) {
        onAllNodes(anyText()).assertCountEquals(6)
    }

    /**
     * §7.1's requirement, asserted at the size where it is hardest to keep: the
     * line is outside the LazyColumn, so it survives however many rows there are.
     */
    @Test
    fun `the date is drawn at both sizes`() {
        val expected = "as of " + formatAsOf(FIXED_DATE, RuntimeEnvironment.getApplication().resources.configuration.locales[0])
        render(COMPACT) { onAllNodes(hasContentDescription(expected)).assertCountEquals(1) }
        render(FULL) { onAllNodes(hasContentDescription(expected)).assertCountEquals(1) }
    }

    /** Even with more rows than fit, so the pinned footer is what is being checked. */
    @Test
    fun `the date survives more rows than the widget can show`() {
        val many = todaySnapshot(habits = (1..12).map { todayHabit(id = habitId(it), name = "habit $it", streak = running(it)) })
        val expected = "as of " + formatAsOf(FIXED_DATE, RuntimeEnvironment.getApplication().resources.configuration.locales[0])
        render(COMPACT, many.toStreakState()) { onAllNodes(hasContentDescription(expected)).assertCountEquals(1) }
    }

    /**
     * One announcement per row, carrying the name and the streak in words — and
     * the full wording even here, where the widget is drawing `3w`.
     */
    @Test
    fun `each row is announced once, with its unit spelled out`() = render(COMPACT) {
        onAllNodes(hasContentDescription("read, 12 days")).assertCountEquals(1)
        onAllNodes(hasContentDescription("gym, 3 weeks")).assertCountEquals(1)
    }

    /** The copy states draw one string and no date. */
    @Test
    fun `an empty log draws one line and no date`() = render(FULL, todaySnapshot().toStreakState()) {
        onAllNodes(anyText()).assertCountEquals(1)
    }

    @Test
    fun `a failed read draws one line and no date`() = runGlanceAppWidgetUnitTest(timeout = RENDER_TIMEOUT) {
        setContext(RuntimeEnvironment.getApplication())
        setAppWidgetSize(FULL)
        provideComposable { StreakBody(StreakContent.Unavailable) }
        awaitIdle()
        onAllNodes(anyText()).assertCountEquals(1)
    }

    @Test
    fun `nothing is drawn before the first read`() = runGlanceAppWidgetUnitTest(timeout = RENDER_TIMEOUT) {
        setContext(RuntimeEnvironment.getApplication())
        setAppWidgetSize(FULL)
        provideComposable { StreakBody(StreakContent.Loading) }
        awaitIdle()
        onAllNodes(anyText()).assertCountEquals(0)
    }

    private fun render(size: DpSize, state: StreakUiState = snapshot.toStreakState(), block: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest(timeout = RENDER_TIMEOUT) {
            setContext(RuntimeEnvironment.getApplication())
            // SizeMode.Exact reads LocalSize, and here it also picks the layout.
            setAppWidgetSize(size)
            provideComposable { StreakBody(StreakContent.Ready(state)) }
            awaitIdle()
            block()
        }
}

/** Aligned with the module's other render tests: Robolectric's first case is slow under load. */
private val RENDER_TIMEOUT = 60.seconds

private val COMPACT = DpSize(180.dp, 110.dp)
private val FULL = DpSize(250.dp, 200.dp)
