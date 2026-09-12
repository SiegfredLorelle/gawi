package com.gawi.app.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.text.Spanned
import android.text.style.TtsSpan
import com.gawi.app.R
import com.gawi.core.data.reminder.OutstandingHabit
import com.gawi.core.data.reminder.ReminderDecision
import com.gawi.core.domain.testing.habitId
import com.gawi.core.testing.FIXED_DATE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * What the reminder puts in the shade: PRD §8's OQ-2 cap, and the two rules
 * docs/ux/reminder.md §4 states about a re-post.
 *
 * Robolectric's `NotificationManager` keeps what was posted, which is as close
 * to the shade as a JVM test reaches. §6's *"no test proves a notification
 * reaches the shade"* still stands for the platform's side of that line — what
 * is asserted here is what this class asked for.
 */
@RunWith(RobolectricTestRunner::class)
class ReminderNotifierTest {

    private lateinit var context: Application
    private lateinit var notifier: ReminderNotifier

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        notifier = ReminderNotifier(context)
        // Robolectric denies every runtime permission until it is granted, and
        // the notifier's first statement refuses without this one. Granting it
        // in setUp is what makes every test below about the notification rather
        // than about the guard; `a re-post is refused when notifications are
        // off` takes the guard away again on purpose.
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun remind(count: Int) = ReminderDecision.Remind(
        logicalDate = FIXED_DATE,
        outstanding = (1..count).map { OutstandingHabit(habitId(it), "habit $it") },
        total = count,
    )

    private fun posted(): Notification? = shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .allNotifications
        .singleOrNull()

    @Test
    fun `one habit left gets one button`() {
        notifier.post(remind(1))

        assertEquals(1, posted()?.actions?.size)
    }

    /** Android draws three, so three is where a button per habit stops. */
    @Test
    fun `three habits left get three buttons`() {
        notifier.post(remind(MAX))

        assertEquals(MAX, posted()?.actions?.size)
    }

    /**
     * PRD §8, OQ-2: four or more and the buttons go entirely rather than three of
     * them being chosen. The notification is still posted — it is the count that
     * matters then, and the tap still opens Today.
     */
    @Test
    fun `four habits left get no buttons at all`() {
        notifier.post(remind(MAX + 1))

        assertNull(posted()?.actions)
        assertNotNull(posted())
    }

    /** The name alone: the button's position is the verb, and a prefix truncates sooner. */
    @Test
    fun `the button is labelled with the habit's name`() {
        notifier.post(remind(1))

        assertEquals("habit 1", posted()?.actions?.single()?.title.toString())
    }

    /**
     * Read alone, a habit name is an instruction rather than a habit. A
     * notification action has no content description, so the spoken form travels
     * inside the title as a [TtsSpan].
     *
     * This proves the span is *set*. Whether the shade keeps it is the device
     * check in docs/running.md §4 — nothing on the JVM can answer that.
     */
    @Test
    fun `the button speaks what pressing it does`() {
        notifier.post(remind(1))

        val title = posted()?.actions?.single()?.title as Spanned
        val span = title.getSpans(0, title.length, TtsSpan::class.java).single()

        assertEquals(
            context.getString(R.string.reminder_action_complete, "habit 1"),
            span.args.getString(TtsSpan.ARG_TEXT),
        )
    }

    /**
     * What one button carries, read back the way the receiver reads it.
     *
     * The date is the notification's, not today's: a reminder outlives its own
     * day, so this is the value that stops a breakfast tap writing to the wrong
     * one (docs/ux/reminder.md §4).
     */
    @Test
    fun `the button carries its habit, its date and what is left`() {
        notifier.post(remind(2))

        val request = requestFrom(intentBehind(0))!!

        assertEquals(habitId(1).value, request.habitId)
        assertEquals(FIXED_DATE, request.logicalDate)
        assertEquals(listOf(habitId(1), habitId(2)), request.outstanding.map { it.id })
        assertEquals(2, request.total)
    }

    /**
     * Each button writes its own habit, and that is not free.
     *
     * A `PendingIntent`'s identity ignores extras, so three intents differing
     * only in theirs collapse into one and every button completes whatever the
     * first one carried. Distinct `data` Uris are what prevent it, and this is
     * the test that fails if they go.
     */
    @Test
    fun `the three buttons carry three different habits`() {
        notifier.post(remind(MAX))

        val tapped = (0 until MAX).map { requestFrom(intentBehind(it))!!.habitId }

        assertEquals((1..MAX).map { habitId(it).value }, tapped)
    }

    /**
     * The first post of the evening alerts.
     *
     * Stated as its own test because the flag it asserts the *absence* of is one
     * a reader would reasonably set unconditionally — and doing so would silence
     * tomorrow's reminder whenever today's was still in the shade.
     */
    @Test
    fun `the first post is allowed to make a sound`() {
        notifier.post(remind(2))

        assertEquals(0, posted()!!.flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    /** One tap must not become a second alert; it replaces the content silently. */
    @Test
    fun `a re-post replaces without alerting again`() {
        notifier.post(remind(2))
        notifier.repost(remind(1))

        val notification = posted()!!
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(1, notification.actions.size)
    }

    /** The fixed id is what makes a re-post replace rather than stack. */
    @Test
    fun `a re-post leaves one notification, not two`() {
        notifier.post(remind(2))
        notifier.repost(remind(1))

        assertNotNull(posted())
    }

    /**
     * docs/ux/reminder.md §4: a re-post *"goes through the same door"* as a
     * post, so the permission is honoured for free rather than by a second check
     * that could drift.
     */
    @Test
    fun `a re-post is refused when notifications are off`() {
        notifier.post(remind(2))
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(notifier.repost(remind(1)))
        // What is on screen, not merely that nothing new arrived: with nothing
        // posted first, an empty shade would hold whether or not the guard ran.
        assertEquals(2, posted()!!.actions.size)
    }

    @Test
    fun `cancelling takes it down`() {
        notifier.post(remind(1))
        notifier.cancel()

        assertNull(posted())
    }

    /** The intent a tap on the [index]th button would broadcast. */
    private fun intentBehind(index: Int): Intent = shadowOf(posted()!!.actions[index].actionIntent).savedIntent

    private companion object {
        const val MAX = 3
    }
}
