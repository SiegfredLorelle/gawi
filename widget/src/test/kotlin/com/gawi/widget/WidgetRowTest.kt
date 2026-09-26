package com.gawi.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.EmittableImage
import androidx.glance.action.ActionModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.findModifier
import androidx.glance.layout.EmittableRow
import androidx.glance.layout.HeightModifier
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.unit.Dimension
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import com.gawi.widget.testsupport.describedNode
import com.gawi.widget.testsupport.ink
import com.gawi.widget.testsupport.isDescribed
import com.gawi.widget.testsupport.mask
import com.gawi.widget.testsupport.tintedWith
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

/**
 * The shape of a habit row: a 48dp clickable row described as the name and its
 * state, and inside it a decorative mark and a decorative name, neither of them
 * a stop of its own.
 *
 * **One stop per row is the property under test**, and it is the thing the
 * Accessibility Scanner and a device both measured the other way
 * (docs/running.md §4). A `CheckBox` here would be a second stop at 32dp that no
 * height can grow, so the mark is drawn instead; asserting that *nothing inside
 * the row is described* is what would catch a control coming back. The rest —
 * the name TalkBack reads, the tap, the completion — survived the move from
 * `CheckBox(text = …)` only by being re-wired, and a re-wiring is what a
 * decision-only test cannot see. What the JVM sees is the emittable tree: that
 * every row *asks* for its description and its height. What a launcher lays out
 * is the device's half.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetRowTest {

    private val snapshot = todaySnapshot(
        habits = listOf(
            todayHabit(id = habitId(1), name = "read", completedToday = true),
            todayHabit(id = habitId(2), name = "walk"),
        ),
    )

    /** Two rows, and four images: a mark and a name each, every one of them silent. */
    @Test
    fun `every row is a decorative mark beside a decorative name`() = render {
        onAllNodes(row()).assertCountEquals(2)
        onAllNodes(image()).assertCountEquals(4)
        onAllNodes(describedImage()).assertCountEquals(0)
    }

    /** On the row, in words, because the row is the stop TalkBack lands on. */
    @Test
    fun `the row is described as the name and whether it is done`() = render {
        onAllNodes(hasContentDescriptionEqualTo("read, done")).assertCountEquals(1)
        onAllNodes(hasContentDescriptionEqualTo("walk, not done")).assertCountEquals(1)
        onAllNodes(describedRow("read, done")).assertCountEquals(1)
        onAllNodes(describedRow("walk, not done")).assertCountEquals(1)
    }

    /**
     * Nothing but the two rows is described, which is the finding stated as a
     * test: the second stop the Scanner found was the checkbox control, and a
     * third described node here is that defect coming back under another name.
     */
    @Test
    fun `the rows are the only things described`() = render {
        onAllNodes(describedNode()).assertCountEquals(2)
    }

    /** The 48dp floor, asked for on every row. Whether a launcher draws it so is the device's to say. */
    @Test
    fun `every row asks for the 48dp touch-target height`() = render {
        onAllNodes(row()).assertCountEquals(2)
        onAllNodes(rowOfHeight(48.dp)).assertCountEquals(2)
    }

    /**
     * The row's action names the habit, and it is the only one: with the control
     * gone there is no second target inside the row to wire, and no
     * `CompoundButton` that would flip on screen without writing anything.
     */
    @Test
    fun `the toggle is on the row with the habit's id, and nowhere else`() = render {
        for (n in 1..2) {
            val parameters = actionParametersOf(HABIT_ID to habitId(n).value)
            onAllNodes(hasRunCallbackClickAction<ToggleHabitAction>(parameters)).assertCountEquals(1)
        }
        onAllNodes(imageWithAnAction()).assertCountEquals(0)
    }

    /**
     * The mark is drawn in the widget's own palette rather than a host default,
     * and it says which state it is in by which of the two it carries.
     *
     * Readable without reflection: the mark is an `Image` and carries its tint in
     * the tree, where a Glance `CheckBox` exposes its colours only through an
     * `internal` accessor and can be asserted against only by rebuilding them
     * inside the composition. Matched by **identity**, because `glyphChecked`
     * and `bandWoven` are both built from `Primary` and so are *equal*.
     * `WidgetPaletteTest` holds the two colours to the contrast floor; this says
     * the widget hands them to the right rows.
     */
    @Test
    fun `the mark shows the completion in the widget palette`() = render {
        onAllNodes(tintedWith(WidgetPalette.glyphChecked)).assertCountEquals(1)
        onAllNodes(tintedWith(WidgetPalette.glyphUnchecked)).assertCountEquals(1)
    }

    /**
     * The state is in the shape as well as the colour: the done row's mark is the
     * filled one, so it carries more ink than the outstanding outline. Without
     * this, a mark drawn from the wrong flag keeps its tint and passes the test
     * above while done and outstanding differ by colour alone
     * (docs/ux/widget.md §8). NATIVE because a LEGACY canvas paints nothing and
     * both masks would weigh zero.
     */
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the done mark is the filled one`() = render {
        val done = onNode(tintedWith(WidgetPalette.glyphChecked)).mask()
        val outstanding = onNode(tintedWith(WidgetPalette.glyphUnchecked)).mask()
        assertTrue(done.ink() > outstanding.ink())
    }

    private fun render(block: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
            setContext(RuntimeEnvironment.getApplication())
            setAppWidgetSize(DpSize(250.dp, 110.dp))
            provideComposable { WidgetBody(WidgetContent.Ready(snapshot.toWidgetState())) }
            awaitIdle()
            block()
        }
}

/** Aligned with WidgetTextColourTest, and for the same reason: Robolectric's first case is slow under load. */
private val RENDER_TIMEOUT = 60.seconds

private fun image() = GlanceNodeMatcher<MappedNode>("is an image") { it.value.emittable is EmittableImage }

/**
 * An image carrying a click action of its own — the second target the mark must
 * not become. Asked of the image rather than counted off
 * `hasRunCallbackClickAction`, whose no-argument form matches only actions with
 * *empty* parameters and so matches none of these.
 */
private fun imageWithAnAction() = GlanceNodeMatcher<MappedNode>("is an image with an action") {
    it.value.emittable is EmittableImage && it.value.emittable.modifier.findModifier<ActionModifier>() != null
}

private fun describedImage() = GlanceNodeMatcher<MappedNode>("is a described image") {
    it.value.emittable is EmittableImage && it.value.emittable.isDescribed()
}

private fun row() = GlanceNodeMatcher<MappedNode>("is a row") { it.value.emittable is EmittableRow }

private fun describedRow(spoken: String) = GlanceNodeMatcher<MappedNode>("is a row described as $spoken") {
    it.value.emittable is EmittableRow && hasContentDescriptionEqualTo(spoken).matches(it)
}

/** `Dimension.Dp` is not a data class, so the dp is compared rather than the wrapper. */
private fun rowOfHeight(height: Dp) = GlanceNodeMatcher<MappedNode>("is a row $height tall") {
    val row = it.value.emittable as? EmittableRow ?: return@GlanceNodeMatcher false
    (row.modifier.findModifier<HeightModifier>()?.height as? Dimension.Dp)?.dp == height
}
