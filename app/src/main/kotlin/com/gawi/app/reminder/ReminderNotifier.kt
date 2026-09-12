package com.gawi.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TtsSpan
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.gawi.app.MainActivity
import com.gawi.app.R
import com.gawi.core.data.reminder.OutstandingHabit
import com.gawi.core.data.reminder.ReminderDecision
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The channel the reminder posts on. Stable: renaming it orphans the user's settings. */
internal const val REMINDER_CHANNEL_ID = "end_of_day_reminder"

/**
 * Puts a [ReminderDecision.Remind] on screen.
 *
 * In `:app` because it names [MainActivity] — the notification opens the app, and
 * `:app` is the only module that knows what the app's one activity is
 * (architecture §2). It holds no rule of its own: what to say is
 * [ReminderDecision]'s answer and when to say it is the worker's.
 *
 * **A fixed notification id, so the reminder replaces rather than stacks.** There
 * is one reminder per day (PRD §6.1.5) and nothing to distinguish two of them, so
 * a unique id per post would only be a way for a duplicate to become visible as
 * two rows instead of one.
 *
 * **Up to three action buttons, one per habit still outstanding, and none at
 * four or more** (PRD §8 OQ-2). The cap is Android's — the shade draws three —
 * and the fallback is no buttons rather than a chosen three, because there is no
 * non-arbitrary way to pick and any rule that did would be the app deciding
 * something about somebody's day in silence. The tap still reaches Today, which
 * shows all of them.
 *
 * **The notification id is private to this class, and that is load-bearing.** It
 * is what makes [post], [repost] and [cancel] the only three doors, so the
 * permission guard cannot be walked around by a caller holding an id.
 */
