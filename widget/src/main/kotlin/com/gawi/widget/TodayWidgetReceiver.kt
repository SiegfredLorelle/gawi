package com.gawi.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The manifest's entry point to [TodayWidget].
 *
 * A `BroadcastReceiver`, so the framework instantiates it reflectively and it
 * must keep a no-arg constructor; `internal` here is a Kotlin visibility
 * statement and not a JVM one. It is also the app's **second exported
 * component**, which is not optional — an `AppWidgetProvider` the launcher
 * cannot bind is not a widget. See docs/ux/widget.md §5: it adds no permission,
 * carries no data in its intents, and every action it can receive is an
 * `AppWidgetManager` broadcast.
 */
internal class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
