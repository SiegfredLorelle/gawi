package com.gawi.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.gawi.app.MainActivity
import com.gawi.app.R
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
 * **No action buttons.** Quick-complete is PRD §4's stretch goal and it stays one:
 * §6.1.1's *"logging < 5 seconds"* is already satisfied by the widget, and OQ-2
 * (what to show when more than three habits remain, against Android's three-button
 * cap) is unanswered. Adding buttons here would be a second path to a solved
 * problem carrying an open question with it.
 */
internal class ReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    /**
     * Posts the reminder, creating the channel if it is not there yet.
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
    fun post(remind: ReminderDecision.Remind): Boolean {
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

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body, remind.outstanding, remind.total))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // A nudge, not an alarm. The channel's importance decides whether it
            // makes a sound; this decides that it is not a heads-up interruption
            // on the versions that still read it.
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(REMINDER_NOTIFICATION_ID, notification)
        return true
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
    }
}
