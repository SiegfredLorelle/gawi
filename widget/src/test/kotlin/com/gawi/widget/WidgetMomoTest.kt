package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.BackgroundModifier
import androidx.glance.Emittable
import androidx.glance.EmittableImage
import androidx.glance.appwidget.EmittableCheckBox
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.semantics.SemanticsModifier
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import androidx.glance.unit.ColorProvider
import com.gawi.widget.testsupport.anyText
import com.gawi.widget.testsupport.drawnOn
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
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
     * The band is the rows' flags, one segment each, in the rows' order — woven
     * segments on [WidgetPalette.bandWoven] and outstanding ones on
     * [WidgetPalette.bandOutstanding], matched by provider identity so a swap of
     * the two turns this red. Nothing in the band is described.
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

        onAllNodes(drawnOn(WidgetPalette.bandWoven)).assertCountEquals(2)
        onAllNodes(drawnOn(WidgetPalette.bandOutstanding)).assertCountEquals(1)
        // Document order is the rows' order; the collection walks the tree depth-first.
        val band = onAllNodes(segment())
        band.assertCountEquals(3)
        band[0].assert(drawnOn(WidgetPalette.bandWoven))
        band[1].assert(drawnOn(WidgetPalette.bandOutstanding))
        band[2].assert(drawnOn(WidgetPalette.bandWoven))
        onAllNodes(describedSegment()).assertCountEquals(0)
    }

    /** One cell tall keeps the widget docs/ux/widget.md §2 settled: no band, however wide. */
    @Test
    fun `one cell tall, however wide, draws no band`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(rows, DpSize(400.dp, 110.dp))

        onAllNodes(segment()).assertCountEquals(0)
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

/** An image with no colour filter — Momo, who carries her own palette. */
private fun untintedImage() = GlanceNodeMatcher<MappedNode>("is an untinted image") { node ->
    (node.value.emittable as? EmittableImage)?.let { it.colorFilterParams == null } == true
}

private fun checkBox() = GlanceNodeMatcher<MappedNode>("is a checkbox") { it.value.emittable is EmittableCheckBox }

/** The same, carrying no description: decorative. Glance's harness has no negative assertion, so a matcher. */
private fun silentUntintedImage() = GlanceNodeMatcher<MappedNode>("is an untinted image with no description") { node ->
    // Glance's Image attaches a SemanticsModifier only when given a description,
    // so an image with none in its modifier chain is a decorative one.
    (node.value.emittable as? EmittableImage)?.let { image ->
        image.colorFilterParams == null && !image.modifier.foldIn(false) { described, element -> described || element is SemanticsModifier }
    } == true
}

/** A tinted string carrying a description — the mood line; every other string in the large body is decorative. */
private fun describedText() = GlanceNodeMatcher<MappedNode>("is a described text") { node ->
    node.value.emittable.let {
        it is EmittableImage && it.colorFilterParams != null &&
            it.modifier.foldIn(false) { d, e -> d || e is SemanticsModifier }
    }
}

/** The colour provider a node's background is drawn with, or null. */
private fun Emittable.ground(): ColorProvider? = modifier
    .foldIn<BackgroundModifier.Color?>(null) { found, element -> found ?: element as? BackgroundModifier.Color }
    ?.colorProvider

/** A band segment: a box on one of the two band fills. */
private fun segment() = GlanceNodeMatcher<MappedNode>("is a band segment") { node ->
    node.value.emittable.ground().let { it === WidgetPalette.bandWoven || it === WidgetPalette.bandOutstanding }
}

private fun describedSegment() = GlanceNodeMatcher<MappedNode>("is a band segment with a description") { node ->
    val e = node.value.emittable
    e.ground().let { it === WidgetPalette.bandWoven || it === WidgetPalette.bandOutstanding } &&
        e.modifier.foldIn(false) { d, el -> d || el is SemanticsModifier }
}

/** See WidgetTextColourTest for why 60s and not the harness's 2s default. */
private val RENDER_TIMEOUT = 60.seconds
