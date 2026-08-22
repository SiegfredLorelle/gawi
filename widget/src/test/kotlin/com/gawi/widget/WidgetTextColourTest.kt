package com.gawi.widget

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.glance.EmittableWithText
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Every piece of text the widget draws is legible against the background it is
 * drawn on, asserted as a WCAG contrast ratio in **both** themes.
 *
 * The only test in this module that renders rather than deciding.
 * `WidgetBodyTest` asserts *which* body is chosen; nothing asserted how it
 * looked, and a real defect shipped through the gap: Glance's default text
 * colour is not theme-aware while the container's background is, so on a
 * dark-themed device the widget drew near-black text on a near-black surface —
 * a ratio of 1.59 against WCAG's 4.5 floor. Found by hand on a Nothing A059
 * (Android 16) on 2026-08-22, after a phase of green builds.
 *
 * **Contrast rather than "names a colour", because the weaker property is
 * hollow.** The first version asserted `style?.color != null`, and a mutation
 * check exposed it: Glance fills in a default `TextStyle`, so the plain-`Text`
 * branch passed with the fix removed and only the `CheckBox` branch failed. The
 * colour was never absent, it was *wrong*, and only a test that knows what the
 * text sits on can tell those apart.
 *
 * **Both themes, because either one alone is a trapdoor.** Night-only was the
 * first version, and it would have let a dark-mode-only literal through —
 * `Color.White`, say — shipping unreadable light-mode text, the mirror image of
 * the bug being fixed. Light-only would have missed the original defect
 * entirely, a dark default on a light background being perfectly legible. The
 * subclasses below are the whole difference.
 *
 * [EmittableWithText] is the supertype of both `EmittableText` and
 * `EmittableCheckBox`, so one matcher covers the copy and the habit rows. That
 * matters: the rows are the case a `Text`-only matcher skips.
 *
 * **Known limit, stated rather than hidden.** [Probe] resolves the background
 * from a second `GlanceTheme { }`, not from the one inside [WidgetBody], because
 * `BackgroundModifier` exposes no colour to read back off the emitted tree. So
 * if `WidgetBody` ever takes an explicit palette — `GlanceTheme(colors = …)` —
 * this test would compare against the *default* background and stop measuring
 * what is drawn. It would not silently pass empty, which is the failure that
 * matters and which [Probe.resolved] rules out, but it would need updating.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
class WidgetTextColourDarkTest : WidgetTextColourContract()

/** The mirror. See the contract's note on why one theme alone is not enough. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "notnight")
class WidgetTextColourLightTest : WidgetTextColourContract()

/**
 * The assertions themselves, run once per theme by the two classes above.
 *
 * Each test also asserts the text it expects is present, because "nothing has
 * bad contrast" is trivially true of an empty tree — which is how a check that
 * verifies nothing comes to look like a check that passes.
 */
abstract class WidgetTextColourContract {

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
 * What the widget is drawn against. Captured during composition because
 * `GlanceTheme.colors` exists only there, and held so the matchers can resolve a
 * `ColorProvider` afterwards.
 */
private class Probe {
    lateinit var context: android.content.Context
    var background: Color = Color.Unspecified

    /**
     * That the capture actually happened.
     *
     * Load-bearing, not defensive. `Color.Unspecified` is `Color(0)`, whose
     * channels are all zero, so [contrastRatio] reads it as **pure black** and
     * every light-on-dark assertion below would pass at about 16:1 having
     * measured nothing at all. A composition that never ran the probe lambda
     * would leave three green tests proving only that the default is black.
     */
    fun resolved(): Probe = apply {
        assertNotEquals("the probe never resolved a background", Color.Unspecified, background)
    }
}

private fun GlanceAppWidgetUnitTest.renderWithProbe(content: WidgetContent): Probe {
    val probe = Probe()
    probe.context = RuntimeEnvironment.getApplication()
    setContext(probe.context)
    provideComposable {
        GlanceTheme {
            val background = GlanceTheme.colors.widgetBackground.getColor(probe.context)
            SideEffect { probe.background = background }
        }
        WidgetBody(content)
    }
    awaitIdle()
    return probe.resolved()
}

private fun anyText() = GlanceNodeMatcher<MappedNode>("draws text") { node ->
    node.value.emittable is EmittableWithText
}

/** WCAG AA for normal text. Float, to match what [contrastRatio] returns. */
private const val MIN_CONTRAST = 4.5f

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

/**
 * WCAG 2.1 contrast, over Compose's own relative luminance.
 *
 * [luminance] *is* the WCAG relative-luminance formula — the sRGB linearisation
 * and the 0.2126/0.7152/0.0722 weighting — so hand-rolling it here, in the one
 * test whose whole job is getting this arithmetic right, only added a second
 * place for it to be wrong. `core/ui/theme/HabitColor.kt` was already calling
 * the library version.
 *
 * It reads RGB and ignores alpha, which is fine for both operands here: each is
 * a resolved colour off a `ColorProvider`, not a translucent tint. A translucent
 * one would have to be composited first, the way `glyphColorOn` does it.
 */
private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + WCAG_OFFSET) / (minOf(la, lb) + WCAG_OFFSET)
}

/** WCAG's constant, which keeps the ratio finite when one side is pure black. */
private const val WCAG_OFFSET = 0.05f
