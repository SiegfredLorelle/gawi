package com.gawi.core.data.testsupport

import com.gawi.core.domain.command.CommandResult
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import kotlin.random.Random

/**
 * A seeded random walk over the repository's *commands*.
 *
 * Deliberately not the domain's `RandomEventGenerator`. That one manufactures
 * dangling tombstones, early-arriving note writes and habits that never got
 * created — sequences replay must survive but that no local command path can
 * produce, because commands validate against the projected state first. Driving
 * those through the repository would test a code path Phase 2 owns.
 *
 * What this weights toward instead is what the command path really does and
 * what the row-delta logic is most likely to get wrong: repeated adds into one
 * cell, undo followed by a fresh add, notes written and then cleared, archive
 * and unarchive part-way through, completions at the exact edges of the retro
 * window, schedule changes that re-denominate a streak, and a clock that keeps
 * moving so streak rows go stale.
 */
internal class RepositoryCommandGenerator(
    private val rng: Random,
    private val store: TestStore,
    /**
     * Whether the walk is allowed to move the clock.
     *
     * Held still, every streak row stays current the moment it is written, so
     * a snapshot taken without sweeping is directly comparable to a rebuild —
     * which is what makes the streak *delta* itself testable. Left moving, it
     * is not: an untouched habit legitimately keeps older numbers.
     */
    private val movesClock: Boolean = true,
) {

    private val habits = mutableListOf<HabitId>()

    suspend fun run(commands: Int) {
        repeat(commands) { step() }
    }

    private suspend fun step() {
        when (rng.nextInt(ACTIONS)) {
            0 -> createHabit()
            1 -> createHabit()
            2 -> updateHabit()
            3 -> archiveHabit()
            4 -> unarchiveHabit()
            5, 6, 7 -> addCompletion()
            8 -> undoCompletion()
            9 -> updateNote()
            else -> advanceClock()
        }
    }

    private suspend fun createHabit() {
        val result = store.repository.createHabit(metadata("habit-${habits.size}", randomSchedule()))
        if (result is CommandResult.Accepted) habits += result.payload
    }

    private suspend fun updateHabit() {
        val habit = pick() ?: return
        // Renaming and re-scheduling both matter: the schedule change is the
        // one that moves a streak without moving a completion.
        store.repository.updateHabit(habit, metadata("habit-${rng.nextInt(habits.size)}", randomSchedule()))
    }

    private suspend fun archiveHabit() {
        pick()?.let { store.repository.archiveHabit(it) }
    }

    private suspend fun unarchiveHabit() {
        pick()?.let { store.repository.unarchiveHabit(it) }
    }

    private suspend fun addCompletion() {
        val habit = pick() ?: return
        // -4 and +1 are outside the window on purpose: a rejection must leave
        // the log and the tables exactly as they were.
        val offset = rng.nextInt(RETRO_LOW, RETRO_HIGH).toLong()
        val note = if (rng.nextBoolean()) null else "note-${rng.nextInt(NOTES)}"
        store.repository.addCompletion(habit, store.today().plusDays(offset), note)
    }

    private suspend fun undoCompletion() {
        val habit = pick() ?: return
        val offset = rng.nextInt(RETRO_LOW, RETRO_HIGH).toLong()
        store.repository.undoCompletion(habit, store.today().plusDays(offset))
    }

    private suspend fun updateNote() {
        val habit = pick() ?: return
        val offset = rng.nextInt(RETRO_LOW, RETRO_HIGH).toLong()
        // An empty write is a real write that clears the note and wins LWW.
        val text = if (rng.nextBoolean()) "" else "note-${rng.nextInt(NOTES)}"
        store.repository.updateNote(habit, store.today().plusDays(offset), text)
    }

    private fun advanceClock() {
        if (movesClock) store.clock.advanceDays(rng.nextInt(1, MAX_DAY_JUMP).toLong())
    }

    private fun pick(): HabitId? = habits.randomOrNull(rng)

    private fun randomSchedule(): Schedule = if (rng.nextBoolean()) Schedule.Daily else Schedule.Weekly(rng.nextInt(1, WEEKLY_MAX))

    private companion object {
        const val ACTIONS = 11
        const val RETRO_LOW = -4
        const val RETRO_HIGH = 2
        const val NOTES = 3
        const val MAX_DAY_JUMP = 4
        const val WEEKLY_MAX = 8
    }
}
