package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import com.gawi.core.testing.broken
import com.gawi.core.testing.running
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import com.gawi.widget.testsupport.RenderProbe
import com.gawi.widget.testsupport.anyText
import com.gawi.widget.testsupport.drawnOn
import com.gawi.widget.testsupport.illegibleText
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * Everything the streak widget draws clears WCAG AA on the ground it draws it on,
 * in both schemes.
 *
 * **Why this widget needs its own contrast test rather than trusting
 * `WidgetPaletteTest`.** That test measures the palette; this measures the tree,
 * and the streak widget is the first surface here where *which* ink a string gets
 * is computed per row from a `StreakUi`. A palette can be entirely legible while
 * [tint] hands a row the wrong member of it — and the case that hides is
 * `tertiary`, which is the only role the Today widget never draws, so nothing
 * before this measured it against a real background.
 *
 * The theme split is the whole difference between the two subclasses: a single
 * run would pass on a dark-on-light default and say nothing about night mode.
 * Each test also asserts the strings it expects are present, because "nothing has
 * bad contrast" is trivially true of an empty tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
class StreakTextColourDarkTest : StreakTextColourContract()

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "notnight")
class StreakTextColourLightTest : StreakTextColourContract()

/** Aligned with the module's other render tests, for the reason `WidgetTextColourTest` gives. */
private val RENDER_TIMEOUT = 60.seconds

abstract class StreakTextColourContract {

    /**
     * The case this class exists for: a daily and a weekly run together, so
     * `primary` and `tertiary` are both under measurement, plus a break and an
     * empty history in `outline`. Four rows, four different inks.
     */
    @Test
    fun `every streak ink is legible, in every unit and both states`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(FULL, mixedRows())

        // Four names, four numerals, the header, and the date.
        onAllNodes(anyText()).assertCountEquals(10)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    /** The same rows at the size where the unit word is gone and only the ink carries it. */
    @Test
    fun `the compact form is legible too`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(COMPACT, mixedRows())

        onAllNodes(anyText()).assertCountEquals(9)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    /**
     * The caption ink, which nothing else in this module draws. §7.1 makes this
     * line mandatory, so it being the least contrasty thing on the widget would
     * be a defect in the one element the design is about.
     */
    @Test
    fun `the as-of line is legible`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val single = todaySnapshot(habits = listOf(todayHabit(name = "read", streak = running(3))))
        val probe = renderWithProbe(COMPACT, single.toStreakState())

        // A name, a numeral, and the date.
        onAllNodes(anyText()).assertCountEquals(3)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    @Test
    fun `the empty copy is legible`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(FULL, todaySnapshot().toStreakState())

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    @Test
    fun `the unavailable copy is legible`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(FULL, state = null)

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    private fun mixedRows(): StreakUiState = todaySnapshot(
        habits = listOf(
            todayHabit(id = habitId(1), name = "read", streak = running(12)),
            todayHabit(id = habitId(2), name = "gym", schedule = Schedule.Weekly(timesPerWeek = 3), streak = running(3)),
            todayHabit(id = habitId(3), name = "stretch", streak = broken(9)),
            todayHabit(id = habitId(4), name = "sketch", streak = StreakSnapshot.NONE),
        ),
    ).toStreakState()
}

/**
 * Render, then establish the ground from the emitted tree before measuring
 * anything against it.
 *
 * The `drawnOn` assertion is load-bearing rather than defensive, for the reason
 * its own KDoc gives: resolving the ground from the palette alone would make it an
 * assumption about what was drawn, and a composition that never ran would leave
 * every ratio below passing on nothing.
 *
 * A null [state] means [StreakContent.Unavailable]; that state has no
 * [StreakUiState] to build.
 */
private fun GlanceAppWidgetUnitTest.renderWithProbe(size: DpSize, state: StreakUiState?): RenderProbe {
    val context = RuntimeEnvironment.getApplication()
    setContext(context)
    setAppWidgetSize(size)
    provideComposable {
        StreakBody(if (state == null) StreakContent.Unavailable else StreakContent.Ready(state))
    }
    awaitIdle()
    onAllNodes(drawnOn(WidgetPalette.surface)).assertCountEquals(1)
    return RenderProbe(context, WidgetPalette.surface.getColor(context))
}

private val COMPACT = DpSize(180.dp, 110.dp)
private val FULL = DpSize(250.dp, 200.dp)
