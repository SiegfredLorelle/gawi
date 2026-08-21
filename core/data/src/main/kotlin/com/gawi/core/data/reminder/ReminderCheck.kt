package com.gawi.core.data.reminder

import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.model.toMoodState
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.data.settings.SettingsSource
import com.gawi.core.data.time.DeviceClock
import com.gawi.core.domain.mascot.Mascot
import com.gawi.core.domain.time.logicalDate
import com.gawi.core.domain.time.reminderOn
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the end-of-day reminder has anything to say, and when the next two
 * scheduled wakes fall.
 *
 * **Here rather than in `:app`, even though architecture §2 gives `:app` the
 * WorkManager scheduling.** What `:app` owns is the worker, the channel and the
 * notification; what this owns is every rule behind them. Splitting it that way
 * is what keeps `:app` free of a clock, a cutoff and a second copy of the
 * now-or-never rule — the three things a reminder built entirely in `:app` would
 * have had to grow, and the third of which [Mascot.isOutstanding]'s KDoc names
 * as how the Today view's chip and this notification would come to disagree.
 *
 * Nothing here decides *whether* to schedule; a caller asks how long until the
 * next wake and does what it likes with the answer. So this class holds no
 * WorkManager type and is testable on the JVM with a fake clock, which is where
 * PRD §6.1's criteria are actually pinned.
 *
 * **Public class, `internal` constructor.** This module's habit is an internal
 * implementation behind a public interface (`OfflineFirstHabitRepository` behind
 * `HabitRepository`), which is the right shape when there is a seam worth faking.
 * There is not one here: `:app` has no reason to substitute a different reminder
 * rule, and an interface with one implementation and one caller would be
 * ceremony. Splitting the visibility says the true thing instead — anyone may
 * *use* it, only `:core:data` may *build* it — and it is what keeps
 * [ReminderJournal] internal, which matters because that class's failure policy
 * is the opposite of the one next door and is not something to expose.
 */
