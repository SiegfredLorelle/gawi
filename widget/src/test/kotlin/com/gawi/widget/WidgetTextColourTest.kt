package com.gawi.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.BackgroundModifier
import androidx.glance.EmittableImage
import androidx.glance.TintColorFilterParams
import androidx.glance.appwidget.CheckBoxColors
import androidx.glance.appwidget.EmittableCheckBox
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import com.gawi.widget.testsupport.MIN_CONTRAST
import com.gawi.widget.testsupport.contrastRatio
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
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
 * then [Probe] resolved it from a second, default `GlanceTheme { }`, and this
 * KDoc carried the consequence as a known limit: the moment the widget took a
 * palette of its own, the test would measure real ink against a background
 * nothing draws and stay green. The widget took one ([WidgetPalette]) — so the
 * limit is gone rather than realised. `BackgroundModifier.Color` does expose its
 * provider, which the old note had wrong, so [paletteBackground] asserts on the
 * emitted tree that the drawn ground *is* that palette's surface and the ratios
 * are measured against the same object. The identity check is the load-bearing
 * half: without it the ground would be an assumption that happens to match.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
class WidgetTextColourDarkTest : WidgetTextColourContract()

/** The mirror. See the contract's note on why one theme alone is not enough. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "notnight")
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
        onAllNodes(illegibleText(probe)).assertCountEquals(0)
    }

    @Test
    fun `the unavailable copy is legible on the widget background`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(WidgetContent.Unavailable)

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe)).assertCountEquals(0)
    }

    /** The rows, which are `CheckBox`es and not `Text`s — the case that hides. */
    @Test
    fun `habit rows are legible on the widget background`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val snapshot = todaySnapshot(
            habits = listOf(todayHabit(id = habitId(1), name = "read"), todayHabit(id = habitId(2), name = "walk")),
        )

        val probe = renderWithProbe(WidgetContent.Ready(snapshot.toWidgetState()))

        onAllNodes(anyText()).assertCountEquals(2)
        onAllNodes(illegibleText(probe)).assertCountEquals(0)
    }

    /**
     * The checkbox glyph, in both of its states — the other half of the contrast
     * failure measured on API 29 and 30, and until the palette pinned it the one
     * colour on this surface that had no test at all because the app did not
     * choose it.
     */
    @Test
    fun `the checkbox glyphs are legible on the widget background`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val snapshot = todaySnapshot(
            habits = listOf(
                todayHabit(id = habitId(1), name = "read", completedToday = true),
                todayHabit(id = habitId(2), name = "walk", completedToday = false),
            ),
        )

        val probe = renderWithProbe(WidgetContent.Ready(snapshot.toWidgetState()))

        onAllNodes(anyGlyph()).assertCountEquals(2)
        onAllNodes(illegibleGlyph(probe)).assertCountEquals(0)
    }
}

/**
 * What the widget is drawn against, plus the `Context` the matchers need to
 * resolve a `ColorProvider` — a day/night provider answers differently in each
 * theme, so the resolution has to happen against the running configuration.
 */
private class Probe {
    lateinit var context: android.content.Context
    var background: Color = Color.Unspecified
}

private fun GlanceAppWidgetUnitTest.renderWithProbe(content: WidgetContent): Probe {
    val probe = Probe()
    probe.context = RuntimeEnvironment.getApplication()
    setContext(probe.context)
    // SizeMode.Exact reads LocalSize; the harness has to supply one or the
    // composition has no width to give a name.
    setAppWidgetSize(DpSize(250.dp, 110.dp))
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
    onAllNodes(paletteBackground()).assertCountEquals(1)
    probe.background = WidgetPalette.surface.getColor(probe.context)
    return probe
}

/**
 * The node whose background is [WidgetPalette.surface] — matched by identity on
 * the provider, so it cannot be satisfied by a different colour that happens to
 * resolve the same way in the theme this subclass runs in.
 */
private fun paletteBackground() = GlanceNodeMatcher<MappedNode>("is drawn on WidgetPalette.surface") { node ->
    node.value.emittable.modifier.foldIn<BackgroundModifier.Color?>(null) { found, element ->
        found ?: element as? BackgroundModifier.Color
    }?.colorProvider === WidgetPalette.surface
}

private fun anyText() = GlanceNodeMatcher<MappedNode>("draws text") { node ->
    node.value.emittable.tint() != null
}

/**
 * The tint an [OutfitText] carries, or null for anything that is not one — which
 * includes Momo's still frame, untinted by design because she carries her own
 * palette ([MomoBitmap]). `WidgetMomoTest` counts her the other way round.
 */
private fun Any.tint() = (this as? EmittableImage)?.colorFilterParams as? TintColorFilterParams

private fun illegibleText(probe: Probe) =
    GlanceNodeMatcher<MappedNode>("draws text below $MIN_CONTRAST:1 against the widget background") { node ->
        val tint = node.value.emittable.tint()
        if (tint == null) {
            false
        } else {
            val colour = tint.colorProvider.getColor(probe.context)
            contrastRatio(colour, probe.background) < MIN_CONTRAST
        }
    }

private fun anyGlyph() = GlanceNodeMatcher<MappedNode>("draws a checkbox glyph") { node ->
    node.value.emittable is EmittableCheckBox
}

/** A checkbox whose glyph is below the floor in either state, against the ground it is drawn on. */
private fun illegibleGlyph(probe: Probe) =
    GlanceNodeMatcher<MappedNode>("draws a checkbox glyph below $MIN_CONTRAST:1 against the widget background") { node ->
        val colours = (node.value.emittable as? EmittableCheckBox)?.colors
        if (colours == null) {
            false
        } else {
            listOf(true, false).any { checked ->
                contrastRatio(colours.glyphColour(probe.context, checked), probe.background) < MIN_CONTRAST
            }
        }
    }

/**
 * The glyph's resolved colour, reached through the one accessor Glance leaves
 * `internal`.
 *
 * **Why reflection, and why it is worth the hop.** Until 2026-08-28 this colour
 * was documented as unassertable, and while the app did not choose it that was
 * the end of it. `CheckBoxColors` exposes its provider only as
 * `getCheckBox$glance_appwidget_release`, returning `CheckableColorProvider` —
 * a public interface with no members — so neither hop can be made in Kotlin
 * source. But the object behind it does have a **public**
 * `getColor(context, isNightMode, isChecked)`, so what is missing is reachability,
 * not API. `WidgetRowTest` already reflects on the `internal`
 * `CompoundButtonAction` for the same reason, so this is the module's existing
 * bargain rather than a new one.
 *
 * Note the argument order: night comes before checked, and getting it the wrong
 * way round would still compile and still pass, measuring the wrong state.
 *
 * What this does not replace: a JVM test cannot exercise a real host's
 * translation, which is where the API 29/30 defect lived. docs/running.md §4
 * keeps its by-hand toggle.
 */
private fun CheckBoxColors.glyphColour(context: Context, checked: Boolean): Color {
    val checkable = checkNotNull(javaClass.getMethod("getCheckBox\$glance_appwidget_release").invoke(this)) {
        "the checkbox exposed no colour provider"
    }
    val getColor = checkable.javaClass.methods.first { it.name.startsWith("getColor-") }
    val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    return Color((getColor.invoke(checkable, context, night, checked) as Long).toULong())
}
