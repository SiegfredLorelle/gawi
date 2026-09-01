package com.gawi.feature.today

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.gawi.core.domain.mascot.Mood
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `celebrates`' cold-start guard, as behaviour: a day already finished before
 * the screen opened is not celebrated, and a day finished while watching is.
 *
 * **Why this is not in `TodayScreenTest`.** That class runs under
 * [AnimationsOffRule], and `CelebrationState.see` only animates when the gate is
 * on — so every "no celebration" assertion there passes whatever the guard does,
 * which is exactly what `with animations off finishing the day draws no
 * celebration` pins on purpose. Here the gate is a *parameter*, passed `true`
 * directly, so the run is observable and its absence means something. Nothing
 * composes `Momo`, so there is no frame loop to keep the composition busy.
 *
 * **Why the guard needs a test at all.** It used to be free: every branch of
 * Today built its own `TodayMotion`, so `Loading -> Habits` composed a fresh
 * `CelebrationState` with `previous` null and could not celebrate. Since the
 * motion was hoisted above the `Scaffold` for the app-bar chip
 * (docs/ux/today-view.md §1) one state outlives that change, and the null mood
 * is what keeps `previous` unseeded. Feed `Loading` a stand-in mood instead and
 * the first real thriving throws a party for something that happened before the
 * app opened.
 *
 * The two tests are a pair. The second is the control: without it a broken rig
 * that could never observe a celebration would make the first one green.
 */
@RunWith(RobolectricTestRunner::class)
class CelebrationGuardTest {

    @get:Rule
    val compose = createComposeRule()

    /** `Loading` then a finished day: the mood the screen opens on is a sighting, not an event. */
    @Test
    fun `a mood arriving after none never celebrates`() {
        val mood = mutableStateOf<Mood?>(null)
        lateinit var state: CelebrationState
        compose.mainClock.autoAdvance = false
        compose.setContent { state = rememberCelebration(mood.value, animationsOn = true) }
        settle()
        assertTrue("there is nothing to celebrate before a mood arrives", state.isOver.value)

        mood.value = Mood.THRIVING
        settle()

        assertTrue("a day already finished when the screen opened is not a celebration", state.isOver.value)
    }

    /** The control: the same rig must be able to see a real celebration, or the test above proves nothing. */
    @Test
    fun `finishing the day while watching celebrates`() {
        val mood = mutableStateOf<Mood?>(Mood.CONTENT)
        lateinit var state: CelebrationState
        compose.mainClock.autoAdvance = false
        compose.setContent { state = rememberCelebration(mood.value, animationsOn = true) }
        settle()
        assertTrue("the resting state, before the day is finished", state.isOver.value)

        mood.value = Mood.THRIVING
        settle()

        assertFalse("entering thriving from content is the day-complete celebration", state.isOver.value)
    }

    /** A few frames with the clock held, as `TodayScreenTest` does it: advancing does not recompose and waitForIdle does not move time. */
    private fun settle() {
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }
}
