package com.gawi.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasContentDescriptionEqualTo
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import com.gawi.core.testing.todayHabit
import com.gawi.core.testing.todaySnapshot
import com.gawi.widget.testsupport.describedText
import com.gawi.widget.testsupport.drawnOn
import com.gawi.widget.testsupport.launchIntent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

/**
 * The sentence a Today or Streaks body says outside its rows is carried by the
 * body's root, which opens the app, and by nothing inside it — `spokenRoot` has
 * why a described line inside a launcher frame is otherwise never reached
 * (docs/running.md §4 heard it). A body with nothing to say has neither half.
 *
 * `GraphicsMode.NATIVE` because the tall bodies render Momo's bitmap;
 * `WidgetMomoTest` records why LEGACY dies there.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetRootTest {

    private val app get() = RuntimeEnvironment.getApplication()

    private val snapshot = todaySnapshot(
        habits = listOf(todayHabit(id = habitId(1), name = "read"), todayHabit(id = habitId(2), name = "walk")),
    )
    private val rows = WidgetContent.Ready(snapshot.toWidgetState())

    @Test
    fun `the face-above-rows body says the mood from its root`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        today(rows, DpSize(180.dp, 220.dp))

        assertRootSays(app.getString(R.string.widget_mood_content))
    }

    @Test
    fun `the large body says the mood line from its root, and the line itself is silent`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        today(rows, DpSize(250.dp, 220.dp))

        assertRootSays(app.getString(R.string.widget_mood_content))
        onAllNodes(describedText()).assertCountEquals(0)
    }

    @Test
    fun `the no-habits body says its copy from its root`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        today(WidgetContent.Ready(todaySnapshot().toWidgetState()), DpSize(250.dp, 220.dp))

        assertRootSays(app.getString(R.string.widget_no_habits))
        onAllNodes(describedText()).assertCountEquals(0)
    }

    /** One cell tall there is no face and no copy, so the rows are all there is to hear. */
    @Test
    fun `rows alone leave the root silent`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        today(rows, DpSize(250.dp, 110.dp))

        assertRootSilent()
    }

    @Test
    fun `loading leaves the root silent`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        today(WidgetContent.Loading, DpSize(250.dp, 220.dp))

        assertRootSilent()
    }

    /** §7.1's provenance line is announced as well as drawn, from the root. */
    @Test
    fun `the streak body says its as-of line from its root`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        streaks(StreakContent.Ready(snapshot.toStreakState()))

        val locale = app.resources.configuration.locales[0]
        assertRootSays(app.getString(R.string.widget_streak_as_of, formatAsOf(FIXED_DATE, locale)))
        onAllNodes(describedText()).assertCountEquals(0)
    }

    @Test
    fun `the empty streak body says its copy from its root`() = runGlanceAppWidgetUnitTest(RENDER_TIMEOUT) {
        streaks(StreakContent.Ready(todaySnapshot().toStreakState()))

        assertRootSays(app.getString(R.string.widget_no_habits))
        onAllNodes(describedText()).assertCountEquals(0)
    }

    private fun GlanceAppWidgetUnitTest.assertRootSays(sentence: String) {
        onNode(drawnOn(WidgetPalette.surface))
            .assertHasContentDescriptionEqualTo(sentence)
            .assert(hasStartActivityClickAction(launchIntent(app)))
    }

    private fun GlanceAppWidgetUnitTest.assertRootSilent() {
        onAllNodes(hasStartActivityClickAction(launchIntent(app))).assertCountEquals(0)
    }

    private fun GlanceAppWidgetUnitTest.today(content: WidgetContent, size: DpSize) {
        setContext(app)
        setAppWidgetSize(size)
        provideComposable { WidgetBody(content) }
        awaitIdle()
    }

    private fun GlanceAppWidgetUnitTest.streaks(content: StreakContent) {
        setContext(app)
        setAppWidgetSize(DpSize(250.dp, 200.dp))
        provideComposable { StreakBody(content) }
        awaitIdle()
    }
}

/** See WidgetTextColourTest for why 60s and not the harness's 2s default. */
private val RENDER_TIMEOUT = 60.seconds
