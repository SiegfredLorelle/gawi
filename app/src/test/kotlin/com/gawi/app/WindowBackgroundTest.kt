package com.gawi.app

import androidx.compose.ui.graphics.toArgb
import com.gawi.core.ui.theme.gawiWindowBackground
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The XML window background is the colour the Compose theme draws.
 *
 * There are two copies of it and there has to be: the window is painted from
 * `values/themes.xml` before `setContent` runs, and XML cannot read Kotlin. So
 * `@color/gawi_window_background` is a hand-copy of the theme's `surface`, and a
 * comment asking the next person to change both is not a mechanism. Left to a
 * comment, retuning `surface` gives a cold start that flashes one colour and
 * settles on another, with lint and every other test green — the same shape of
 * defect the restyle's device pass turned up in the retro strip
 * (docs/ux/visual-identity.md §3).
 *
 * Here rather than in `:core:ui` because the resource is `:app`'s, and it reads
 * the expected value through [gawiWindowBackground] because the schemes
 * themselves stay `internal` to that module.
 *
 * **No Compose rule.** `createComposeRule()` launches the `ComponentActivity`
 * that `ui-test-manifest` declares, and for an *application* module Robolectric
 * resolves against `:app`'s own merged manifest, which does not carry it —
 * measured, and the reason this reads the resource directly instead. It needs
 * Robolectric only for resource qualifier resolution.
 *
 * Two themes, two classes, for the reason `WidgetTextColourTest` gives: one
 * alone is a trapdoor. Night-only would let a light-mode mismatch through, and
 * light-only would miss the dark one — the wider gap of the two, because the
 * platform's own window grey sits far closer to the light surface than to the
 * dark one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "notnight")
class WindowBackgroundLightTest {

    @Test
    fun `the light window background matches the light surface`() {
        assertWindowBackgroundMatches(darkTheme = false)
    }
}

/** The mirror. See the light case for why one theme alone is not enough. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "night")
class WindowBackgroundDarkTest {

    @Test
    fun `the dark window background matches the dark surface`() {
        assertWindowBackgroundMatches(darkTheme = true)
    }
}

/**
 * Compared as hex strings, because the failure is read by a person: an `Int`
 * mismatch reports two nine-digit negatives with no hint which channel moved.
 */
private fun assertWindowBackgroundMatches(darkTheme: Boolean) {
    val fromXml = RuntimeEnvironment.getApplication().getColor(R.color.gawi_window_background)
    assertEquals(
        "values/themes.xml and GawiTheme disagree, so a cold start will flash",
        Integer.toHexString(gawiWindowBackground(darkTheme).toArgb()),
        Integer.toHexString(fromXml),
    )
}