internal class ReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Posts the evening's reminder. The first post of the day, so it alerts.
     *
     * **Returns whether anything was shown, and the caller does not have to
     * care.** By the time this is reached the reminder has already been recorded
     * as posted, and un-recording it would only produce a second attempt the user
     * is equally unable to see — so the boolean exists to be logged, not branched
     * on.
     *
     * **Two checks decide that, and they are not redundant.**
     * `areNotificationsEnabled` is the semantic one, and the only one correct on
     * every API level: below 33 there is no runtime permission at all, but a user
     * can still switch this app's notifications off, and that switch is what
     * decides whether the reminder is seen. It is the same read the settings row
     * uses, which is what stops the row's promise and this class's behaviour from
     * drifting apart. `checkSelfPermission` is the one **Android Lint** requires:
     * `notify` is annotated `@RequiresPermission(POST_NOTIFICATIONS)` and lint will
     * not accept the other call in its place, `MissingPermission` is an error, and
     * `warningsAsErrors` makes that a failed build. It also names the permission
     * the manifest declares, which the other one does not.
     *
     * Channel creation is idempotent — the platform ignores a repeat for an id it
     * already has, and deliberately ignores a *changed* importance too, which is
     * why this is safe to call on every post rather than needing a one-time hook
     * in `Application.onCreate`.
     */
    fun post(remind: ReminderDecision.Remind): Boolean = show(remind, onlyAlertOnce = false)

    /**
     * Replaces what is on screen after a quick-complete tap, silently.
     *
     * **Silent because it is an acknowledgement, not a nudge.** One tap becoming
     * a second sound is wrong on its own, and worse for a user who has just
     * turned the reminder off and would be sounded at for completing a habit.
     *
     * The count and the buttons are the same fact twice, so both move together
     * or the shade starts disagreeing with itself while the user is looking at
     * it. The fixed id is what makes that cheap.
     *
     * **It stamps nothing.** Going through `ReminderCheck.evaluate` would record
     * the logical date as reminded, so a tap after the day cutoff would silence
     * that evening's real reminder — docs/ux/reminder.md §1's late-wake case
     * reached from a new direction. The permission is still honoured, because
     * [show] is the only door.
     */
    fun repost(remind: ReminderDecision.Remind): Boolean = show(remind, onlyAlertOnce = true)

    /**
     * Takes the reminder down, for when the last outstanding habit is completed
     * from the shade: there is nothing left for it to say.
     *
     * Not guarded by the permission check the other two share. Cancelling an id
     * that was never posted is a no-op, and refusing to cancel because
     * notifications are *now* off would strand one posted while they were on.
     */
    fun cancel() {
        NotificationManagerCompat.from(context).cancel(REMINDER_NOTIFICATION_ID)
    }

    /**
     * The one door: both guards, the channel, the builder and the `notify`.
     *
     * [post] and [repost] cannot each have their own copy of this. Lint's flow
     * analysis only accepts a `checkSelfPermission` it can see in the same method
     * as the guarded `notify`, so splitting the guard out would fail the build
     * whichever half kept it — and one body is also what makes "a re-post goes
     * through the same door" true rather than merely intended.
     *
     * [onlyAlertOnce] is a parameter rather than a fixed `true`, and that is the
     * trap in this file. The flag suppresses the sound whenever a notification
     * with this id is *already showing* — so setting it unconditionally would
     * also silence tomorrow evening's first reminder whenever today's is still
     * sitting unread in the shade, `setAutoCancel` clearing it only on a tap.
     * What is wanted is narrower: the first post alerts, a re-post replaces its
     * content silently.
     */
    private fun show(remind: ReminderDecision.Remind, onlyAlertOnce: Boolean): Boolean {
        val manager = NotificationManagerCompat.from(context)
        // One condition rather than two guards, and inline rather than in a
        // helper. Both shapes are forced: lint's flow analysis only accepts a
        // permission check it can see in the same method as the guarded call, and
        // detekt's ReturnCount allows two returns per function, which a second
        // guard clause would exceed.
        if ((
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) ||
            !manager.areNotificationsEnabled()
        ) {
            return false
        }

        manager.createNotificationChannel(channel())

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body, remind.outstanding.size, remind.total))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // A nudge, not an alarm. The channel's importance decides whether it
            // makes a sound; this decides that it is not a heads-up interruption
            // on the versions that still read it.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(onlyAlertOnce)

        for (action in actions(remind)) builder.addAction(action)

        manager.notify(REMINDER_NOTIFICATION_ID, builder.build())
        return true
    }

    /**
     * A button per outstanding habit, or none at all above [MAX_ACTIONS].
     *
     * PRD §8's OQ-2 in one expression. Four or more left and the list is empty
     * rather than truncated: the shade draws three, and choosing which three
     * would need a rule — most at risk, longest streak, alphabetical — that the
     * app has no honest basis for. Falling back to no buttons is not a
     * degradation; the tap still opens Today, which shows every one of them.
     */
    private fun actions(remind: ReminderDecision.Remind): List<NotificationCompat.Action> = if (remind.outstanding.size > MAX_ACTIONS) {
        emptyList()
    } else {
        remind.outstanding.map { habit ->
            // Icon 0, meaning none: since API 24 the phone's notification
            // template does not draw action icons at all, and inventing one
            // would be a drawable nothing renders.
            NotificationCompat.Action.Builder(0, spoken(habit.name), complete(habit, remind)).build()
        }
    }

    /**
     * The habit's name to read, and what pressing the button does to hear.
     *
     * **The button is labelled with the name and nothing else.** Its position is
     * the verb — an action row under a reminder is not read as a list — and a
     * *"Done: "* prefix would only make a long name truncate sooner.
     *
     * What it *speaks* cannot be the bare name, though: read alone, *"Read"* is
     * an instruction rather than a habit. A notification action has no content
     * description — `NotificationCompat.Action.Builder` exposes none, and the
     * title is both what is drawn and what a screen reader announces — so the
     * split is made inside the title itself with a [TtsSpan], which is a
     * `ParcelableSpan` and survives the trip to the shade.
     */
    private fun spoken(name: String): CharSequence = SpannableString(name).apply {
        setSpan(
            TtsSpan.TextBuilder(context.getString(R.string.reminder_action_complete, name)).build(),
            0,
            length,
            Spanned.SPAN_INCLUSIVE_EXCLUSIVE,
        )
    }

    /**
     * The tap: complete this habit, for the date the notification was posted for.
     *
     * **Its own `data` Uri, and that is not decoration.** A `PendingIntent`'s
     * identity ignores extras (`Intent.filterEquals`), so three buttons whose
     * intents differed only in their extras would collapse into one and every
     * button would write the same habit. The habit's id in the Uri is what keeps
     * them distinct; `FLAG_UPDATE_CURRENT` is what lets a re-post rewrite the
     * remaining list carried by the buttons that survive.
     *
     * **The whole outstanding list travels with each button**, not just the
     * habit tapped. The receiver has to say what is left afterwards, and it
     * cannot ask: `observeToday` answers for the *current* logical date, and this
     * notification may be tapped on a later one. Carrying the answer is what
     * keeps a tap free of a clock.
     */
    private fun complete(habit: OutstandingHabit, remind: ReminderDecision.Remind): PendingIntent {
        val intent = Intent(context, QuickCompleteReceiver::class.java)
            .setData("$COMPLETE_URI${habit.id.value}".toUri())
            .putExtra(EXTRA_HABIT_ID, habit.id.value)
            .putExtra(EXTRA_DATE, remind.logicalDate.toString())
            .putExtra(EXTRA_TOTAL, remind.total)
            .putExtra(EXTRA_IDS, remind.outstanding.map { it.id.value }.toTypedArray())
            .putExtra(EXTRA_NAMES, remind.outstanding.map { it.name }.toTypedArray())

        return PendingIntent.getBroadcast(
            context,
            REMINDER_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * `IMPORTANCE_DEFAULT`, which makes a sound.
     *
     * A habit nudge that arrives silently in the shade is one the user finds
     * tomorrow morning, which is the whole point missed. `IMPORTANCE_HIGH` would
     * be a full-screen-adjacent interruption for something that is not urgent.
     * The user can change either in the channel's own settings, which is where
     * that choice belongs.
     */
    private fun channel() = NotificationChannel(
        REMINDER_CHANNEL_ID,
        context.getString(R.string.reminder_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply { description = context.getString(R.string.reminder_channel_description) }

    /**
     * Opens the app at whatever it was last showing, which is Today by default.
     *
     * `FLAG_IMMUTABLE` because nothing fills anything in on this intent — the
     * launcher-equivalent flags are the whole payload, so a mutable
     * `PendingIntent` would hand a third party the ability to rewrite it for no
     * gain. `FLAG_UPDATE_CURRENT` so a re-post reuses the one PendingIntent
     * rather than leaving a stale one behind.
     *
     * Deliberately **not** a deep link. `:app` owns the navigation graph and
     * Today is already the start destination, so there is nothing for a route to
     * add; a deep link would only be a second way to express the same landing
     * place, which could then disagree with the graph.
     */
    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            context,
            REMINDER_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val REMINDER_NOTIFICATION_ID = 1

        /** Android's own: the shade draws three action buttons and no more. */
        const val MAX_ACTIONS = 3

        /**
         * Scheme and path of the per-button `data` Uri. Never resolved by
         * anything — the intent names its receiver explicitly, and this exists
         * only so the three `PendingIntent`s are not equal to one another.
         */
        const val COMPLETE_URI = "gawi://reminder/complete/"
    }
}
