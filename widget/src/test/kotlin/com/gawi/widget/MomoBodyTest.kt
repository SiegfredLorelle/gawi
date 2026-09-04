package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import com.gawi.core.domain.mascot.Mood
import com.gawi.widget.testsupport.anyText
import com.gawi.widget.testsupport.describedText
import com.gawi.widget.testsupport.drawnOn
import com.gawi.widget.testsupport.habitId
import com.gawi.widget.testsupport.silentUntintedImage
import com.gawi.widget.testsupport.todayHabit
import com.gawi.widget.testsupport.todaySnapshot
import com.gawi.widget.testsupport.untintedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

/**
 * What the Momo widget's tree holds in each state, and what TalkBack reads of it.
 *
 * The one-reading rule (docs/ux/momo.md §5) is the property under test: exactly
 * one node is described, and which one depends on the state. It composes a
 * `WidgetContent` — the Today widget's own read, which this widget shares.
 * `GraphicsMode.NATIVE` because composing the body renders Momo's bitmap —
 * `WidgetMomoTest` records why LEGACY dies there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MomoBodyTest {

    private val app get() = RuntimeEnvironment.getApplication()

    private val oneOutstanding = WidgetContent.Ready(todaySnapshot(habits = listOf(todayHabit(id = habitId(1)))).toWidgetState())

    @Test
    fun `a mood draws the face on her ground with one word, and the face is what is read`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(oneOutstanding)

        onAllNodes(drawnOn(WidgetPalette.momoGround)).assertCountEquals(1)
        onAllNodes(untintedImage()).assertCountEquals(1)
        onNode(untintedImage()).assertHasContentDescriptionEqualTo(app.getString(R.string.widget_mood_content))
        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(describedText()).assertCountEquals(0)
    }

    /** With no habits the copy is read and the face is decorative — the Today widget's rule for the same state. */
    @Test
    fun `no habits draws the face under the no-habits copy, and the copy is what is read`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(WidgetContent.Ready(todaySnapshot().toWidgetState()))

        onAllNodes(untintedImage()).assertCountEquals(1)
        onAllNodes(silentUntintedImage()).assertCountEquals(1)
        onAllNodes(anyText()).assertCountEquals(1)
        onNode(describedText()).assertHasContentDescriptionEqualTo(app.getString(R.string.widget_no_habits))
    }

    @Test
    fun `a failed read draws the failure copy and no face`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(WidgetContent.Unavailable)

        onAllNodes(untintedImage()).assertCountEquals(0)
        onNode(describedText()).assertHasContentDescriptionEqualTo(app.getString(R.string.widget_unavailable))
    }

    @Test
    fun `loading draws the ground and nothing on it`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(WidgetContent.Loading)

        onAllNodes(drawnOn(WidgetPalette.momoGround)).assertCountEquals(1)
        onAllNodes(untintedImage()).assertCountEquals(0)
        onAllNodes(anyText()).assertCountEquals(0)
    }

    /** Four moods, four words — a mapper that reused one would pass a weaker test. */
    @Test
    fun `every mood has its own word`() {
        assertEquals(Mood.entries.size, Mood.entries.map { it.caption() }.toSet().size)
    }

    /**
     * The face gives way to the caption, never the other way round: full size
     * where the tile has room, shrinking as the text scale eats it, and gone
     * when the two-line no-habits copy leaves less than a face. Review found
     * the unbounded 72 overflowed a 110dp tile from about 1.3× on, and the PR
     * found the reservation counting one line where up to three were drawn.
     */
    @Test
    fun `the face is full size on the default tile and gives way to a scaled caption`() {
        val tile = DpSize(110.dp, 110.dp)
        assertEquals(MomoBitmap.HEIGHT_DP, momoFaceHeight(tile, textScale = 1f, captionLines = 1))
        // A doubled caption on the default tile leaves less than the constant and more than the floor.
        val scaled = momoFaceHeight(tile, textScale = 2f, captionLines = 1)
        assertTrue(scaled != null && scaled < MomoBitmap.HEIGHT_DP)
        // Two lines at 2x leave 31dp, under the floor: no face rather than a sliver.
        assertNull(momoFaceHeight(tile, textScale = 2f, captionLines = 2))
        // A taller tile never grows her past the constant.
        assertEquals(MomoBitmap.HEIGHT_DP, momoFaceHeight(DpSize(300.dp, 300.dp), textScale = 1f, captionLines = 1))
    }

    /** The empty tile at 200 %: the copy stays whole and read, and the face steps aside. */
    @Test
    @Config(fontScale = 2f)
    fun `no habits at a doubled font scale draws the copy and no face`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        render(WidgetContent.Ready(todaySnapshot().toWidgetState()))

        onAllNodes(untintedImage()).assertCountEquals(0)
        onNode(describedText()).assertHasContentDescriptionEqualTo(app.getString(R.string.widget_no_habits))
    }

    private fun GlanceAppWidgetUnitTest.render(content: WidgetContent) {
        setContext(app)
        setAppWidgetSize(DpSize(110.dp, 110.dp))
        provideComposable { MomoBody(content) }
        awaitIdle()
    }
}

/** See WidgetTextColourTest for why 60s and not the harness's 2s default. */
private val RENDER_TIMEOUT = 60.seconds
