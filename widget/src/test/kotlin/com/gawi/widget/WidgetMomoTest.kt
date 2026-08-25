package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.EmittableImage
import androidx.glance.appwidget.EmittableCheckBox
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.semantics.SemanticsModifier
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
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
    fun `two cells tall, Momo sits above the rows and says how she is`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(rows, DpSize(250.dp, 220.dp))

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

/** See WidgetTextColourTest for why 60s and not the harness's 2s default. */
private val RENDER_TIMEOUT = 60.seconds
