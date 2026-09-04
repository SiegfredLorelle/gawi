package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
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
 * Every string the Momo widget draws is legible on the ground it is drawn on —
 * which is **not** the surface the other two widgets use. The caption sits on
 * `primaryContainer`, so `WidgetTextColourTest`'s measurements say nothing about
 * it, and this is the pair `WidgetPaletteTest` also holds without a tree. Both
 * themes, one class each, for the reason that test's KDoc gives.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MomoTextColourDarkTest : MomoTextColourContract()

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "notnight")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MomoTextColourLightTest : MomoTextColourContract()

abstract class MomoTextColourContract {

    @Test
    fun `the word under the face is legible on Momo's ground`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(WidgetContent.Ready(todaySnapshot(habits = listOf(todayHabit(id = habitId(1)))).toWidgetState()))

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    @Test
    fun `the no-habits copy is legible on Momo's ground`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(WidgetContent.Ready(todaySnapshot().toWidgetState()))

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }

    @Test
    fun `the failure copy is legible on Momo's ground`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        val probe = renderWithProbe(WidgetContent.Unavailable)

        onAllNodes(anyText()).assertCountEquals(1)
        onAllNodes(illegibleText(probe.context, probe.background)).assertCountEquals(0)
    }
}

/** The ground actually drawn, asserted by identity before anything is measured against it. */
private fun GlanceAppWidgetUnitTest.renderWithProbe(content: WidgetContent): RenderProbe {
    val context = RuntimeEnvironment.getApplication()
    setContext(context)
    setAppWidgetSize(DpSize(110.dp, 110.dp))
    provideComposable { MomoBody(content) }
    awaitIdle()
    onAllNodes(drawnOn(WidgetPalette.momoGround)).assertCountEquals(1)
    return RenderProbe(context, WidgetPalette.momoGround.getColor(context))
}

private val RENDER_TIMEOUT = 60.seconds
