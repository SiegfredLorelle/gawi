package com.gawi.core.ui.component

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
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
 * `:feature:today`'s and `:app`'s test sets zeroes the scale before the
 * activity launches; every loop gated here goes quiet with it, and a loop
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
