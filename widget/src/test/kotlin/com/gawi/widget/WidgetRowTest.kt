package com.gawi.widget

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
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Duration.Companion.seconds

/**
 * The shape of a habit row now that its name is a bitmap: a text-less checkbox,
 * one image beside it described by the name, and the toggle reachable from both.
 *
 * `WidgetTextColourTest` asserts the image's colour; this asserts everything
 * else the switch from `CheckBox(text = …)` could have lost — the name TalkBack
 * reads, the tap on the name, and the checked state — because each of those
 * survived the change only by being re-wired, and a re-wiring is exactly what a
 * decision-only test cannot see.
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

    /** The bare name stays on the checkbox; the row carries name and state, and the image is decorative. */
    @Test
    fun `the name is what the checkbox is described as`() = render {
        onAllNodes(hasContentDescriptionEqualTo("read")).assertCountEquals(1)
        onAllNodes(hasContentDescriptionEqualTo("walk")).assertCountEquals(1)
        onAllNodes(describedCheckBox("read")).assertCountEquals(1)
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
