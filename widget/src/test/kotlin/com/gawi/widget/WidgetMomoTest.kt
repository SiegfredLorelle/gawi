package com.gawi.widget

import android.graphics.Bitmap
import android.graphics.Color
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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
     * this asserts left-to-right, the only direction it runs in; the band does
     * not mirror under RTL (docs/ux/widget.md §8). Two
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

/** Whether each of [n] equal segments across the mask has any ink at its leading column, `i · width / n`. */
private fun Bitmap.segmentsInked(n: Int): List<Boolean> = (0 until n).map { i ->
    val x = (i * width / n.toFloat()).toInt().coerceIn(0, width - 1)
    (0 until height).any { y -> Color.alpha(getPixel(x, y)) > 0 }
}

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
