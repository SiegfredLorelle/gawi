package com.gawi.core.testing

import android.provider.Settings
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment

/**
 * Animations off, the way a user turns them off, for the life of a test.
 *
 * `Momo` runs a frame loop while it animates, and a composition with a frame
 * awaiter is never idle, so every `waitForIdle` would time out with a message
 * that names nothing. With the animator scale at zero Momo draws its resting
 * frame (docs/ux/momo.md §5), which is the frame these tests are about anyway.
 *
 * A rule rather than a `@Before`, and ordered **before** any compose rule:
 * `createAndroidComposeRule` launches the activity inside the rule, so a
 * `@Before` runs after the first composition.
 *
 * Needs Robolectric on the consumer's test classpath; this module has it as
 * compileOnly so that consumers without it are not handed it.
 */
class AnimationsOffRule : ExternalResource() {
    private var previous: Float = 1f

    override fun before() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        previous = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    override fun after() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous)
    }
}
