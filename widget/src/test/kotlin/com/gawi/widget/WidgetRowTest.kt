package com.gawi.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.EmittableImage
import androidx.glance.action.ActionModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.EmittableCheckBox
import androidx.glance.appwidget.action.RunCallbackAction
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.isChecked
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.findModifier
import androidx.glance.layout.EmittableRow
import androidx.glance.layout.HeightModifier
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.unit.Dimension
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.isDescribed
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.seconds

/**
 * The shape of a habit row now that its name is a bitmap: a 48dp clickable row
 * described as the name and its state, a text-less checkbox keeping the bare
 * name, one decorative image, and the toggle reachable from row and glyph.
 *
 * `WidgetTextColourTest` asserts the image's colour; this asserts everything
 * else the switch from `CheckBox(text = …)` could have lost — the name TalkBack
 * reads, the tap on the name, and the checked state — because each of those
 * survived the change only by being re-wired, and a re-wiring is exactly what a
 * decision-only test cannot see. The row is what is described because the row
 * is the stop TalkBack lands on: Glance describes a `CheckBox`'s wrapper and not
 * the control, and a device heard the name at the row and nothing at the box
 * (`TodayWidget.kt`, `HabitRows`). What the JVM sees is the emittable tree —
 * that every row *asks* for its description and its height; what a launcher
 * lays out, and what the 32dp control inside still measures, is the device's
 * half (docs/running.md §4).
 */
@RunWith(RobolectricTestRunner::class)
class WidgetRowTest {

    private val snapshot = todaySnapshot(
        habits = listOf(
            todayHabit(id = habitId(1), name = "read", completedToday = true),
            todayHabit(id = habitId(2), name = "walk"),
        ),
    )

    @Test
    fun `every row is a text-less checkbox and one Outfit image`() = render {
        onAllNodes(checkBox()).assertCountEquals(2)
        onAllNodes(checkBoxWithText()).assertCountEquals(0)
        onAllNodes(image()).assertCountEquals(2)
    }

    /** On the row, in words, because the row is the stop TalkBack lands on. */
    @Test
    fun `the row is described as the name and whether it is done`() = render {
        onAllNodes(hasContentDescriptionEqualTo("read, done")).assertCountEquals(1)
        onAllNodes(hasContentDescriptionEqualTo("walk, not done")).assertCountEquals(1)
        onAllNodes(describedRow("read, done")).assertCountEquals(1)
        onAllNodes(describedRow("walk, not done")).assertCountEquals(1)
    }

    /** Kept on the box as the bare name, so a host that attaches it to the control has a label; the image stays decorative. */
    @Test
    fun `the checkbox keeps the bare name and the image carries nothing`() = render {
        onAllNodes(describedCheckBox("read")).assertCountEquals(1)
        onAllNodes(describedCheckBox("walk")).assertCountEquals(1)
        onAllNodes(hasContentDescriptionEqualTo("read")).assertCountEquals(1)
        onAllNodes(describedImage()).assertCountEquals(0)
    }

    /** The 48dp floor, asked for on every row. Whether a launcher draws it so is the device's to say. */
    @Test
    fun `every row asks for the 48dp touch-target height`() = render {
        onAllNodes(row()).assertCountEquals(2)
        onAllNodes(rowOfHeight(48.dp)).assertCountEquals(2)
    }

    /**
     * The stock matcher sees the Row's action and not the checkbox's: Glance wraps
     * a `CheckBox`'s `onCheckedChange` in a `CompoundButtonAction`, so that one is
     * read by unwrapping. Both have to name the habit, or a tap on the name and a
     * tap on the glyph would write different things.
     */
    @Test
    fun `the toggle is on the row and on its glyph, with the habit's id`() = render {
        for (n in 1..2) {
            val parameters = actionParametersOf(HABIT_ID to habitId(n).value)
            onAllNodes(hasRunCallbackClickAction<ToggleHabitAction>(parameters)).assertCountEquals(1)
            onAllNodes(checkBoxToggling(parameters)).assertCountEquals(1)
        }
    }

    @Test
    fun `the glyph shows the completion`() = render {
        onAllNodes(isChecked()).assertCountEquals(1)
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

private fun checkBox() = GlanceNodeMatcher<MappedNode>("is a checkbox") { it.value.emittable is EmittableCheckBox }

private fun checkBoxWithText() = GlanceNodeMatcher<MappedNode>("is a checkbox carrying text") {
    (it.value.emittable as? EmittableCheckBox)?.text?.isNotEmpty() == true
}

/**
 * Glance wraps `onCheckedChange` in `CompoundButtonAction`, which is `internal`
 * to Glance and so unreachable by name from here; its JVM getter is public, and
 * reflection on one method name is the smallest hole to read the inner action
 * through. If Glance renames it this fails loudly, which is the right failure.
 */
private fun checkBoxToggling(parameters: ActionParameters) = GlanceNodeMatcher<MappedNode>("is a checkbox running ToggleHabitAction") {
    val checkBox = it.value.emittable as? EmittableCheckBox ?: return@GlanceNodeMatcher false
    val wrapped = checkBox.modifier.findModifier<ActionModifier>()?.action ?: return@GlanceNodeMatcher false
    val inner = wrapped.javaClass.getMethod("getInnerAction").invoke(wrapped) as? RunCallbackAction
    inner?.callbackClass == ToggleHabitAction::class.java && inner.parameters == parameters
}

private fun describedCheckBox(name: String) = GlanceNodeMatcher<MappedNode>("is a checkbox described as $name") {
    it.value.emittable is EmittableCheckBox && hasContentDescription(name).matches(it)
}

private fun image() = GlanceNodeMatcher<MappedNode>("is an image") { it.value.emittable is EmittableImage }

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
