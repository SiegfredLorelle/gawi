package com.gawi.core.domain.mascot

import com.gawi.core.domain.model.Schedule
import com.gawi.core.domain.streak.StreakSnapshot
import com.gawi.core.domain.time.reminderOn
import com.gawi.core.domain.time.weekStartOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The mascot's mood rules (docs/ux/today-view.md §4).
 *
 * Pure, and deliberately not stored or folded into projection, for the reason
 * already written into [com.gawi.core.domain.streak.Streaks]' KDoc: this
 * depends on "today", which is not in the event log, so applying it during
 * replay would break architecture §4's incremental-≡-rebuild invariant. Like
 * every other rule in this module, "today" is a parameter and there is no
 * clock here.
 *
 * Provisional in the way §4 is provisional: this is Phase 1 behaviour, written
 * down so the MVP placeholder and the eventual Rive state machine are driven by
 * one rule. Grace mechanics, if they ever land (PRD OQ-3), change what
 * "recently broken" means and therefore this whole file.
 */
object Mascot {

    /**
     * How long a broken streak keeps Momo regenerating, in logical days,
     * counting the day the break became visible.
     *
     * §4 sizes this window in days while `StreakSnapshot.brokenOn` is
     * denominated in the schedule's own unit — a date for a daily habit, a week
     * start for a weekly one — so the two do not compose on their own. Days for
     * both is the reading taken: it is literal to §4, and because
     * [Mood.REGENERATING] outranks [Mood.WORRIED], a short window is exactly
     * what lets a weekly habit's now-or-never warning surface late in the week
     * instead of being masked by the recovery face. For a weekly habit that
     * means the first three days of the week its streak zeroed.
     *
     * The 3 is a guess. It was flagged for the 30-day trial alongside PRD
     * OQ-3, and PRD §8 now records why that flag cannot be acted on as
     * written: Phase 0 draws three faces and folds [Mood.REGENERATING] onto
     * neutral, so no amount of using the app distinguishes this window's
     * effect from its absence. It waits for Phase 1's fourth face, or it is
     * settled on the streak rules alone.
     */
    const val REGENERATING_WINDOW_DAYS = 3L

    /**
     * The mood for one reading of the Today view — §4's precedence table, first
     * match wins.
     *
     * The table is the `when` rather than something the code happens to do, so
     * the order is reviewable against the doc line by line. Two rows carry
     * reasoning worth not re-deriving:
     *
     * Rule 0 is load-bearing, not a guard. Without it a first run with zero
     * habits satisfies rule 1 and Momo would greet a brand-new user as
     * thriving.
     *
     * [Mood.THRIVING] outranking [Mood.REGENERATING] is the deliberate call.
     * Finishing the day is the way out of the recovery state, so it can never
     * sit there as a quiet scold.
     *
     * Rule 3 in the doc reads "`outstanding` non-empty **and** `nearBoundary`".
     * The conjunction is missing here because rule 1 already answered for the
     * empty case; adding it back would be harmless and would also quietly say
     * the precedence does not matter.
     */
    fun mood(inputs: MoodInputs): Mood {
        val live = inputs.habits.filterNot { it.archived }
        // A predicate rather than the set: row 1 asks whether outstanding is
        // empty, nothing here asks what is in it, and the remaining count the
        // Today view needs belongs to the UI computing it from [isOutstanding].
        val nothingOutstanding = live.none { isOutstanding(it, inputs.today, inputs.weekStart) }
        return when {
            live.isEmpty() -> Mood.CONTENT
            nothingOutstanding -> Mood.THRIVING
            live.any { recentlyBroken(it.streak, inputs.today) } -> Mood.REGENERATING
            nearBoundary(inputs) -> Mood.WORRIED
            else -> Mood.CONTENT
        }
    }

    /**
     * Whether [habit] is due today and not yet satisfied — §4's `outstanding`.
     *
     * Nothing completed today is outstanding today, whatever its schedule. §4
     * writes that gate into the daily rule only, and stating the weekly rule as
     * a bare `remaining >= daysLeft` leaves a habit outstanding after the user
     * has done everything today allows: 3×/week with none done reaches Saturday
     * needing 3 in 2 days, and completing Saturday leaves 2 needed in a
     * `daysLeft` of 2, so it would still read outstanding — nagging about a
     * target that is already out of reach, on a day the user did turn up. The
     * gate only ever fires once the target has become unreachable, because a
     * reachable one is satisfied by the arithmetic instead.
     *
     * A weekly habit is deliberately **now-or-never**: with `remaining`
     * completions to go and `daysLeft` days to go, it is outstanding only once
     * `remaining >= daysLeft`, so a 1×/week habit stays quiet until its last
     * possible day. Weekly targets are not tied to specific days (PRD §4), and
     * treating one as due every day until met would nag about a Sunday-able
     * habit on Monday, contradicting the schedule type.
     *
     * Public because the Today view needs the same answer for its remaining
     * count and the app-bar chip (§1), and a second implementation of the
     * now-or-never rule in the UI is exactly how the two would come to
     * disagree.
     */
    fun isOutstanding(habit: HabitMoodState, today: LocalDate, weekStart: DayOfWeek): Boolean =
        !habit.completedToday && when (habit.schedule) {
            is Schedule.Daily -> true

            is Schedule.Weekly -> {
                val remaining = habit.schedule.timesPerWeek - habit.completionsThisWeek
                remaining > 0 && remaining >= daysLeftInWeek(today, weekStart)
            }
        }

    /** Days left in [today]'s week, counting today: 7 on the week's first day, 1 on its last. */
    private fun daysLeftInWeek(today: LocalDate, weekStart: DayOfWeek): Int {
        val elapsed = ChronoUnit.DAYS.between(weekStartOn(today, weekStart), today)
        return (Schedule.DAYS_PER_WEEK - elapsed).toInt()
    }

    /**
     * §4's `recentlyBroken`, answered from [StreakSnapshot.brokenOn] with
     * nothing stored.
     *
     * A null `brokenOn` is [StreakSnapshot.NONE] — a habit with no completions
     * yet, which is nothing to recover from rather than a break. A `brokenOn`
     * after [today] means the caller paired a snapshot with a date it was not
     * computed for; it is not a break that has happened, so it does not count.
     */
    private fun recentlyBroken(streak: StreakSnapshot, today: LocalDate): Boolean {
        val brokenOn = streak.brokenOn ?: return false
        return !brokenOn.isAfter(today) && !brokenOn.isBefore(today.minusDays(REGENERATING_WINDOW_DAYS - 1))
    }

    /**
     * §4's `nearBoundary`: at or past the configured reminder time, and before
     * the day boundary. One threshold, shared with the end-of-day reminder, so
     * there is no second one to keep in sync.
     *
     * Where the threshold falls is [reminderOn]'s answer, not this function's.
     * It is shared because the data layer has to wake at the same instant to
     * re-read the mood, and the reminder notification will have to fire at it.
     *
     * The upper bound is this function's own, and is redundant when `today` was
     * derived from `now`. It is kept anyway: it means a caller holding a stale
     * date reads "not near the boundary" rather than leaving Momo worried
     * indefinitely.
     */
    private fun nearBoundary(inputs: MoodInputs): Boolean {
        val dayStart = LocalDateTime.of(inputs.today, inputs.dayCutoff)
        val reminderAt = reminderOn(inputs.today, inputs.reminderTime, inputs.dayCutoff)
        return !inputs.now.isBefore(reminderAt) && inputs.now.isBefore(dayStart.plusDays(1))
    }
}
