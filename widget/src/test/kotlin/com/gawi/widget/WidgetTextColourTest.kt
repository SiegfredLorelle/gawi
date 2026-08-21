package com.gawi.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.EmittableWithText
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Every piece of text the widget draws is legible against the background it is
 * drawn on — asserted as a contrast ratio, in dark mode, because that is the
 * defect this exists for.
 *
 * The one test in this module that renders rather than deciding.
 * `WidgetBodyTest` asserts *which* body is chosen; nothing asserted how it
 * looked, and a real defect shipped through the gap: Glance's default text
 * colour is not theme-aware while the container's background is, so on a
 * dark-themed device the widget drew near-black text on `#303030` — **1.59:1**,
 * against WCAG's 4.5:1 floor. Found by hand on a Nothing A059 (Android 16) on
 * 2026-08-22, after a whole phase of green builds.
 *
 * **Contrast rather than "names a colour", because the weaker property is
 * hollow.** The first version of this test asserted `style?.color != null` and
 * a mutation check exposed it: Glance fills in a default `TextStyle`, so the
 * plain-`Text` branch passed with the fix removed. Only the `CheckBox` branch
 * failed. The colour was never absent — it was *wrong*, and only a test that
 * knows what the text sits on can tell the difference.
 *
 * `@Config(qualifiers = "night")` is load-bearing. In light mode the broken
 * code looks fine — a dark default on a light background is perfectly legible —
 * which is exactly why this went unnoticed.
 *
 * [EmittableWithText] is the supertype of both `EmittableText` and
 * `EmittableCheckBox`, so one matcher covers the copy and the habit rows. That
 * matters: the rows are the case a `Text`-only matcher skips.
 *
 * `setContext` is not optional — the copy branch resolves its string through
 * `LocalContext.current`, and Glance's unit-test environment supplies no default
 * one. Resolving a `ColorProvider` needs the same context.
 *
 * **Each test also asserts the text it expects is actually there**, because
 * "nothing has bad contrast" is trivially true of an empty tree, which is how a
 * check that verifies nothing comes to look like a check that passes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
class WidgetTextColourTest {

    @Test
    fun `the empty copy is legible on the widget background`() = runGlanceAppWidgetUnitTest {
        val probe = renderWithProbe(WidgetContent.Ready(todaySnapshot().toWidgetState()))

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe)).assertCountEquals(0)
    }

    @Test
    fun `the unavailable copy is legible on the widget background`() = runGlanceAppWidgetUnitTest {
        val probe = renderWithProbe(WidgetContent.Unavailable)

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe)).assertCountEquals(0)
    }

    /** The rows, which are `CheckBox`es and not `Text`s — the case that hides. */
    @Test
    fun `habit rows are legible on the widget background`() = runGlanceAppWidgetUnitTest {
        val snapshot = todaySnapshot(
            habits = listOf(todayHabit(id = habitId(1), name = "read"), todayHabit(id = habitId(2), name = "walk")),
        )

        val probe = renderWithProbe(WidgetContent.Ready(snapshot.toWidgetState()))

        onAllNodes(anyText()).assertCountEquals(2)
        onAllNodes(illegibleText(probe)).assertCountEquals(0)
    }
}

/**
 * What the widget is drawn against, read from the same theme the composable
 * reads. Captured during composition because `GlanceTheme.colors` exists only
 * there, and returned so the matchers can resolve a `ColorProvider` afterwards.
 */
private class Probe {
    lateinit var context: android.content.Context
    var background: Color = Color.Unspecified
}

private fun androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.renderWithProbe(content: WidgetContent): Probe {
    val probe = Probe()
    probe.context = RuntimeEnvironment.getApplication()
    setContext(probe.context)
    provideComposable {
        GlanceTheme { probe.background = GlanceTheme.colors.widgetBackground.getColor(probe.context) }
        WidgetBody(content)
    }
    awaitIdle()
    return probe
}

private fun anyText() = GlanceNodeMatcher<MappedNode>("draws text") { node ->
    node.value.emittable is EmittableWithText
}

/** WCAG AA for normal text. */
private const val MIN_CONTRAST = 4.5

private fun illegibleText(probe: Probe) =
    GlanceNodeMatcher<MappedNode>("draws text below $MIN_CONTRAST:1 against the widget background") { node ->
        val emittable = node.value.emittable
        if (emittable !is EmittableWithText) {
            false
        } else {
            val colour = emittable.style?.color?.getColor(probe.context)
            colour == null || contrastRatio(colour, probe.background) < MIN_CONTRAST
        }
    }

/** WCAG 2.1 relative-luminance contrast, the same arithmetic used on the device. */
private fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun relativeLuminance(colour: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(colour.red) + 0.7152 * channel(colour.green) + 0.0722 * channel(colour.blue)
}
