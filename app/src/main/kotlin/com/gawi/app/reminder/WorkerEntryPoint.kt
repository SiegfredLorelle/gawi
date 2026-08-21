package com.gawi.app.reminder

import com.gawi.core.data.projection.ProjectionListener
import com.gawi.core.data.reminder.ReminderCheck
import com.gawi.core.data.repository.HabitRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * How a worker reaches the graph.
 *
 * The same seam, and the same argument, as `:widget`'s `WidgetEntryPoint`: a
 * `ListenableWorker` is constructed by WorkManager's own factory, not by Hilt, so
 * it is not an injection site and `@AndroidEntryPoint` cannot help. An entry
 * point resolved off the application is the supported way in, and it is the same
 * singleton graph the app uses — which matters for the same reason it does there:
 * `OfflineFirstHabitRepository` owns the command mutex and the in-memory
 * projection, so a second instance would be a second command authority
 * disagreeing in silence.
 *
 * **Deliberately not `androidx.hilt:hilt-work`.** `@HiltWorker` wants a
 * `HiltWorkerFactory` installed through a `Configuration.Provider` on the
 * `Application` — and that configuration governs **every** worker in the process,
 * including Glance's own `SessionWorker`, which is how the widget renders at all
 * (architecture §7). Taking over WorkManager's initialisation to inject two
 * classes would put the widget's rendering path behind a change made for the
 * reminder's convenience. This adds no dependency and leaves the default
 * `androidx.startup` initialisation exactly as the widget found it.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WorkerEntryPoint {

    fun reminderCheck(): ReminderCheck

    fun reminderNotifier(): ReminderNotifier

    fun reminderScheduler(): ReminderScheduler

    fun habitRepository(): HabitRepository

    /**
     * The rollover worker's reason for existing.
     *
     * `:core:data` declares this and `:widget` binds it, so resolving it here
     * reaches the Glance implementation without `:app` naming a `:widget` type —
     * the same route the `ProjectionListener` binding already takes to close the
     * graph.
     */
    fun projectionListener(): ProjectionListener
}
