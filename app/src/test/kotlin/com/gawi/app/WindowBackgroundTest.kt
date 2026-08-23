package com.gawi.app

import androidx.compose.ui.graphics.toArgb
import com.gawi.core.ui.theme.gawiWindowBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
 * Resolved through `Theme.Gawi`, not read off `R.color` directly.
 *
 * That distinction is the whole test. Reading the colour resource pins its
 * *value* and says nothing about the *wiring*: with
 * `<item name="android:windowBackground">` deleted from either `themes.xml` the
 * resource still matches this scheme, the assertion still passes, and a cold
 * start still flashes — the defect intact and the guard green. Resolving the
 * attribute covers both links in one assertion, because it can only arrive at
 * this colour if the theme names it. Mutation-checked in both directions:
 * deleting the item fails the matching case, and changing the colour value fails
 * it differently.
 *
 * Compared as hex strings, because the failure is read by a person: an `Int`
 * mismatch reports two nine-digit negatives with no hint which channel moved.
 */
private fun assertWindowBackgroundMatches(darkTheme: Boolean) {
    val theme = RuntimeEnvironment.getApplication().resources.newTheme()
    theme.applyStyle(R.style.Theme_Gawi, true)
    val attrs = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
    val resolved = try {
        attrs.getColor(0, NOT_A_COLOUR)
    } finally {
        attrs.recycle()
    }

    // A guard against getColor silently defaulting, which it does whenever the
    // attribute resolves to something that is not a colour — a drawable, as many
    // window themes use. Measured, so as not to overclaim: it is *not* what
    // catches a deleted item here. Both framework parents answer with a colour
    // of their own, so removing the item is caught by the comparison below
    // (light resolved fffafafa against fff4fbfa). This guard is for the day a
    // parent changes, or someone points the item at a drawable.
    assertNotEquals(
        "Theme.Gawi resolved no colour for android:windowBackground",
        NOT_A_COLOUR,
        resolved,
    )
    assertEquals(
        "Theme.Gawi and GawiTheme disagree, so a cold start will flash",
        Integer.toHexString(gawiWindowBackground(darkTheme).toArgb()),
        Integer.toHexString(resolved),
    )
}

/** A value no scheme colour can take: every surface here is fully opaque. */
private const val NOT_A_COLOUR = 0
