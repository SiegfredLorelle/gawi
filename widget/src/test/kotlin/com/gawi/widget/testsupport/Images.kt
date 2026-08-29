package com.gawi.widget.testsupport

import android.graphics.Bitmap
import androidx.glance.BackgroundModifier
import androidx.glance.BitmapImageProvider
import androidx.glance.Emittable
import androidx.glance.EmittableImage
import androidx.glance.semantics.SemanticsModifier
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.unit.ColorProvider

/*
 * Matchers over the images a widget tree holds, shared by WidgetMomoTest and
 * MomoBodyTest rather than copied — review found the copies.
 */

/** An image with no colour filter — Momo, who carries her own palette. */
fun untintedImage() = GlanceNodeMatcher<MappedNode>("is an untinted image") { node ->
    (node.value.emittable as? EmittableImage)?.let { it.colorFilterParams == null } == true
}

/** Whether an emittable's modifier chain carries any semantics — a description. */
fun Emittable.isDescribed(): Boolean = modifier.foldIn(false) { described, element -> described || element is SemanticsModifier }

/** The same, carrying no description: decorative. Glance's harness has no negative assertion, so a matcher. */
fun silentUntintedImage() = GlanceNodeMatcher<MappedNode>("is an untinted image with no description") { node ->
    (node.value.emittable as? EmittableImage)?.let { it.colorFilterParams == null && !it.isDescribed() } == true
}

/** A tinted string carrying a description — the one that is read. */
fun describedText() = GlanceNodeMatcher<MappedNode>("is a described text") { node ->
    node.value.emittable.let { it.tint() != null && it.isDescribed() }
}

/** An image tinted by exactly [provider], matched by identity. */
fun tintedWith(provider: ColorProvider) = GlanceNodeMatcher<MappedNode>("is an image tinted with the given provider") { node ->
    node.value.emittable.tint() === provider
}

/** The colour provider a node's background is drawn with, or null. */
fun Emittable.ground(): ColorProvider? = modifier
    .foldIn<BackgroundModifier.Color?>(null) { found, element -> found ?: element as? BackgroundModifier.Color }
    ?.colorProvider

/** The bitmap behind an image node, if it is one. */
fun Emittable.bitmap(): Bitmap? = ((this as? EmittableImage)?.provider as? BitmapImageProvider)?.bitmap
