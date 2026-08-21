package com.gawi.widget

import com.gawi.core.data.repository.HabitRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * How the widget reaches the graph.
 *
 * A `GlanceAppWidget` and an `ActionCallback` are constructed by the framework
 * and by Glance's own `PendingIntent` plumbing, not by Hilt, so neither is an
 * injection site and `@AndroidEntryPoint` cannot help. An entry point resolved
 * off the application is the supported way in, and it is the same singleton
 * graph the app uses — which matters more here than it looks:
 * `OfflineFirstHabitRepository` owns the command mutex and the in-memory
 * projection, so a second instance would be a second command authority
 * disagreeing in silence.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun habitRepository(): HabitRepository
}
