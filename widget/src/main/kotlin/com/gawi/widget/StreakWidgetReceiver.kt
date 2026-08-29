package com.gawi.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The manifest entry point for [StreakWidget].
 *
 * Its own receiver, because one `AppWidgetProvider` serves one
 * `appwidget-provider` and the launcher offers one entry per receiver — which is
 * the "each new widget is its own provider" cost docs/ux/visual-identity.md §7.4
 * prices. Named in `AndroidManifest.xml` fully qualified: a library manifest has
 * no package for `.StreakWidgetReceiver` to resolve against.
 */
internal class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
