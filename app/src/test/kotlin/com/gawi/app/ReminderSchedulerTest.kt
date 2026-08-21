package com.gawi.app

import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.gawi.app.reminder.ReminderScheduler
import com.gawi.core.data.reminder.ReminderCheck
import com.gawi.core.data.settings.SettingsSource
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalTime
import javax.inject.Inject

/**
 * That the two scheduled wakes are actually armed, and armed with the instants
 * `:core:data` computed.
 *
 * **This test exists because WorkManager is not initialised under Robolectric.**
 * `WorkManager.getInstance` throws `IllegalStateException` there — its
 * `androidx.startup` provider does not run — which was measured, not assumed, and
 * has two consequences worth stating together. First, `ReminderScheduler`'s
 * `Throwable`-absorbing guard is load-bearing rather than defensive: it is the
 * reason `AppNavigationTest` and `AppSmokeTest` still pass with the scheduler
 * wired into `GawiApplication.onCreate`. Second, that same guard means a
 * *completely broken* scheduler would also pass those tests in silence, which is
 * exactly the failure shape `ProjectionListenerTest` was written to rule out for
 * the widget. `WorkManagerTestInitHelper` is what closes it here.
 *
 * What is deliberately **not** here: whether the reminder decides correctly.
 * That is `ReminderCheckTest` in `:core:data`, driven by a fake clock, which is
 * where PRD §6.1's criteria can be pinned without a framework at all. This test
 * only asks whether the wiring carries that decision to WorkManager intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
class ReminderSchedulerTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var check: ReminderCheck

    @Inject
    lateinit var settings: SettingsSource

    private val context get() = RuntimeEnvironment.getApplication()

    private val workManager get() = WorkManager.getInstance(context)

    @Before
    fun setUp() {
        hilt.inject()
        // SynchronousExecutor so an enqueue is observable on the calling thread.
        // The test driver never *runs* the work here — every assertion below is
        // about what was scheduled, not about what a worker did.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun stateOf(name: String): List<WorkInfo.State> = workManager.getWorkInfosForUniqueWork(name).get().map { it.state }

    private fun delayOf(name: String): Long = workManager.getWorkInfosForUniqueWork(name).get().single().initialDelayMillis

    @Test
    fun `arming the reminder enqueues it under its own name`() = runTest {
        scheduler.armReminder()

        assertEquals(listOf(WorkInfo.State.ENQUEUED), stateOf(REMINDER_WORK))
    }

    @Test
    fun `arming the rollover enqueues it under its own name`() = runTest {
        scheduler.armRollover()

        assertEquals(listOf(WorkInfo.State.ENQUEUED), stateOf(ROLLOVER_WORK))
    }

    /**
     * The two names are separate work, which is what makes the workers able to arm
     * each other without either cancelling itself (`ReminderScheduler`'s KDoc).
     * One unique name for both would make that scheme silently self-destructive.
     */
    @Test
    fun `the reminder and the rollover are separate work`() = runTest {
        scheduler.armReminder()
        scheduler.armRollover()

        assertEquals(listOf(WorkInfo.State.ENQUEUED), stateOf(REMINDER_WORK))
        assertEquals(listOf(WorkInfo.State.ENQUEUED), stateOf(ROLLOVER_WORK))
    }

    /**
     * The delay is `:core:data`'s answer, passed through rather than recomputed.
     *
     * Compared against [ReminderCheck.untilNextReminder] called from the test
     * rather than against a hand-computed instant, deliberately: what could break
     * here is the wiring — a forgotten `setInitialDelay`, which would enqueue work
     * that runs *now* — and a duplicate of the arithmetic in the assertion would
     * be a second place for the shift `reminderOn` applies to be got wrong.
     *
     * The tolerance is a second, because the clock moves between the two calls.
     */
    @Test
    fun `the reminder is armed for the reminder time and not for now`() = runTest {
        scheduler.armReminder()

        val expected = check.untilNextReminder().toMillis()
        val actual = delayOf(REMINDER_WORK)
        assertTrue("expected ~$expected ms, was $actual", Math.abs(expected - actual) < 1_000)
        assertTrue("a delay of $actual ms would fire immediately", actual > 0)
    }

    @Test
    fun `the rollover is armed for the day boundary and not for now`() = runTest {
        scheduler.armRollover()

        val expected = check.untilNextCutoff().toMillis()
        val actual = delayOf(ROLLOVER_WORK)
        assertTrue("expected ~$expected ms, was $actual", Math.abs(expected - actual) < 1_000)
        assertTrue("a delay of $actual ms would fire immediately", actual > 0)
    }

    /**
     * KEEP leaves a pending wake alone — the policy the first settings emission
     * uses, because "these are the settings" is not an edit and must not disturb
     * work that is already scheduled or running.
     */
    @Test
    fun `keep does not move a wake that is already armed`() = runTest {
        scheduler.armReminder()
        val armedFor = delayOf(REMINDER_WORK)

        settings.update { it.copy(reminderTime = LocalTime.of(6, 30)) }
        scheduler.armReminder(ExistingWorkPolicy.KEEP)

        assertEquals(armedFor, delayOf(REMINDER_WORK))
    }

    /**
     * REPLACE moves it — the policy a real settings edit uses, and the whole
     * reason the scheduler watches `SettingsSource` at all. A reminder time is not
     * an event, so nothing else could tell WorkManager it had moved.
     */
    @Test
    fun `replace moves the wake when the reminder time changes`() = runTest {
        settings.update { it.copy(reminderTime = LocalTime.of(23, 30)) }
        scheduler.armReminder()
        val before = delayOf(REMINDER_WORK)

        settings.update { it.copy(reminderTime = LocalTime.of(6, 30)) }
        scheduler.armReminder(ExistingWorkPolicy.REPLACE)

        assertTrue("the wake did not move: $before vs ${delayOf(REMINDER_WORK)}", before != delayOf(REMINDER_WORK))
    }

    private companion object {
        /**
         * Duplicated from `ReminderScheduler`, which keeps them private on
         * purpose: a unique work name is persisted, so a test that imported the
         * constant would keep passing if it were renamed, while every install
         * already in the field kept its wake under the old name. Spelling them out
         * here means a rename has to be a deliberate two-place change.
         */
        const val REMINDER_WORK = "gawi.reminder.end-of-day"
        const val ROLLOVER_WORK = "gawi.reminder.day-rollover"
    }
}
