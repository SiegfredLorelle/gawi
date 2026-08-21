package com.gawi.app.reminder

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.gawi.core.data.reminder.ReminderCheck
import com.gawi.core.data.settings.SettingsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderScheduler"

/** The reminder's unique work name. Stable: changing it orphans whatever is already scheduled. */
private const val REMINDER_WORK = "gawi.reminder.end-of-day"

/** The day-rollover refresh's unique work name. */
private const val ROLLOVER_WORK = "gawi.reminder.day-rollover"

/**
 * When the two scheduled wakes happen — the whole of `:app`'s side of
 * architecture §2's *"WorkManager scheduling for the end-of-day reminder"*.
 *
 * Two wakes, not one, and only one of them is about notifications:
 *
 * - **The reminder**, at the configured reminder time (PRD §4).
 * - **The rollover refresh**, at the day cutoff. A day boundary commits nothing,
 *   so no `ProjectionListener` push can fire for it and a widget left on a
 *   launcher shows yesterday's ticks (architecture §4, docs/ux/widget.md §4 and
 *   §6). This is the scheduled wake that section asks the reminder step to add.
 *
 * ## Why the two workers arm *each other*
 *
 * This is the one thing here that looks like a mistake and is not.
 * [ReminderWorker] arms the rollover, and [RolloverWorker] arms the reminder.
 * Neither ever re-enqueues its **own** unique name.
 *
 * `enqueueUniqueWork` with [ExistingWorkPolicy.REPLACE] cancels whatever is
 * already under that name — **including a run in progress**. So a worker that
 * re-armed itself would cancel itself every single time, leaving the tail of its
 * own `doWork` to run inside a cancelled coroutine and its completion to be
 * recorded as `CANCELLED`. Correct-looking and racy.
 *
 * Arming the other name has no such race, because the other name is provably not
 * running: the reminder falls strictly inside a logical day and the cutoff ends
 * it, so the two never coincide except when a user sets the reminder time exactly
 * equal to the cutoff — and [com.gawi.core.domain.time.reminderOn] resolves that
 * to the day's *start*, which leaves them a whole day apart rather than together.
 *
 * The chain therefore alternates: 21:00 arms midnight, midnight arms 21:00. If
 * either link is ever lost — a cancelled worker, a cleared app, a WorkManager
 * database migration — [start] re-arms both on the next process start, so the
 * chain has a repair path that does not depend on itself.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val check: ReminderCheck,
    private val settings: SettingsSource,
) {

    /**
     * Outlives every screen by design, and there is nothing that should cancel
     * it: the work it arms is meant to survive the process, not the Activity.
     * `SupervisorJob` so one failed arming does not take the collector down.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The application-scoped collector, and the only thing
     * [com.gawi.app.GawiApplication] has to call.
     *
     * **What it collects is the reason this exists at all.** A settings edit is
     * not an event — changing the day cutoff moves the logical date, and changing
     * the reminder time moves the threshold, and neither writes anything to the
     * log — so nothing can push either change at WorkManager. `SettingsSource`
     * already re-emits on both, which makes one collector the whole mechanism, and
     * it closes docs/ux/widget.md §6's *"a settings edit is not an event either"*
     * gap as a side effect rather than needing anything of its own.
     *
     * Deduped on the two fields that move a wake. The week start also lives in
     * `UserSettings` and moves neither, so reacting to it would re-enqueue both
     * works for nothing.
     */
    fun start() {
        scope.launch {
            var armed = false
            settings.observe()
                .map { it.dayCutoff to it.reminderTime }
                .distinctUntilChanged()
                .collect { _ ->
                    // The first emission is "what the settings already are", which
                    // is not an edit — so it must not disturb work that is already
                    // scheduled or, worse, running. KEEP arms only what is absent.
                    // Every later emission is a real edit, where replacing the
                    // pending wake with one at the new time is the entire point.
                    arm(if (armed) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
                    armed = true
                }
        }
    }

    /** Arms both wakes. Used on the first settings emission and on every edit. */
    private suspend fun arm(policy: ExistingWorkPolicy) {
        armReminder(policy)
        armRollover(policy)
    }

    /** Arms the next reminder. Called by [RolloverWorker], never by [ReminderWorker]. */
    suspend fun armReminder(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        enqueue(REMINDER_WORK, ReminderWorker::class.java, policy) { check.untilNextReminder() }
    }

    /** Arms the next rollover refresh. Called by [ReminderWorker], never by [RolloverWorker]. */
    suspend fun armRollover(policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        enqueue(ROLLOVER_WORK, RolloverWorker::class.java, policy) { check.untilNextCutoff() }
    }

    /**
     * One enqueue, with the rules that apply to both of them.
     *
     * **No constraints, and in particular never a network one.** `:app`'s manifest
     * removes `ACCESS_NETWORK_STATE` with `tools:node="remove"` because WorkManager
     * wants it only to evaluate network constraints and nothing here has one —
     * which is what keeps PRD §5's *"no network permission at MVP"* true. A
     * constraint added here would force that line out and falsify a headline claim
     * for a wake that needs no network. `ManifestPermissionTest` is the tripwire.
     *
     * **Not expedited and not an exact alarm.** Architecture §7 rules both out by
     * name: the reminder fires within WorkManager's flex window, and
     * `SCHEDULE_EXACT_ALARM` is deliberately avoided because a "habits left today"
     * nudge does not need exact delivery and the permission attracts Play-policy
     * scrutiny. `setExpedited` would additionally pull foreground-service
     * behaviour into a background nudge.
     *
     * **Every failure is absorbed and logged**, the same call
     * `GlanceProjectionListener` and `ExportJournal.record` make — and here it is
     * load-bearing rather than defensive, because the throw is real and measured:
     * `WorkManager.getInstance` raises `IllegalStateException` when the
     * `androidx.startup` initialiser has not run, which is exactly the shape of
     * `:app`'s Robolectric tests.
     *
     * The crash it prevents is worth describing precisely, because the obvious
     * description is wrong. This is reached from a coroutine [start] launched, so
     * `Application.onCreate` has already returned and nothing would unwind through
     * it. But an uncaught exception in a `launch` with no `CoroutineExceptionHandler`
     * is handed to the thread's default handler, which on Android **is** a process
     * crash — just an asynchronous one, moments after launch, with a stack trace
     * pointing at a scheduler rather than at anything the user did. Absorbing it
     * costs one late wake, which the next process start repairs.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun enqueue(
        name: String,
        worker: Class<out ListenableWorker>,
        policy: ExistingWorkPolicy,
        delay: suspend () -> Duration,
    ) {
        try {
            val request = OneTimeWorkRequest.Builder(worker)
                // Coerced rather than trusted. Both durations are positive by
                // construction, but they are computed a moment before this runs
                // and WorkManager rejects a negative delay outright.
                .setInitialDelay(delay().coerceAtLeast(Duration.ZERO))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
        } catch (cause: Throwable) {
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "could not arm $name; the next process start will retry", cause)
        }
    }
}
