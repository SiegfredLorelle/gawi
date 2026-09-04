package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.BackgroundModifier
import androidx.glance.EmittableImage
import androidx.glance.TintColorFilterParams
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import com.gawi.core.domain.testing.habitId
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
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

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
 * **Since 2026-08-25 the text is pixels, and the colour is a tint.** Every string
 * is an [OutfitText] — an `EmittableImage` carrying `ColorFilter.tint(onSurface)`
 * — so the matcher reads the tint's `ColorProvider` where it read the style's
 * before. It deliberately does **not** match `EmittableWithText` any more: the
 * `CheckBox` beside each name is still one, with `text == ""`, and a matcher that
 * counted it would report two texts per row and measure a colour nothing draws.
 * The count guards below are what make an empty tree fail, so they count images.
 *
 * **The ground is the one that was actually drawn, since 2026-08-28.** Until
 * then the probe resolved it from a second, default `GlanceTheme { }`, and this
 * KDoc carried the consequence as a known limit: the moment the widget took a
 * palette of its own, the test would measure real ink against a background
 * nothing draws and stay green. The widget took one ([WidgetPalette]) — so the
 * limit is gone rather than realised. `BackgroundModifier.Color` does expose its
 * provider, which the old note had wrong, so `drawnOn` asserts on the
 * emitted tree that the drawn ground *is* that palette's surface and the ratios
 * are measured against the same object. The identity check is the load-bearing
 * half: without it the ground would be an assumption that happens to match.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetTextColourDarkTest : WidgetTextColourContract()

/** The mirror. See the contract's note on why one theme alone is not enough. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetTextColourLightTest : WidgetTextColourContract()

/**
 * How long a render may take before the harness gives up, and why it is stated
 * rather than defaulted.
 *
 * `runGlanceAppWidgetUnitTest` defaults to about **2 seconds**, while plain
 * `runTest` from kotlinx-coroutines-test defaults to **60**. That 30× gap made
 * this the only flaky test in the suite. Robolectric pays a large one-time cost
 * initialising the first case in a class, and here it lands *inside* the timed
 * block: measured at 3.6s with `:widget` alone, 10.6s under a full parallel
 * suite, and 32.1s on the run that failed. `org.gradle.parallel=true` means
 * modules compete for CPU, so the number is a property of the machine's load,
 * not of this test.
 *
 * Around thirty other classes run Robolectric inside plain `runTest`, several
 * taking 9–15s — `TodayScreenTest` peaks at 19.9s — and none can hit this,
 * because 60s absorbs the cost. So the value here is not tuned to the observed
 * 32s; it is **aligned with what every other coroutine test in this repo already
 * gets**. A guessed 30s would have failed the run that failed.
 *
 * Accepted cost: a genuinely hung render now takes a minute to report instead of
 * two seconds. That is the price of a gate that means something, and it is
 * already the price paid everywhere else here.
 */
private val RENDER_TIMEOUT = 60.seconds

/**
 * The assertions themselves, run once per theme by the two classes above.
 *
 * Each test also asserts the text it expects is present, because "nothing has
 * bad contrast" is trivially true of an empty tree — which is how a check that
 * verifies nothing comes to look like a check that passes.
 */
abstract class WidgetTextColourContract {

    @Test
    fun `the empty copy is legible on the widget background`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(WidgetContent.Ready(todaySnapshot().toWidgetState()))

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    @Test
    fun `the unavailable copy is legible on the widget background`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(WidgetContent.Unavailable)

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    /** The rows, which are `CheckBox`es and not `Text`s — the case that hides. */
    @Test
    fun `habit rows are legible on the widget background`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val snapshot = todaySnapshot(
            habits = listOf(todayHabit(id = habitId(1), name = "read"), todayHabit(id = habitId(2), name = "walk")),
        )

        val probe = renderWithProbe(WidgetContent.Ready(snapshot.toWidgetState()))

        onAllNodes(anyText()).assertCountEquals(2)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    /**
     * The large body's mood line, drawn beside Momo on the surface — the one
     * string the header adds, measured on the ground it is actually on. The
     * count is three: the line and two names.
     */
    @Test
    fun `the large body's mood line is legible on the widget background`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val snapshot = todaySnapshot(
            habits = listOf(todayHabit(id = habitId(1), name = "read", completedToday = true), todayHabit(id = habitId(2), name = "walk")),
        )

        val probe = renderWithProbe(WidgetContent.Ready(snapshot.toWidgetState()), DpSize(250.dp, 220.dp))

        onAllNodes(anyText()).assertCountEquals(3)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }
}

/**
 * What the widget is drawn against, plus the `Context` the matchers need to
 * resolve a `ColorProvider` — a day/night provider answers differently in each
 * theme, so the resolution has to happen against the running configuration.
 */
private fun GlanceAppWidgetUnitTest.renderWithProbe(content: WidgetContent, size: DpSize = DpSize(250.dp, 110.dp)): RenderProbe {
    val context = RuntimeEnvironment.getApplication()
    setContext(context)
    // SizeMode.Exact reads LocalSize; the harness has to supply one or the
    // composition has no width to give a name.
    setAppWidgetSize(size)
    provideComposable { WidgetBody(content) }
    awaitIdle()
    // Load-bearing, not defensive, and it replaces a weaker guard. Until
    // 2026-08-28 the ground was captured inside the composition and this line
    // asserted only that the capture was not `Color.Unspecified` — `Color(0)`,
    // which reads as pure black, so a composition that never ran would have left
    // every light-on-dark assertion passing at about 16:1 having measured
    // nothing. Resolving the ground from the palette makes that particular hole
    // impossible but opens another: the value would be an assumption about what
    // was drawn. So the tree is asked instead. Exactly one node carries a
    // background and it is the palette's surface object itself; nothing below
    // measures anything until that holds.
    onAllNodes(drawnOn(WidgetPalette.surface)).assertCountEquals(1)
    return RenderProbe(context, WidgetPalette.surface.getColor(context))
}
