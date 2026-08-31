package com.gawi.widget

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.EmittableCheckBox
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeAssertion
import androidx.glance.testing.GlanceNodeAssertionCollection
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import com.gawi.widget.testsupport.anyText
import com.gawi.widget.testsupport.bitmap
import com.gawi.widget.testsupport.describedText
import com.gawi.widget.testsupport.drawnOn
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.isDescribed
import com.gawi.widget.testsupport.silentUntintedImage
import com.gawi.widget.testsupport.tintedWith
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import com.gawi.widget.testsupport.untintedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

/**
 * Momo's still frame is in the widget's tree when the host gave it room, and
 * only then.
 *
 * `WidgetBodyTest` pins the gate as a value; this checks the value is wired —
 * that a body with a mood puts an image in the tree and one without does not.
 * The one untinted image is hers: every text is a tinted [OutfitText], which is
 * what lets `WidgetTextColourTest` count text without seeing her.
 *
 * `GraphicsMode.NATIVE` although nothing here reads a pixel: composing the body
 * *renders* the bitmap, and under Robolectric's LEGACY graphics a Compose
 * `ImageBitmap` has no Android bitmap behind it — `asAndroidBitmap()` hands
 * Glance a null and the composition dies in `setHasAlpha`. Measured; the two
 * tall cases failed that way before this line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetMomoTest {

    private val rows = WidgetContent.Ready(
        todaySnapshot(
            habits = listOf(todayHabit(id = habitId(1), name = "read"), todayHabit(id = habitId(2), name = "walk")),
        ).toWidgetState(),
    )

    @Test
    fun `one cell tall, the rows stand alone`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(rows, DpSize(250.dp, 110.dp))

        onAllNodes(untintedImage()).assertCountEquals(0)
    }

    @Test
    fun `two cells tall and narrow, Momo sits above the rows and says how she is`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(rows, DpSize(180.dp, 220.dp))

        onAllNodes(untintedImage()).assertCountEquals(1)
        onNode(
            untintedImage(),
        ).assertHasContentDescriptionEqualTo(RuntimeEnvironment.getApplication().getString(R.string.widget_mood_content))
        // The rows are still there beneath her — the tall layout is otherwise
        // rendered by no other test, which all compose at one cell.
        onAllNodes(checkBox()).assertCountEquals(2)
    }

    /** The empty state's face is decorative: the copy already reads once. */
    @Test
    fun `two cells tall with no habits, Momo is there and silent`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(WidgetContent.Ready(todaySnapshot().toWidgetState()), DpSize(250.dp, 220.dp))

        onAllNodes(untintedImage()).assertCountEquals(1)
        onAllNodes(silentUntintedImage()).assertCountEquals(1)
    }

    /**
     * The large body (docs/ux/widget.md §7): Momo on her ground, silent, beside
     * the mood line, which is the one thing here that is read — the rows'
     * checkboxes still announce themselves beneath.
     */
    @Test
    fun `two cells tall and wide, Momo sits on her ground beside the mood line and the mood line is what is read`() =
        runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
            render(rows, DpSize(250.dp, 220.dp))

            onAllNodes(untintedImage()).assertCountEquals(1)
            onAllNodes(silentUntintedImage()).assertCountEquals(1)
            onAllNodes(drawnOn(WidgetPalette.momoGround)).assertCountEquals(1)
            // The mood line plus two names: the header adds exactly one string.
            onAllNodes(anyText()).assertCountEquals(3)
            onNode(
                describedText(),
            ).assertHasContentDescriptionEqualTo(RuntimeEnvironment.getApplication().getString(R.string.widget_mood_content))
            onAllNodes(checkBox()).assertCountEquals(2)
        }

    /**
     * The band is the rows' flags, one segment each, in the rows' order — which
     * this asserts left-to-right, the direction the host reads here; the case
     * below is the same claim under an RTL one. Two
     * masks in the tree, the woven one tinted [WidgetPalette.bandWoven] and the
     * outstanding one [WidgetPalette.bandOutstanding], matched by identity so a
     * swap of the two turns this red — and each mask's ink lands where its
     * habits' segments are, read off the pixels, so the tint cannot be on the
     * wrong mask either. Nothing in the band is described. `BandBitmapTest` has
     * the geometry; this is the wiring.
     */
    @Test
    fun `the band has one segment per habit, in order, coloured by whether today is done`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val snapshot = todaySnapshot(
            habits = listOf(
                todayHabit(id = habitId(1), name = "read", completedToday = true),
                todayHabit(id = habitId(2), name = "walk", completedToday = false),
                todayHabit(id = habitId(3), name = "swim", completedToday = true),
            ),
        )
        render(WidgetContent.Ready(snapshot.toWidgetState()), DpSize(250.dp, 220.dp))

        onAllNodes(tintedWith(WidgetPalette.bandWoven)).assertCountEquals(1)
        onAllNodes(tintedWith(WidgetPalette.bandOutstanding)).assertCountEquals(1)
        val woven = onNode(tintedWith(WidgetPalette.bandWoven)).mask()
        val outstanding = onNode(tintedWith(WidgetPalette.bandOutstanding)).mask()
        assertEquals(listOf(true, false, true), woven.segmentsInked(3))
        assertEquals(listOf(false, true, false), outstanding.segmentsInked(3))
        assertEquals(
            0,
            onAllNodes(tintedWith(WidgetPalette.bandWoven)).fetchDescribed() +
                onAllNodes(tintedWith(WidgetPalette.bandOutstanding)).fetchDescribed(),
        )
    }

    /**
     * Six habits is eleven children as boxes, one over Glance's cap, and the
     * first cut lost the sixth segment there. As masks the count is two
     * whatever the day holds, and every habit is inked.
     */
    @Test
    fun `six habits are six segments, not five`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val snapshot = todaySnapshot(habits = (1..6).map { todayHabit(id = habitId(it), name = "h$it", completedToday = true) })
        render(WidgetContent.Ready(snapshot.toWidgetState()), DpSize(250.dp, 220.dp))

        onAllNodes(tintedWith(WidgetPalette.bandWoven)).assertCountEquals(1)
        onAllNodes(tintedWith(WidgetPalette.bandOutstanding)).assertCountEquals(0)
        assertEquals(List(6) { true }, onNode(tintedWith(WidgetPalette.bandWoven)).mask().segmentsInked(6))
    }

    /**
     * The direction `WovenBand` derives, pinned through the pixels it changes.
     * `BandBitmapTest` pins the arithmetic *given* a flag; `mirrored` is a local,
     * so composing the real body is the only way to see it at all.
     *
     * **What this catches that nothing else did**, measured rather than assumed
     * after the PR raised it. Hardcoding `mirrored = false` — the behaviour
     * before 2026-08-30, and what dropping the configuration read would leave —
     * fails **this case and nothing else in `:widget`**. The review's other
     * suggested mutation, flipping `==` to `!=`, turns out to be caught already:
     * it mirrors the *LTR* renders too, so the two cases above fail on it. So the
     * uncovered half was RTL specifically, which is what this adds.
     *
     * Asymmetric flags on purpose. The case above uses `[true, false, true]`,
     * which is its own mirror and would assert nothing here.
     *
     * **Not covered, and not claimed:** that `mirrored` joined the band's
     * `remember` keys. That needs two compositions at different configurations
     * within one session, which this harness does not offer; a launcher is still
     * the only thing that would notice (docs/running.md §4).
     */
    @Test
    @Config(qualifiers = "he-rIL")
    fun `under an rtl host the band leads from the far end`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        // Load-bearing: if the qualifier ever stops reaching the configuration,
        // this says so, instead of the pixel assertions failing for a reason
        // that looks like the band.
        assertEquals(
            View.LAYOUT_DIRECTION_RTL,
            RuntimeEnvironment.getApplication().resources.configuration.layoutDirection,
        )
        val snapshot = todaySnapshot(
            habits = listOf(
                todayHabit(id = habitId(1), name = "read", completedToday = true),
                todayHabit(id = habitId(2), name = "walk", completedToday = false),
                todayHabit(id = habitId(3), name = "swim", completedToday = false),
            ),
        )
        render(WidgetContent.Ready(snapshot.toWidgetState()), DpSize(250.dp, 220.dp))

        val woven = onNode(tintedWith(WidgetPalette.bandWoven)).mask()
        // Read right-to-left, the completed habit still leads.
        assertEquals(listOf(true, false, false), woven.segmentsInked(3, mirrored = true))
        // And the picture really moved: flush at the far edge, clear at the near
        // one, which is the half of the arithmetic the off-by-a-gap form inverts.
        assertTrue("the first habit is not flush against the far edge", woven.inkedAt(woven.width - 1))
        assertFalse("the near edge is inked, so the gap fell at the wrong end", woven.inkedAt(0))
    }

    /** One cell tall keeps the widget docs/ux/widget.md §2 settled: no band, however wide. */
    @Test
    fun `one cell tall, however wide, draws no band`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(rows, DpSize(400.dp, 110.dp))

        onAllNodes(tintedWith(WidgetPalette.bandWoven)).assertCountEquals(0)
        onAllNodes(tintedWith(WidgetPalette.bandOutstanding)).assertCountEquals(0)
        onAllNodes(drawnOn(WidgetPalette.momoGround)).assertCountEquals(0)
    }

    @Test
    fun `a failed read has no face at any size`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(WidgetContent.Unavailable, DpSize(250.dp, 220.dp))

        onAllNodes(untintedImage()).assertCountEquals(0)
    }
}