@Singleton
class ReminderCheck @Inject internal constructor(
    private val repository: HabitRepository,
    private val settings: SettingsSource,
    private val clock: DeviceClock,
    private val journal: ReminderJournal,
) {

    /**
     * What to do now, having been woken at the reminder threshold.
     *
     * One read of [HabitRepository.observeToday], because that snapshot already
     * carries everything the decision needs — the rows, the logical date they
     * were queried for, and the week start the weekly rule buckets against
     * ([TodaySnapshot]). Reading the clock or the settings again here would be a
     * second, independently-resolved "today" that could disagree with the rows.
     *
     * The count comes from [Mascot.isOutstanding] and is not recomputed. The
     * daily case is obvious and the weekly one is not — a weekly habit is only
     * outstanding once the week has too few days left to still finish it — and a
     * notification that counted differently from the app-bar chip would be worse
     * than one that did not exist.
     *
     * **The threshold is re-checked, and that is not belt-and-braces.** A wake can
     * be deferred — Doze, a powered-off device, a vendor battery policy — and one
     * deferred past the day cutoff arrives inside the *next* logical day, where
     * every habit is legitimately incomplete. Without this it would post *"5 of 5
     * left today"* at 00:30 and, worse, stamp the journal for the new day, so the
     * real reminder that evening would be suppressed by the one that fired by
     * mistake. Silence in that window is not a missed reminder; the correctly
     * armed one is still ahead.
     *
     * The threshold is [reminderOn]'s, so it is the same instant the mascot's
     * `nearBoundary` uses and not a second copy of it. `nearBoundary`'s *upper*
     * bound is deliberately not repeated here: it exists there to protect a caller
     * holding a stale date, and [TodaySnapshot.today] is derived from
     * [TodaySnapshot.now] in the same read, so the day boundary cannot be behind
     * us and the check would be dead code.
     *
     * **The tolerance is asymmetric on purpose.** Late is rejected outright, above.
     * Early is allowed a minute, because the two costs are not comparable: a wake
     * a second early that is refused means no reminder *at all* that day — the
     * next armed wake is tomorrow's — while a wake a minute early means a nudge a
     * minute early, which nobody can perceive. WorkManager defers work and does
     * not run it ahead of its delay, so this absorbs clock jitter between arming
     * and waking rather than a scheduling behaviour.
     *
     * Ordered so that **nothing is stamped unless something is said**:
     *
     * - Nothing outstanding is [ReminderDecision.Silent] and leaves the journal
     *   untouched. PRD §6.1.5's *"silent when all done"*, and stamping here would
     *   suppress a real reminder later the same evening if a habit were added, or
     *   a completion undone.
     * - Already reminded for this logical date is [ReminderDecision.Silent] too,
     *   which is the other half of §6.1.5.
     * - Otherwise stamp first, then report. Stamping before returning means a
     *   caller that posts and then crashes has still recorded the day, which
     *   errs towards one reminder rather than two — the direction
     *   [ReminderJournal] argues for throughout.
     */
    suspend fun evaluate(): ReminderDecision {
        val snapshot = repository.observeToday().first()
        val threshold = reminderOn(snapshot.today, snapshot.reminderTime, snapshot.dayCutoff)
        if (snapshot.now.isBefore(threshold.minus(EARLY_TOLERANCE))) return ReminderDecision.Silent

        val outstanding = snapshot.habits.count { row ->
            Mascot.isOutstanding(row.toMoodState(), snapshot.today, snapshot.weekStart)
        }

        // A `when` rather than three guard clauses, so the order these are decided
        // in is one visible list instead of something a reader has to reconstruct
        // from the sequence of early returns. The threshold check above stays an
        // early return because it is the one that avoids work rather than ordering
        // it.
        return when {
            outstanding == 0 -> ReminderDecision.Silent

            journal.alreadyReminded(snapshot.today) -> ReminderDecision.Silent

            else -> {
                journal.record(snapshot.today)
                ReminderDecision.Remind(outstanding = outstanding, total = snapshot.habits.size)
            }
        }
    }

    /**
     * How long until the reminder threshold next falls.
     *
     * A duration rather than an instant, so that **`:app` holds no clock**. That
     * is the claim this class's KDoc makes, and handing out an instant would have
     * quietly made it false: a caller turning one into a WorkManager delay has to
     * read `now` to do it, and would then own a second reading of the clock that
     * could disagree with the one this was resolved against.
     *
     * [reminderOn]'s third caller, which its KDoc has been naming since it was
     * written: the mood's `nearBoundary`, the data layer's ticker, and this. A
     * second copy of the shift it applies is how the worried face and the
     * notification would come to fire at different hours.
     *
     * Today's threshold if it is still ahead, otherwise tomorrow's. "Still ahead"
     * is strict, so a worker woken *exactly* on the threshold schedules the next
     * one for tomorrow rather than for the instant it is already standing at —
     * which would be a worker that re-enqueues itself in a loop.
     */
    suspend fun untilNextReminder(): Duration {
        val now = clock.now()
        val current = settings.observe().first()
        val today = logicalDate(now, current.dayCutoff, clock.zone())
        val at = reminderOn(today, current.reminderTime, current.dayCutoff).atInstant()
        val next = if (at.isAfter(now)) at else reminderOn(today.plusDays(1), current.reminderTime, current.dayCutoff).atInstant()

        return Duration.between(now, next)
    }

    /**
     * How long until the day boundary next falls — the wake the widget needs and
     * that no event can provide.
     *
     * A day rollover commits nothing, so no `ProjectionListener` push can fire
     * for one, and a widget with no live session shows the previous day's ticks
     * (architecture §4, docs/ux/widget.md §4). This is when to wake.
     *
     * The logical date `D` runs from its cutoff to the next, so `D`'s boundary is
     * the cutoff on `D + 1` — the same arithmetic `Mascot.nearBoundary` does as
     * `dayStart.plusDays(1)`, and consistent with [logicalDate]'s rule that a
     * wall time exactly at the cutoff begins the new day. That rule is also why
     * this needs no strictly-ahead fallback the way [untilNextReminder] does: the
     * boundary of the date `now` resolves to is always strictly after `now`.
     */
    suspend fun untilNextCutoff(): Duration {
        val now = clock.now()
        val cutoff = settings.observe().first().dayCutoff
        val today = logicalDate(now, cutoff, clock.zone())

        return Duration.between(now, LocalDateTime.of(today.plusDays(1), cutoff).atInstant())
    }

    /**
     * The one place a local time becomes an instant.
     *
     * The zone is read per call, never cached: [DeviceClock] is an interface
     * precisely so that a user who travels gets answers in the zone they are
     * actually in.
     *
     * `atZone` resolves a DST gap forwards and picks the earlier of a repeated
     * hour, which is the right shape for a wake — a threshold that does not exist
     * on a given day still has to happen, and one that happens twice should not
     * fire twice. Architecture §7's *"deliberately inexact"* covers the residual.
     */
    private fun LocalDateTime.atInstant(): Instant = atZone(clock.zone()).toInstant()

    private companion object {
        /** How far ahead of the threshold a wake may arrive and still count. */
        val EARLY_TOLERANCE: Duration = Duration.ofMinutes(1)
    }
}

/**
 * What [ReminderCheck.evaluate] found.
 *
 * A sealed interface rather than a nullable count, because "nothing to say" and
 * "nothing outstanding" are the same word in this feature and reading a `0`
 * as either is how a silent reminder becomes a reminder saying zero.
 */
sealed interface ReminderDecision {

    /** Say nothing: everything is done, or today has already been reminded. */
    data object Silent : ReminderDecision

    /**
     * Post a reminder about [outstanding] of [total] habits.
     *
     * [total] is every non-archived habit, so the copy can say *"2 of 5 left"* the
     * way the Today view's chip does rather than inventing a second phrasing.
     */
    data class Remind(val outstanding: Int, val total: Int) : ReminderDecision
}
