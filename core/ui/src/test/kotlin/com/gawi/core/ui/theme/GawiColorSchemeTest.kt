package com.gawi.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.gawi.core.testing.WCAG_NON_TEXT_FLOOR
import com.gawi.core.testing.WCAG_TEXT_FLOOR
import com.gawi.core.testing.contrastRatio
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
 * **What it deliberately does not assert, and the reasoning for each.**
 *
 *  - **A fill against the page.** `surfaceVariant` and the container roles sit
 *    close to `surface` on purpose — they are grounds (the icon-picker swatch,
 *    [HabitIcon]'s fallback circle, the strip's today cell), and the contrast
 *    they owe is to their own contents, which is what the pairs below check.
 *    Holding a fill to 3:1 against the page would pin the wrong property and
 *    force every quiet surface to look like a button. Two fills against *each
 *    other* are a different question and are held: the history grid's cells say
 *    done-versus-not-done with nothing but their ground, so that pair carries
 *    meaning where a single fill does not.
 *  - **`outlineVariant` anywhere.** It draws exactly one thing, the shut cell's
 *    border in `RetroStrip`, at about 1.5:1 on `surface` in both themes — well
 *    under 1.4.11's 3:1. Accepted rather than overlooked: Material specifies
 *    this role as a low-contrast divider, a shut day is meant to be the quietest
 *    thing on the strip, and the state has two louder carriers anyway — the
 *    struck-through number and its dimmed `outline` colour, both asserted. A 3:1
 *    border would make the disabled cell the most emphatic one on screen.
 *  - **Accent roles on grounds nothing pairs them with.** `primary`, `tertiary`,
 *    `outline` and `error` are checked on `surface` and, where the app draws
 *    them there, on `secondaryContainer`. They are not swept across the whole
 *    surface family, because which role sits on which is a fact about the
 *    screens. **The known constraint, so it is not rediscovered the hard way:**
 *    `outline` on `surfaceContainerHigh` — Material's default `AlertDialog`
 *    ground — is about 4.1:1 in dark. A broken-streak or shut-day label inside a
 *    dialog would ship under the floor, so add the pair here when something
 *    draws it.
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
    fun `every habit hue is visible as a badge on both surfaces`() {
        // A habit's colour is a graphic that carries meaning, so 3:1 rather
        // than 4.5:1 — the glyph drawn on top of it is text and HabitColorTest
        // holds that to the text floor. Here because the floor is a property of
        // the surface the badge sits on, which is this file's subject.
        schemes.forEach { (theme, scheme) ->
            HabitPalette.Colors.forEach { hex ->
                val ratio = contrastRatio(parseHabitColor(hex)!!, scheme.surface)
                assertTrue("$theme badge $hex drew at $ratio", ratio >= WCAG_NON_TEXT_FLOOR)
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

    /**
     * A pair where neither side is text — two fills whose difference *is* the
     * information, so 1.4.11's 3:1 rather than the text floor.
     *
     * Distinct from the bullet in this file's KDoc about not holding a fill
     * against the page. That rule is about a ground and the surface behind it,
     * where the contrast owed is to the ground's own contents. Here both sides
     * are grounds and the reader's question is which of the two a cell is.
     */
    fun graphic(what: String, foreground: Color, background: Color) = add(Pairing(what, foreground, background, WCAG_NON_TEXT_FLOOR))

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

    // secondaryContainer is a *ground*, not just an accent: RetroStrip fills
    // today's cell with it and HabitIcon uses it for a habit with no readable
    // colour, so content lands on top of it. Missing that is how a 4.24:1
    // weekday letter reached a device — the roles below are the ones actually
    // drawn there, and enumerating a container as a ground is the general
    // lesson. `outline` is deliberately absent: it borders cells, and after the
    // fix in RetroStrip nothing draws it on this fill.
    text("onSurface on secondaryContainer", onSurface, secondaryContainer)
    text("onSurfaceVariant on secondaryContainer", onSurfaceVariant, secondaryContainer)
    text("primary on secondaryContainer", primary, secondaryContainer)

    // The history grid's two cell fills (docs/ux/insights.md §7): `primary` for
    // a completed day, `surfaceContainerHighest` for one that is not. Held as a
    // pair because the pair is what says which day is which — the numbers drawn
    // on them are `onPrimary on primary` and `onSurfaceVariant on
    // surfaceContainerHighest`, both already above.
    //
    // Deliberately not `surfaceContainerHighest` against `surface`. That is
    // 1.26 in light and 1.50 in dark, and it is meant to be: a month of quiet
    // cells is a calendar, and a month of 3:1 cells is a keypad. The day number
    // is what makes an unfilled cell readable against the page.
    //
    // Also why the grid marks today with a ring rather than the filled
    // `secondaryContainer` ground `RetroStrip` uses. That fill against this one
    // measures 1.04 in light and 1.05 in dark, so a not-done today would be
    // indistinguishable from any other not-done day — the same failure §3's
    // published `tertiary` had at 1.02, caught here rather than on a device.
    graphic("primary against surfaceContainerHighest", primary, surfaceContainerHighest)

    // The large Today widget's woven day band (docs/ux/widget.md §7): one
    // segment per habit, `primary` when woven and `outlineVariant` when still
    // outstanding, with nothing drawn on either. The pair is the information,
    // so it is held the way the grid's two fills are. This is the one place
    // `outlineVariant` is held to anything — the KDoc above says why it is not
    // held against `surface`, and this does not change that.
    graphic("primary against outlineVariant", primary, outlineVariant)

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
        // Capitalised, because these four are among the tightest pairs in the
        // scheme and so the likeliest to actually print. "onprimaryFixed" names
        // no role and sends the reader looking for one.
        val onName = "on" + family.replaceFirstChar(Char::uppercaseChar)
        text("${onName}Fixed on ${family}Fixed", on, plain)
        text("${onName}Fixed on ${family}FixedDim", on, dim)
        text("${onName}FixedVariant on ${family}Fixed", onVariant, plain)
        text("${onName}FixedVariant on ${family}FixedDim", onVariant, dim)
    }
}