private fun GlanceAppWidgetUnitTest.render(content: WidgetContent, size: DpSize) {
    setContext(RuntimeEnvironment.getApplication())
    setAppWidgetSize(size)
    provideComposable { WidgetBody(content) }
    awaitIdle()
}

private fun checkBox() = GlanceNodeMatcher<MappedNode>("is a checkbox") { it.value.emittable is EmittableCheckBox }

/** The bitmap a matched image node carries. */
private fun GlanceNodeAssertion<MappedNode, *>.mask(): Bitmap {
    var found: Bitmap? = null
    assert(GlanceNodeMatcher("carries a bitmap") { node -> node.value.emittable.bitmap().also { found = it } != null })
    return checkNotNull(found)
}

/**
 * Whether each of [n] equal segments across the mask has any ink at its leading
 * column — the one a reader meets first, `i · width / n` left-to-right and the
 * column one in from `width - i · width / n` when [mirrored].
 *
 * The mirrored sampler is not decoration: a mirrored band read with the *LTR*
 * columns comes back all-false rather than reversed, because mirroring shifts
 * every segment right by one gap and each LTR leading column lands in one.
 * `BandBitmapTest.centres` is generalised the same way.
 */
private fun Bitmap.segmentsInked(n: Int, mirrored: Boolean = false): List<Boolean> = (0 until n).map { i ->
    val edge = i * width / n.toFloat()
    inkedAt((if (mirrored) width - edge - 1f else edge).toInt().coerceIn(0, width - 1))
}

/** Whether the column at [x] carries ink at any height. */
private fun Bitmap.inkedAt(x: Int): Boolean = (0 until height).any { y -> Color.alpha(getPixel(x, y)) > 0 }

/** How many of the matched nodes carry a description; the harness has no negative assertion. */
private fun GlanceNodeAssertionCollection<MappedNode, *>.fetchDescribed(): Int {
    var count = 0
    assertAll(
        GlanceNodeMatcher("counts descriptions") { node ->
            if (node.value.emittable.isDescribed()) count++
            true
        },
    )
    return count
}

/** See WidgetTextColourTest for why 60s and not the harness's 2s default. */
private val RENDER_TIMEOUT = 60.seconds
