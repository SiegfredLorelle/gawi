package com.gawi.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The manifest entry point for [MomoWidget]. Its own receiver for the reason
 * [StreakWidgetReceiver] gives: one provider per receiver, one picker entry
 * per receiver, which is the per-widget cost docs/ux/visual-identity.md §7.4
 * prices. Named fully qualified in `AndroidManifest.xml`.
 */
internal class MomoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MomoWidget()
}
