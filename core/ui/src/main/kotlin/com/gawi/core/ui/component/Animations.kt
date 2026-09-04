package com.gawi.core.ui.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext

/**
 * Whether this composition should move at all.
 *
 * The system's *Animator duration scale* is the one switch a viewer has for
 * turning animation off, and everything that loops in Gawi — Momo, the tank
 * life behind it, a celebration — answers to it (docs/ux/momo.md §5). Read once
 * per composition, in a side effect, because it is a binder call; not observed
 * afterwards, because a developer-option toggle is not worth a
 * `ContentObserver`, so a change takes effect at the next composition
 * (docs/running.md §4). `false` until it has been read, so the first frame of
 * anything is its resting frame and never a stray moving one.
 *
 * One reader rather than one per animated thing, for a reason a test found:
 * a frame loop is a permanent awaiter on the frame clock, so a Robolectric
 * composition with any loop running is never idle. `AnimationsOffRule` in
 * `:core:testing` zeroes the scale before the activity launches; every loop
 * gated here goes quiet with it, and a loop
 * gated on anything else would hang every screen test that composes it.
 *
 * [animated] is the caller's own override — a preview or a still surface
 * passes `false` — and it wins over the setting.
 */
@Composable
fun rememberAnimationsEnabled(animated: Boolean = true): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = false, animated) {
        value = animated &&
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/**
 * Seconds since this composition started moving, advanced every frame while
 * [animationsOn] and held at 0 otherwise — the clock every looping frame
 * function ([MomoFrame.at] and the tank's) is a pure function of.
 *
 * Read it inside a draw lambda, never in composition: a `Canvas` that reads
 * the value redraws each frame, while a composable that reads it recomposes
 * each frame. This is the frame loop the KDoc on [Momo] warns about — a
 * permanent awaiter on the frame clock, so a Robolectric composition running
 * one is never idle; it goes quiet with the gate above.
 */
@Composable
fun rememberFrameClock(animationsOn: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationsOn) {
        if (!animationsOn) {
            seconds.floatValue = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> seconds.floatValue = (now - start) / NANOS_PER_SECOND }
        }
    }
    return seconds
}

private const val NANOS_PER_SECOND = 1_000_000_000f
