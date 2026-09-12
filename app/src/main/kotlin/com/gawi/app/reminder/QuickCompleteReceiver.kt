package com.gawi.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gawi.core.data.reminder.OutstandingHabit
import com.gawi.core.data.reminder.ReminderDecision
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The habit the tapped button belongs to. A plain string; see [ReminderNotifier]. */
internal const val EXTRA_HABIT_ID = "com.gawi.app.reminder.HABIT_ID"

/** The logical date the notification was posted for, ISO-8601. */
internal const val EXTRA_DATE = "com.gawi.app.reminder.DATE"

/** Every non-archived habit, so the re-post can say *"2 of 5"* as the first post did. */
internal const val EXTRA_TOTAL = "com.gawi.app.reminder.TOTAL"

/** Ids of every habit outstanding when the notification was posted. */
internal const val EXTRA_IDS = "com.gawi.app.reminder.IDS"

/** Their names, positionally paired with [EXTRA_IDS]. */
internal const val EXTRA_NAMES = "com.gawi.app.reminder.NAMES"

private const val TAG = "QuickComplete"

/**
 * One tap on a reminder's action button: complete that habit for the date the
 * notification was posted for, then move the shade to match.
 *
 * **Registered `exported="false"`.** Nothing outside this app has any business
 * sending it, and the only sender is a `PendingIntent` this app built.
 *
 * `@AndroidEntryPoint` is deliberately not used, for the reason
 * [ReminderEntryPoint] gives about the workers beside it: one route into the
 * graph rather than two.
 */
internal class QuickCompleteReceiver : BroadcastReceiver() {

    /**
     * **The whole body is guarded.** A `BroadcastReceiver` has nothing between a
     * throw here and the thread's default handler, so an unguarded failure is
     * process death on a tap — the same reason `:widget`'s `ToggleHabitAction`
     * guards its own, and an `Error` walks past `catch (e: Exception)`.
     *
     * `goAsync` because the write is suspending and `onReceive` is not: without
     * it the process may be killed the moment this returns, mid-commit.
     *
     * Not unit-testable — it needs a real broadcast. [quickComplete] is the seam
     * that holds the decision, and [requestFrom] the one that holds the parsing.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val entryPoint = EntryPointAccessors.fromApplication(context, ReminderEntryPoint::class.java)
        val notifier = entryPoint.reminderNotifier()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val request = requestFrom(intent) ?: return@launch
                quickComplete(
                    habits = entryPoint.habitRepository(),
                    repost = { remind -> notifier.repost(remind) },
                    cancel = { notifier.cancel() },
                    request = request,
                )
            } catch (e: Throwable) {
                currentCoroutineContext().ensureActive()
                // Logged, not dropped. There is no snackbar behind a notification,
                // so without this a persistently failing button is
                // indistinguishable from one nobody is pressing.
                Log.w(TAG, "a quick-complete tap failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * What a tapped button carried: which habit, which date, and what the shade
 * should say afterwards.
 *
 * A parsed value rather than an `Intent`, so the decision below is tested
 * without Android.
 */
internal data class QuickCompleteRequest(
    val habitId: String,
    val logicalDate: LocalDate,
    val outstanding: List<OutstandingHabit>,
    val total: Int,
)

/**
 * Reads a tap's extras, or null if any of them is missing or malformed.
 *
 * Null rather than a throw or a default: every field is load-bearing — a
 * defaulted date would write to the wrong day, a defaulted list would re-post a
 * notification contradicting itself — so the honest answer to a broken intent is
 * to do nothing at all and leave what is on screen alone. An empty list is
 * malformed for the same reason: a notification with nothing outstanding has no
 * buttons to have been pressed.
 *
 * The two arrays are read as one list and only as far as the shorter of them,
 * because nothing downstream can tell a name from a missing name.
 */
internal fun requestFrom(intent: Intent): QuickCompleteRequest? {
    val habitId = intent.getStringExtra(EXTRA_HABIT_ID)
    val date = runCatching { LocalDate.parse(intent.getStringExtra(EXTRA_DATE)) }.getOrNull()
    val ids = intent.getStringArrayExtra(EXTRA_IDS) ?: emptyArray()
    val names = intent.getStringArrayExtra(EXTRA_NAMES) ?: emptyArray()
    val outstanding = ids.zip(names) { id, name -> OutstandingHabit(HabitId(id), name) }

    // One return rather than four guard clauses: detekt allows two per function,
    // and "all four or nothing" is what this is anyway.
    return if (habitId == null || date == null || outstanding.isEmpty()) {
        null
    } else {
        QuickCompleteRequest(habitId, date, outstanding, intent.getIntExtra(EXTRA_TOTAL, 0))
    }
}

/**
 * The whole of the tap's decision, as a function taking its collaborators so it
 * is tested without a broadcast or a device.
 *
 * **It completes; it never toggles.** `ToggleHabitAction` undoes an already-done
 * habit, which on a widget is a mis-tap the user can fix where it happened. Here
 * the row was drawn hours ago: a habit ticked in the app since then would be
 * *un*-ticked by a button still labelled with its name, in a surface the user is
 * not looking at. So this is an idempotent completion — pressing twice writes
 * what pressing once did, `Commands.addCompletion` accepting a duplicate by
 * design (architecture §4).
 *
 * **It writes to the carried date and reads no clock.** That is the correctness
 * rule docs/ux/reminder.md §4 exists for: a notification posted before the day
 * cutoff is still there at breakfast, and resolving *now* would put the
 * completion on today for a habit owed yesterday — which architecture §5's
 * three-day window accepts rather than refuses, so it would be silent.
 *
 * **What is left afterwards is carried, not recounted**, and that has a cost
 * worth stating: a habit completed in the app since the notification was posted
 * still shows a button, and pressing it re-adds a completion that is already
 * there. The alternative is worse — `HabitRepository.observeToday` answers for
 * the *current* logical date, so recounting a notification tapped the next
 * morning would need a second way to decide what is outstanding, beside
 * `Mascot.isOutstanding`, for a date nothing else asks about.
 *
 * **A refused write leaves the shade exactly as it is**, and that is the one
 * branch here that is not obvious. The carried date is days old once a
 * notification has outlived a long enough gap, and architecture §5's three-day
 * retroactive window is a *command* rule, so the domain refuses it. Dropping the
 * button anyway would tell the user the habit was logged when nothing was
 * written — the silent wrong answer this whole section exists to avoid. Leaving
 * the notification alone says the truthful thing instead: the button is still
 * there, and it still owes something.
 *
 * A `HabitId` is constructed from the carried string, where the widget's tap
 * deliberately avoids doing so. It can afford to: it has a snapshot to match the
 * id against. This does not, the value came from this app's own `PendingIntent`,
 * and the receiver's guard is what catches the `require` if it ever were
 * something else.
 */
internal suspend fun quickComplete(
    habits: HabitRepository,
    repost: (ReminderDecision.Remind) -> Unit,
    cancel: () -> Unit,
    request: QuickCompleteRequest,
) {
    val written = habits.addCompletion(HabitId(request.habitId), request.logicalDate)
    if (written is CommandResult.Rejected) {
        Log.w(TAG, "a quick-complete tap was refused: ${written.error}")
        return
    }

    val remaining = request.outstanding.filterNot { it.id.value == request.habitId }
    if (remaining.isEmpty()) {
        // Nothing left for it to say.
        cancel()
    } else {
        repost(
            ReminderDecision.Remind(
                logicalDate = request.logicalDate,
                outstanding = remaining,
                total = request.total,
            ),
        )
    }
}
