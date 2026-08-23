package com.gawi.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The designed schemes are legible, in both themes, at every pair the app draws.
 *
 * Plain JVM, no Robolectric: a `ColorScheme` is a holder of `Color`s and both
 * are ordinary Kotlin, so the schemes can be read directly. That is what makes
 * this cheap enough to assert exhaustively rather than on a sample.
 *
 * **There was no test for the theme at all before this**, which is how a role
 * could drift below a floor and ship. That is not hypothetical in this repo: the
 * widget drew near-black text on a near-black surface at 1.59:1 through a phase
 * of green builds, because what asserted the widget's *content* never asserted
 * how it looked. `HabitColorTest` covers a habit's own colour; this covers
 * everything the theme decides.
 *
 * **What it deliberately does not assert.** `surfaceVariant` and the container
 * roles sit close to `surface` on purpose — they are fills (the icon-picker
 * swatch, [HabitIcon]'s fallback circle), and the contrast they owe is to their
 * own contents, which is the `onX` pair below. Holding a fill to 3:1 against the
 * page would pin the wrong property and force a design that looks like a
 * button.
 */
class GawiColorSchemeTest {

    @Test
    fun `every foreground the app draws clears its floor in both themes`() {
        schemes.forEach { (theme, scheme) ->
            scheme.pairings().forEach { pair ->
                val ratio = contrastRatio(pair.foreground, pair.background)
                assertTrue("$theme ${pair.what} drew at $ratio, floor ${pair.floor}", ratio >= pair.floor)
            }
        }
    }

    @Test
    fun `a day streak and a week streak differ in lightness, not only in hue`() {
        // docs/ux/visual-identity.md §4.1: StreakBadge distinguishes a day
        // streak from a week streak by primary versus tertiary, and the text
        // differs only by a trailing "w". Equal-lightness roles would leave the
        // distinction to hue alone, which is nothing in greyscale and nothing
        // under deuteranopia. §3's published tertiary failed this in dark mode
        // at 1.02 — the reason the value here is not the one in that table.
        schemes.forEach { (theme, scheme) ->
            val step = contrastRatio(scheme.primary, scheme.tertiary)
            assertTrue("$theme primary and tertiary are $step apart in lightness", step >= MIN_LIGHTNESS_STEP)
        }
    }

    @Test
    fun `the dimmed roles are dimmer than the plain one`() {
        // §4.1 again: outline and onSurfaceVariant carry the broken-streak and
        // shut-day states, so "recessive" is a meaning here and not a leftover.
        // Asserted as being nearer the surface than onSurface is, which is what
        // recessive means, rather than as a hardcoded ratio.
        schemes.forEach { (theme, scheme) ->
            val plain = contrastRatio(scheme.onSurface, scheme.surface)
            listOf("outline" to scheme.outline, "onSurfaceVariant" to scheme.onSurfaceVariant).forEach { (role, color) ->
                val dimmed = contrastRatio(color, scheme.surface)
                assertTrue("$theme $role at $dimmed is not recessive against onSurface at $plain", dimmed < plain)
            }
        }
    }

    @Test
    fun `background and surface agree, because the glyph tests depend on it`() {
        // Not a style rule — a dependency between two test files. HabitIcon and
        // ColorPicker composite a habit's tint over `background` before choosing
        // its glyph, while HabitColorTest measures those glyphs against
        // `surface`, and the badge assertions here use `surface` too. While the
        // two roles are equal that is the same measurement. If they ever diverge
        // both files keep passing and start measuring a background nothing draws
        // on, which is precisely how the widget's 1.59:1 defect survived. So the
        // assumption is pinned here rather than left in a comment.
        schemes.forEach { (theme, scheme) ->
            assertEquals("$theme draws on a background that is not its surface", scheme.surface, scheme.background)
        }
    }

    @Test
    fun `the two schemes are actually different`() {
        // Cheap, and it rules out the failure that would make every assertion
        // above pass while measuring one theme twice.
        assertTrue("the two schemes share a surface", GawiLightColors.surface != GawiDarkColors.surface)
        assertTrue("the two schemes share a primary", GawiLightColors.primary != GawiDarkColors.primary)
    }

    private companion object {
        val schemes = listOf("light" to GawiLightColors, "dark" to GawiDarkColors)

        /**
         * §4.1 measured 1.27-1.74 across the candidates and rejected the
         * ~1.05 a same-tone pair gives. The floor is the bottom of that.
         */
        const val MIN_LIGHTNESS_STEP = 1.27f
    }
}

/** A foreground, what it is drawn on, and the floor that pair owes. */
private data class Pairing(val what: String, val foreground: Color, val background: Color, val floor: Float)

/**
 * Every pair the app actually draws, named.
 *
 * Enumerated rather than derived from the scheme's shape, because "which role
 * sits on which" is a fact about the screens and not about `ColorScheme`. A
 * derived version would assert something Material happens to imply instead of
 * something this app does.
 */
private fun ColorScheme.pairings(): List<Pairing> = buildList {
    fun text(what: String, foreground: Color, background: Color) = add(Pairing(what, foreground, background, WCAG_TEXT_FLOOR))

    // Content on the page.
    text("onSurface on surface", onSurface, surface)
    text("onSurfaceVariant on surface", onSurfaceVariant, surface)
    text("onBackground on background", onBackground, background)
    // Roles used directly as text: primary and tertiary are StreakBadge and
    // RetroStrip, outline is a broken streak, error is the delete confirmation.
    text("primary on surface", primary, surface)
    text("tertiary on surface", tertiary, surface)
    text("outline on surface", outline, surface)
    text("error on surface", error, surface)

    // Accent pairs.
    text("onPrimary on primary", onPrimary, primary)
    text("onSecondary on secondary", onSecondary, secondary)
    text("onTertiary on tertiary", onTertiary, tertiary)
    text("onError on error", onError, error)
    text("onPrimaryContainer on primaryContainer", onPrimaryContainer, primaryContainer)
    text("onSecondaryContainer on secondaryContainer", onSecondaryContainer, secondaryContainer)
    text("onTertiaryContainer on tertiaryContainer", onTertiaryContainer, tertiaryContainer)
    text("onErrorContainer on errorContainer", onErrorContainer, errorContainer)
    text("inverseOnSurface on inverseSurface", inverseOnSurface, inverseSurface)

    // The surface family. Any of these can end up under body text, so both
    // content roles are checked against all of them rather than against the one
    // surface a screen happens to use today.
    val surfaces = listOf(
        "surfaceBright" to surfaceBright,
        "surfaceDim" to surfaceDim,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
        "surfaceVariant" to surfaceVariant,
    )
    surfaces.forEach { (name, color) ->
        text("onSurface on $name", onSurface, color)
        text("onSurfaceVariant on $name", onSurfaceVariant, color)
    }

    // The fixed accents, which Material specifies as theme-invariant. Nothing
    // draws them yet; they are checked because a scheme that declares a role
    // owns it, and an unused role is exactly the one that ships wrong.
    listOf(
        Triple("primary", primaryFixed, primaryFixedDim) to (onPrimaryFixed to onPrimaryFixedVariant),
        Triple("secondary", secondaryFixed, secondaryFixedDim) to (onSecondaryFixed to onSecondaryFixedVariant),
        Triple("tertiary", tertiaryFixed, tertiaryFixedDim) to (onTertiaryFixed to onTertiaryFixedVariant),
    ).forEach { (fixed, ons) ->
        val (family, plain, dim) = fixed
        val (on, onVariant) = ons
        text("on${family}Fixed on ${family}Fixed", on, plain)
        text("on${family}Fixed on ${family}FixedDim", on, dim)
        text("on${family}FixedVariant on ${family}Fixed", onVariant, plain)
        text("on${family}FixedVariant on ${family}FixedDim", onVariant, dim)
    }
}
