package com.gawi.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.EmittableImage
import androidx.glance.action.ActionModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.CheckBoxColors
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.EmittableCheckBox
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
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import com.gawi.widget.testsupport.isDescribed
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
     * The row's action names the habit, and the glyph carries one of its own.
     *
     * *Which* action the glyph carries is not asserted here: Glance wraps
     * `onCheckedChange` in an `internal` `CompoundButtonAction`, and unwrapping
     * it needs reflection into the library. That the glyph is wired at all is
     * public, and it is the half that regresses silently — dropping
     * `onCheckedChange` leaves a glyph that draws and does nothing. The device
     * box in docs/running.md §4 confirms the tap writes the same thing as a tap
     * on the name.
     */
    @Test
    fun `the toggle is on the row with the habit's id, and the glyph is wired too`() = render {
        for (n in 1..2) {
            val parameters = actionParametersOf(HABIT_ID to habitId(n).value)
            onAllNodes(hasRunCallbackClickAction<ToggleHabitAction>(parameters)).assertCountEquals(1)
        }
        onAllNodes(checkBoxWithAnAction()).assertCountEquals(2)
    }

    /**
     * The glyph is drawn in the widget's own palette rather than the host's
     * default, which is the 2.91:1 defect docs/running.md §4 records against
     * API 29 and 30.
     *
     * The expected value is built inside the composition, because
     * `CheckboxDefaults.colors` is `@Composable`; `CheckBoxColorsImpl` overrides
     * equality, so the comparison is a real one. Deleting the `colors` argument
     * in `TodayWidget` leaves the theme default here and turns this red.
     * `WidgetPaletteTest` holds the two colours themselves to the contrast
     * floor; this is what says the widget hands them over.
     */
    @Test
    fun `the checkbox glyphs carry the widget palette`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        setContext(RuntimeEnvironment.getApplication())
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        var expected: CheckBoxColors? = null
        provideComposable {
            expected = CheckboxDefaults.colors(
                checkedColor = WidgetPalette.glyphChecked,
                uncheckedColor = WidgetPalette.glyphUnchecked,
            )
            WidgetBody(WidgetContent.Ready(snapshot.toWidgetState()))
        }
        awaitIdle()
        onAllNodes(checkBoxColoured(checkNotNull(expected))).assertCountEquals(2)
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

/** A checkbox carrying an action of its own, whatever Glance wrapped it in. */
private fun checkBoxWithAnAction() = GlanceNodeMatcher<MappedNode>("is a checkbox with an action") {
    val checkBox = it.value.emittable as? EmittableCheckBox ?: return@GlanceNodeMatcher false
    checkBox.modifier.findModifier<ActionModifier>() != null
}

/** A checkbox whose colours are [expected], compared through `CheckBoxColorsImpl`'s own equality. */
private fun checkBoxColoured(expected: CheckBoxColors) = GlanceNodeMatcher<MappedNode>("is a checkbox coloured $expected") {
    (it.value.emittable as? EmittableCheckBox)?.colors == expected
}

private fun checkBoxWithText() = GlanceNodeMatcher<MappedNode>("is a checkbox carrying text") {
    (it.value.emittable as? EmittableCheckBox)?.text?.isNotEmpty() == true
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
