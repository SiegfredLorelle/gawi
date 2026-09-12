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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * One tap at a time, across every broadcast this receiver gets.
 *
 * File scope rather than a field, because the platform builds a fresh receiver
 * per broadcast and two taps must still queue behind one another. What it buys
 * is that [quickComplete]'s read of the day happens after the previous tap's
 * write, which is the half of the two-taps problem that ordering *can* fix;
 * `kotlinx`'s `Mutex` is FIFO, so the shade settles in the order the buttons
 * were pressed.
 */
private val tapLock = Mutex()

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
     * **Everything but `goAsync` is guarded, and the graph lookups are inside
     * on purpose.** A `BroadcastReceiver` has nothing between a throw here and
     * the thread's default handler, so an unguarded failure is process death on
     * a tap — the same reason `:widget`'s `ToggleHabitAction` guards its own,
     * and an `Error` walks past `catch (e: Exception)`. Resolving the entry
     * point is a real thrower rather than a formality: it is a Hilt provision,
     * and doing it above the `try` would both escape the guard and leak the
     * `PendingResult` this never gets to `finish()`.
     *
     * `goAsync` stays outside because it has to: it must run synchronously
     * before `onReceive` returns, and it is what keeps the process alive across
     * the suspending write that follows.
     *
     * Not unit-testable — it needs a real broadcast. [quickComplete] is the seam
     * that holds the decision, and [requestFrom] the one that holds the parsing.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(context, ReminderEntryPoint::class.java)
                val notifier = entryPoint.reminderNotifier()
                val request = requestFrom(intent) ?: return@launch
                tapLock.withLock {
                    quickComplete(
                        habits = entryPoint.habitRepository(),
                        repost = { remind -> notifier.repost(remind) },
                        cancel = { notifier.cancel() },
                        request = request,
                    )
                }
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
 * **[EXTRA_TOTAL] is checked too, not defaulted.** A total below the number of
 * habits carried would re-post *"1 of 0 left today"* — the body and the buttons
 * disagreeing, which is the one failure the shade must never show.
 *
 * **So is the tapped id**, and for the reason [outstandingFrom] gives about the
 * ones beside it: `HabitId` validates in its own `init`, so an unchecked one
 * would throw out of the first statement of [quickComplete] instead of reaching
 * the null promised here — and it would throw in a broadcast, where the only
 * thing between it and the default handler is the receiver's guard.
 */
internal fun requestFrom(intent: Intent): QuickCompleteRequest? {
    val habitId = intent.getStringExtra(EXTRA_HABIT_ID)?.takeIf { runCatching { HabitId(it) }.isSuccess }
    val date = runCatching { LocalDate.parse(intent.getStringExtra(EXTRA_DATE)) }.getOrNull()

    // One return rather than a guard clause each, and the malformed cases folded
    // into the values above rather than added here: detekt allows two returns
    // and three conditions per function, and "all of it or none of it" is what
    // this is anyway.
    return outstandingFrom(intent)?.let { habits ->
        val total = intent.getIntExtra(EXTRA_TOTAL, 0)
        if (habitId == null || date == null || total < habits.size) {
            null
        } else {
            QuickCompleteRequest(habitId, date, habits, total)
        }
    }
}

/**
 * The habits a button carried, or null if anything about them is wrong.
 *
 * **`HabitId` validates in its own `init`**, so building the list inline would
 * throw out of a broadcast on a malformed id rather than reaching the null
 * [requestFrom] documents — and it would throw while assembling the *re-post*,
 * so the completion the user actually asked for would be lost with it. Each id
 * is therefore tried rather than trusted, and one bad entry rejects the lot.
 *
 * The two arrays are read as one list and only as far as the shorter of them,
 * because nothing downstream can tell a name from a missing name.
 */
private fun outstandingFrom(intent: Intent): List<OutstandingHabit>? {
    val ids = intent.getStringArrayExtra(EXTRA_IDS) ?: emptyArray()
    val names = intent.getStringArrayExtra(EXTRA_NAMES) ?: emptyArray()
    val habits = ids.zip(names) { id, name ->
        runCatching { OutstandingHabit(HabitId(id), name) }.getOrNull()
    }

    // A missing array zips to nothing, which is refused by the same clause that
    // refuses an empty one — so neither needs a guard of its own.
    return habits.takeIf { it.isNotEmpty() && null !in it }?.filterNotNull()
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
 * **The carried list decides the candidates; the log decides which are left.**
 * Nothing re-derives what is *outstanding* for the carried date — that needs the
 * schedule, and `HabitRepository.observeToday` only ever answers for the current
 * logical date, so it would mean a second way to decide outstanding beside
 * `Mascot.isOutstanding`. What it does ask is the far cheaper question: of the
 * habits this button carried, which already have a completion on that day. That
 * is a fact rather than a judgement, and one grouped query
 * ([HabitRepository.observeCompletionDatesByHabit] with the carried date at both
 * ends) answers it for all of them at once. Still no clock: the date goes in, it
 * is not resolved here.
 *
 * **That is also what makes two taps in flight safe, and serialising alone would
 * not have been.** Each button's `PendingIntent` was filled in when the
 * notification was posted, so a second tap carries a list that predates the
 * first tap's write — ordering the two would still have left the later one
 * re-posting a button for a habit already done. Reading the day back is what
 * closes it, and [tapLock] is what guarantees the read happens after the write
 * it should see.
 *
 * A read that fails leaves the shade untouched and the write standing, the same
 * shape as the refused branch below. There is deliberately no fallback to the
 * unreconciled list: re-posting a button for a completed habit is the defect
 * this paragraph exists to remove.
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

    // containsKey, not the value: a habit with nothing in the range is absent
    // rather than present with an empty set, which the read's own KDoc states.
    val done = habits.observeCompletionDatesByHabit(request.logicalDate, request.logicalDate).first()
    val remaining = request.outstanding.filterNot { done.containsKey(it.id) }

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
