package com.gawi.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The type scale is Outfit, on every role, and it is *only* the face.
 *
 * Two different mistakes are pinned here and they fail differently.
 *
 * **A role left behind.** docs/ux/visual-identity.md §5 names the ten roles the
 * app draws, and it would have been reasonable to set the family on only those.
 * [GawiTypography] covers all fifteen instead, because the five nobody draws yet
 * are exactly the ones a *future* screen would reach for, and a role left at the
 * default renders in Roboto silently — it looks like a design choice rather than
 * a gap. Iterating all fifteen is what makes that non-silent.
 *
 * **A scale retuned while the KDoc still claims it was not.** Type.kt says only
 * the face changed and that the metrics are Material's untouched, on the
 * reasoning that the sizes are the one part already validated on a device.
 * That is a claim about the code, so it is asserted against a fresh
 * `Typography()` rather than trusted. Tuning `letterSpacing` for Outfit is a
 * likely next change and a legitimate one — this test is what makes it a
 * deliberate edit with its KDoc updated, instead of a quiet drift.
 */
class GawiTypographyTest {

    /**
     * Named accessors rather than a reflective sweep, so a failure says which
     * role broke. The list being all fifteen is the point of the first test.
     */
    private val roles: List<Pair<String, (Typography) -> TextStyle>> = listOf(
        "displayLarge" to { t: Typography -> t.displayLarge },
        "displayMedium" to { t: Typography -> t.displayMedium },
        "displaySmall" to { t: Typography -> t.displaySmall },
        "headlineLarge" to { t: Typography -> t.headlineLarge },
        "headlineMedium" to { t: Typography -> t.headlineMedium },
        "headlineSmall" to { t: Typography -> t.headlineSmall },
        "titleLarge" to { t: Typography -> t.titleLarge },
        "titleMedium" to { t: Typography -> t.titleMedium },
        "titleSmall" to { t: Typography -> t.titleSmall },
        "bodyLarge" to { t: Typography -> t.bodyLarge },
        "bodyMedium" to { t: Typography -> t.bodyMedium },
        "bodySmall" to { t: Typography -> t.bodySmall },
        "labelLarge" to { t: Typography -> t.labelLarge },
        "labelMedium" to { t: Typography -> t.labelMedium },
        "labelSmall" to { t: Typography -> t.labelSmall },
    )

    @Test
    fun `every role draws in Outfit, including the five nothing draws yet`() {
        assertEquals("Material 3 has fifteen roles; this list must cover them", 15, roles.size)
        roles.forEach { (name, role) ->
            assertSame("$name is not drawn in Outfit", Outfit, role(GawiTypography).fontFamily)
        }
    }

    @Test
    fun `only the face changed - every metric is still Material's`() {
        val stock = Typography()
        roles.forEach { (name, role) ->
            val ours = role(GawiTypography)
            val theirs = role(stock)
            assertEquals("$name fontSize moved", theirs.fontSize, ours.fontSize)
            assertEquals("$name lineHeight moved", theirs.lineHeight, ours.lineHeight)
            assertEquals("$name letterSpacing moved", theirs.letterSpacing, ours.letterSpacing)
            assertEquals("$name fontWeight moved", theirs.fontWeight, ours.fontWeight)
        }
    }

    /**
     * The asset itself, because a font that is not a font fails at *render* time
     * and looks exactly like the face not having been applied. A saved error
     * page with a `.ttf` extension is the specific mistake in mind; it happened
     * to be worth checking during the widget experiment and it is worth
     * checking here permanently.
     *
     * Read as a file rather than a resource because this module's tests are
     * plain JVM by design (see `core/ui/build.gradle.kts`) and pulling in
     * Robolectric to open one byte stream would be a poor trade. That makes the
     * path relative to the module directory, so a missing file **fails** rather
     * than being skipped.
     */
    @Test
    fun `the bundled font is a real sfnt file`() {
        val font = File("src/main/res/font/outfit.ttf")
        assertTrue(
            "expected the font at ${font.absolutePath} — if this path is wrong the test proves nothing",
            font.isFile,
        )
        val signature = font.inputStream().use { it.readNBytes(4) }.map { it.toInt() and 0xFF }
        // 0x00010000 is the TrueType outline version tag a variable .ttf carries.
        assertEquals(listOf(0x00, 0x01, 0x00, 0x00), signature)
        assertTrue("a whole variable face should not be ${font.length()} bytes", font.length() > 50_000)
    }

    /**
     * The weights the app can *request*, which is not the set it writes.
     *
     * Material's fifteen roles ask for W400 and W500 and no source sets a weight
     * by hand, so two entries look sufficient and are not. Compose installs an
     * `AndroidFontResolveInterceptor` holding `Configuration.fontWeightAdjustment`
     * and adds it to every request, so with the system's *Bold text* setting on
     * (+300, API 31+) every role asks for W700 or W800. Unregistered, those fall
     * to the nearest entry plus platform synthesis — fake bold drawn over a real
     * bold that is already inside the same file. Nothing about that is visible
     * without turning the setting on, which is why it is pinned here.
     */
    @Test
    fun `the weights Bold text can request have real instances, not synthesis`() {
        @Suppress("UNCHECKED_CAST")
        val registered = (Outfit as List<Font>).map { it.weight }.toSet()
        assertTrue(
            "W700 is unregistered, so Bold text gets synthesis over a real bold",
            FontWeight.Bold in registered,
        )
        assertTrue(
            "W800 is unregistered, so Bold text gets synthesis over a real bold",
            FontWeight.ExtraBold in registered,
        )
        assertEquals(
            "OutfitWeights must describe the family it claims to document",
            OutfitWeights.toSet(),
            registered,
        )
    }

    /**
     * Every entry names the `wght` axis explicitly, and that is not decoration.
     *
     * `Font(resId, weight, …)`'s default for `variationSettings` reads, in the
     * decompiled bytecode, as though it already derives
     * `FontVariation.Settings(weight, style)` — so the explicit argument in
     * Type.kt looks redundant, and a code review argued from that bytecode that
     * it should go. On a device it is not redundant: removing it renders the whole
     * app at the font's `fvar` default of 100, "Outfit Thin", measured and
     * reproduced on 2026-08-24.
     *
     * A rendering property cannot be asserted from a JVM test, so this asserts
     * the input to it instead — that each entry carries a `wght` setting equal to
     * its own declared weight. That is enough to make the deletion red, which is
     * the point: the failure it prevents is a uniformly thin app that no test
     * would otherwise notice and that looks like a font choice.
     */
    @Test
    fun `every entry names the wght axis, matching its declared weight`() {
        @Suppress("UNCHECKED_CAST")
        val fonts = (Outfit as List<Font>).map { it as ResourceFont }
        assertEquals("the family should hold one entry per documented weight", OutfitWeights.size, fonts.size)
        fonts.forEach { font ->
            val axes = font.variationSettings.settings
            assertEquals(
                "the ${font.weight} entry must name exactly the wght axis",
                listOf("wght"),
                axes.map { it.axisName },
            )
            assertEquals(
                "the ${font.weight} entry names a wght that is not its weight",
                font.weight.weight.toFloat(),
                axes.single().toVariationValue(Density(1f)),
                0.01f,
            )
        }
    }
}
