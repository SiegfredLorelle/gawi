package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.EmittableImage
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.semantics.SemanticsModifier
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import com.gawi.core.domain.mascot.Mood
import com.gawi.widget.testsupport.anyText
import com.gawi.widget.testsupport.drawnOn
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

/**
 * What the Momo widget's tree holds in each state, and what TalkBack reads of it.
 *
 * The one-reading rule (docs/ux/momo.md §5) is the property under test: exactly
 * one node is described, and which one depends on the state. `GraphicsMode.NATIVE`
 * because composing the body renders Momo's bitmap — `WidgetMomoTest` records why
 * LEGACY dies there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MomoBodyTest {

    private val app get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a mood draws the face on her ground with one word, and the face is what is read`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(MomoContent.Ready(Mood.WORRIED, empty = false))

        onAllNodes(drawnOn(WidgetPalette.momoGround)).assertCountEquals(1)
        onAllNodes(untintedImage()).assertCountEquals(1)
        onNode(untintedImage()).assertHasContentDescriptionEqualTo(app.getString(R.string.widget_mood_worried))
        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(describedText()).assertCountEquals(0)
    }

    /** With no habits the copy is read and the face is decorative — the Today widget's rule for the same state. */
    @Test
    fun `no habits draws the face under the no-habits copy, and the copy is what is read`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(MomoContent.Ready(Mood.CONTENT, empty = true))

        onAllNodes(untintedImage()).assertCountEquals(1)
        onAllNodes(silentUntintedImage()).assertCountEquals(1)
        onAllNodes(anyText()).assertCountEquals(1)
        onNode(describedText()).assertHasContentDescriptionEqualTo(app.getString(R.string.widget_no_habits))
    }

    @Test
    fun `a failed read draws the failure copy and no face`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(MomoContent.Unavailable)

        onAllNodes(untintedImage()).assertCountEquals(0)
        onNode(describedText()).assertHasContentDescriptionEqualTo(app.getString(R.string.widget_unavailable))
    }

    @Test
    fun `loading draws the ground and nothing on it`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(MomoContent.Loading)

        onAllNodes(drawnOn(WidgetPalette.momoGround)).assertCountEquals(1)
        onAllNodes(untintedImage()).assertCountEquals(0)
        onAllNodes(anyText()).assertCountEquals(0)
    }

    private fun GlanceAppWidgetUnitTest.render(content: MomoContent) {
        setContext(app)
        setAppWidgetSize(DpSize(110.dp, 110.dp))
        provideComposable { MomoBody(content) }
        awaitIdle()
    }
}

/** An image with no colour filter — Momo, who carries her own palette. */
private fun untintedImage() = GlanceNodeMatcher<MappedNode>("is an untinted image") { node ->
    (node.value.emittable as? EmittableImage)?.let { it.colorFilterParams == null } == true
}

private fun silentUntintedImage() = GlanceNodeMatcher<MappedNode>("is an untinted image with no description") { node ->
    (node.value.emittable as? EmittableImage)?.let { image ->
        image.colorFilterParams == null && !image.modifier.foldIn(false) { described, element -> described || element is SemanticsModifier }
    } == true
}

/** A tinted string carrying a description. */
private fun describedText() = GlanceNodeMatcher<MappedNode>("is a described text") { node ->
    node.value.emittable.let {
        it is EmittableImage && it.colorFilterParams != null &&
            it.modifier.foldIn(false) { d, e -> d || e is SemanticsModifier }
    }
}

/** See WidgetTextColourTest for why 60s and not the harness's 2s default. */
private val RENDER_TIMEOUT = 60.seconds
