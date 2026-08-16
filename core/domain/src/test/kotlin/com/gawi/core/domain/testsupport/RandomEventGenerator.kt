package com.gawi.core.domain.testsupport

import com.gawi.core.domain.event.CompletionAdded
import com.gawi.core.domain.event.CompletionNoteUpdated
import com.gawi.core.domain.event.CompletionTombstoned
import com.gawi.core.domain.event.Event
import com.gawi.core.domain.event.HabitArchived
import com.gawi.core.domain.event.HabitUnarchived
import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.id.UuidV7Generator
import com.gawi.core.domain.model.HabitId
import com.gawi.core.domain.model.Schedule
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random

/**
 * Seeded generator of realistic-hostile event sequences for the projection
 * invariant test: duplicate adds into the same cell, tombstones and note
 * writes with dangling references, habit events for never-created ids,
 * forced occurred-at ties, and a clock that sometimes jumps backwards.
 * Event ids come from the real UuidV7Generator, so invariant runs double as
 * a generator soak.
 */
class RandomEventGenerator(private val rng: Random, private val today: LocalDate) {

    private var idClockMillis = 1_700_000_000_000L
    private val ids = UuidV7Generator({ idClockMillis++ }, Random(rng.nextLong()))

    private val createdHabits = List(3) { HabitId(ids.next().value) }
    private val ghostHabit = HabitId(ids.next().value)
    private val allHabits = createdHabits + ghostHabit

    private var occurredAt = 1_750_000_000_000L
    private val addIds = mutableListOf<Pair<EventId, CompletionAdded>>()

    fun sequence(length: Int): List<Event> {
        val events = mutableListOf<Event>()
        createdHabits.forEachIndexed { i, habit ->
            val schedule = if (i % 2 == 0) Schedule.Daily else Schedule.Weekly(rng.nextInt(2, 5))
            events += emit(habitCreated(habit, name = "habit-$i", schedule = schedule))
        }
        repeat(length - events.size) { events += emit(drawPayload()) }
        return events
    }

    private fun emit(payload: com.gawi.core.domain.event.EventPayload): Event {
        val jitter = rng.nextInt(10)
        occurredAt = when {
            jitter < 2 -> occurredAt
            jitter < 3 -> occurredAt - rng.nextLong(3_600_000)
            else -> occurredAt + rng.nextLong(1, 60_000)
        }
        val event = Event(EventId(ids.next().value), Instant.ofEpochMilli(occurredAt), 0, payload)
        (payload as? CompletionAdded)?.let { addIds += event.id to it }
        return event
    }

    @Suppress("MagicNumber")
    private fun drawPayload(): com.gawi.core.domain.event.EventPayload {
        val roll = rng.nextInt(100)
        return when {
            roll < 40 -> drawAdd()
            roll < 60 -> drawTombstone()
            roll < 75 -> drawNoteUpdate()
            roll < 85 -> habitUpdated(allHabits.random(rng), name = "renamed-${rng.nextInt(100)}")
            roll < 90 -> HabitArchived(allHabits.random(rng))
            roll < 95 -> HabitUnarchived(allHabits.random(rng))
            else -> habitCreated(allHabits.random(rng), name = "recreated-${rng.nextInt(100)}")
        }
    }

    private fun drawAdd(): CompletionAdded {
        val reuseCell = addIds.isNotEmpty() && rng.nextInt(100) < 30
        val note = if (rng.nextInt(100) < 25) "note-${rng.nextInt(100)}" else null
        return if (reuseCell) {
            val (_, previous) = addIds.random(rng)
            CompletionAdded(previous.habitId, previous.logicalDate, note)
        } else {
            val habit = if (rng.nextInt(100) < 10) ghostHabit else createdHabits.random(rng)
            CompletionAdded(habit, today.minusDays(rng.nextLong(-1, 61)), note)
        }
    }

    private fun drawTombstone(): CompletionTombstoned {
        val dangling = addIds.isEmpty() || rng.nextInt(100) < 25
        val target = if (dangling) EventId(uuid(rng.nextInt(1_000_000))) else addIds.random(rng).first
        return CompletionTombstoned(target)
    }

    private fun drawNoteUpdate(): CompletionNoteUpdated {
        val dangling = addIds.isEmpty() || rng.nextInt(100) < 20
        val target = if (dangling) EventId(uuid(rng.nextInt(1_000_000))) else addIds.random(rng).first
        val text = if (rng.nextInt(100) < 20) "" else "updated-${rng.nextInt(100)}"
        return CompletionNoteUpdated(target, text)
    }
}
