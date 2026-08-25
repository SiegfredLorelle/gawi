package com.gawi.widget.testsupport

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Pixel readings for the two rasterisers' tests. Only meaningful under
 * `GraphicsMode.NATIVE`; in Robolectric's default LEGACY mode a canvas paints
 * nothing and every count here is zero.
 */
fun Bitmap.pixels(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

/** Pixels with any alpha at all. */
fun Bitmap.inkedPixels(): Int = pixels().count { Color.alpha(it) > 0 }

/** Alpha-weighted ink, so a heavier weight measures heavier even where both cover the same pixels. */
fun Bitmap.ink(): Long = pixels().sumOf { Color.alpha(it).toLong() }
