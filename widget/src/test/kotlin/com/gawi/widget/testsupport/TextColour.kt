package com.gawi.widget.testsupport

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.BackgroundModifier
import androidx.glance.EmittableImage
import androidx.glance.TintColorFilterParams
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.unit.ColorProvider
import com.gawi.core.ui.theme.WCAG_TEXT_FLOOR
import com.gawi.core.ui.theme.contrastRatio
import com.gawi.widget.WidgetPalette

/*
 * Matchers for "is what this widget draws legible on the ground it draws it on".
 *
 * Shared by WidgetTextColourTest and StreakTextColourTest. Extracted here when
 * the second provider arrived rather than copied, for the reason contrastRatio's
 * own KDoc gives about itself: a second copy is how the two would drift apart
 * while both stayed green. Nothing here reads a checkbox glyph: its colour sits
 * behind an `internal` Glance accessor, so the glyph is measured at the palette
 * in `WidgetPaletteTest` and on a device (docs/running.md §4).
 */

/**
 * The tint an `OutfitText` carries, or null for anything that is not one — which
 * includes Momo's still frame, untinted by design because she carries her own
 * palette. `WidgetMomoTest` counts her the other way round.
 */
fun Any.tint(): ColorProvider? = ((this as? EmittableImage)?.colorFilterParams as? TintColorFilterParams)?.colorProvider

/**
 * The two tints that are not text: the woven day band's masks (`BandBitmap`),
 * which are tinted images like a string but are fills, owe no 4.5:1 to the
 * surface, and are held to their own pair floor in `WidgetPaletteTest`. Named
 * here so a text count cannot be inflated by them and a text floor cannot be
 * failed by them.
 */
private val bandTints get() = listOf(WidgetPalette.bandWoven, WidgetPalette.bandOutstanding)

/**
 * The tint of a rasterised string, or null for anything that is not one — Momo,
 * and the band's masks. Identity, not equality: two day/night providers built
 * from the same role are *equal*, so `bandWoven == streakDays`, and an `in`
 * check here silently dropped every `primary` string from the streak tests.
 */
fun Any.textTint(): ColorProvider? = tint()?.takeUnless { tint -> bandTints.any { it === tint } }

/** Anything drawing tinted ink as text, which for these widgets means any rasterised string. */
fun anyText() = GlanceNodeMatcher<MappedNode>("draws text") { node -> node.value.emittable.textTint() != null }

/** Ink below the WCAG floor against [background]. */
fun illegibleText(context: Context, background: Color) =
    GlanceNodeMatcher<MappedNode>("draws text below $WCAG_TEXT_FLOOR:1 against the widget background") { node ->
        val tint = node.value.emittable.textTint()
        tint != null && contrastRatio(tint.getColor(context), background) < WCAG_TEXT_FLOOR
    }

/**
 * The node whose background is [provider] — matched by **identity**, so it cannot
 * be satisfied by a different colour that happens to resolve the same way in the
 * theme a given subclass runs in.
 *
 * Asserting this before measuring anything is load-bearing rather than defensive.
 * An earlier version of the Today widget's test captured the ground inside the
 * composition and only checked it was not `Color.Unspecified` — which is
 * `Color(0)`, pure black, so a composition that never ran left every
 * light-on-dark assertion passing at about 16:1 having measured nothing.
 */
fun drawnOn(provider: ColorProvider) = GlanceNodeMatcher<MappedNode>("is drawn on the given colour provider") { node ->
    node.value.emittable.modifier.foldIn<BackgroundModifier.Color?>(null) { found, element ->
        found ?: element as? BackgroundModifier.Color
    }?.colorProvider === provider
}

/**
 * A rendered tree's context, and the ground that was actually drawn on it.
 *
 * Immutable and constructed after the render, so there is no window in which a
 * caller can measure against an unset background — the `lateinit`/`Unspecified`
 * shape this replaced could yield `Color(0)`, pure black, and pass every
 * light-on-dark assertion having measured nothing.
 */
class RenderProbe(val context: Context, val background: Color)
