package com.gawi.app

import android.app.Application
import com.gawi.app.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GawiApplication : Application() {

    /**
     * Field injection, because Hilt owns this class's construction.
     *
     * `lateinit var` rather than a constructor parameter: `@HiltAndroidApp`
     * generates the superclass that performs the injection, and it happens inside
     * `super.onCreate()` — which is why [ReminderScheduler.start] is called after
     * it and not before.
     */
    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    /**
     * Arms the two scheduled wakes (architecture §2, docs/ux/reminder.md).
     *
     * The only work `:app` does at startup, and it is deliberately not work: it
     * launches one collector on the scheduler's own scope and returns. Nothing is
     * read from disk on this thread, and a failure to reach WorkManager is
     * absorbed inside — an `Application.onCreate` that can throw is a launch that
     * can fail, and a late reminder is not worth that trade.
     *
     * Called on every process start, which is also the chain's repair path: if
     * either armed wake is ever lost, opening the app restores both.
     */
    override fun onCreate() {
        super.onCreate()
        reminderScheduler.start()
    }
}
