package com.gawi.core.domain.id

import kotlin.random.Random

/**
 * Hand-rolled UUIDv7 generator (RFC 9562) with a strict monotonicity
 * guarantee: every id returned by one instance compares greater than the
 * previous one, in generation order, no matter what the clock does.
 *
 * Layout: 48-bit unix timestamp (millis) | version 7 | 12-bit counter in
 * rand_a | variant 10 | 62 random bits in rand_b.
 *
 * The counter is randomly seeded in the lower half of its range on each new
 * millisecond and incremented for ids generated within the same
 * millisecond. On counter overflow, or when the wall clock moves backwards,
 * the timestamp field advances by one millisecond instead — the id stream
 * never blocks and never repeats, at the cost of timestamps briefly running
 * ahead of a misbehaving clock.
 *
 * The clock is clamped to the 48-bit field at both ends. Below zero it would
 * hex-format with a minus sign; above the field maximum (year ~10889) it
 * would format wider than twelve digits, and either way [format] emits a
 * string [CanonicalUuid] rejects — which poisons `lastMillis` and makes every
 * later call throw too, killing the whole write path rather than one id. At
 * the upper clamp the timestamp can no longer advance, so monotonicity is
 * what gives way instead of the format: only reachable from garbage RTC data,
 * and a lower-sorting id beats an unusable generator.
 *
 * **Monotonicity is per-instance, not process-wide.** `lastMillis` and
 * `counter` are instance state, while architecture §3 leans on a global order
 * for the LWW tiebreak and the events table's primary key. Two instances
 * generating in the same millisecond interleave freely, and because each
 * seeds `counter` randomly in `[0, 0x800)` they can collide outright — on the
 * sync dedupe key. Exactly one instance may exist per event log; provide it
 * as a `@Singleton`. The `@Synchronized` below guards shared use of one
 * instance and says nothing about a second one.
 */
class UuidV7Generator(private val nowMillis: () -> Long = System::currentTimeMillis, private val random: Random = Random.Default) {

    private var lastMillis = -1L
    private var counter = 0

    @Synchronized
    fun next(): EventId {
        // Garbage RTC data at either end would format outside the canonical
        // form and poison the generator; clamp to the field width instead.
        val now = nowMillis().coerceIn(0, MAX_TIMESTAMP_MILLIS)
        if (now > lastMillis) {
            lastMillis = now
            counter = random.nextInt(COUNTER_SEED_BOUND)
        } else if (counter < COUNTER_MAX) {
            counter++
        } else {
            lastMillis = (lastMillis + 1).coerceAtMost(MAX_TIMESTAMP_MILLIS)
            counter = random.nextInt(COUNTER_SEED_BOUND)
        }
        return EventId(format(lastMillis, counter, random.nextLong()))
    }

    private fun format(millis: Long, counter: Int, randB: Long): String {
        val ts = millis.toString(HEX_RADIX).padStart(TIMESTAMP_HEX_DIGITS, '0')
        val mid = counter.toString(HEX_RADIX).padStart(COUNTER_HEX_DIGITS, '0')
        val low = (VARIANT_BITS or (randB and RAND_B_MASK))
            .toULong()
            .toString(HEX_RADIX)
            .padStart(LOW_HEX_DIGITS, '0')
        return "${ts.take(GROUP_1)}-${ts.drop(GROUP_1)}-7$mid-${low.take(GROUP_4)}-${low.drop(GROUP_4)}"
    }

    private companion object {
        const val HEX_RADIX = 16
        const val TIMESTAMP_HEX_DIGITS = 12
        const val COUNTER_HEX_DIGITS = 3
        const val LOW_HEX_DIGITS = 16
        const val GROUP_1 = 8
        const val GROUP_4 = 4
        const val COUNTER_MAX = 0xFFF

        /** The widest value the 48-bit timestamp field can hold, i.e. twelve hex digits. */
        const val MAX_TIMESTAMP_MILLIS = 0xFFFF_FFFF_FFFFL

        /** Seeding below half the range leaves ≥2048 same-millisecond ids of burst headroom. */
        const val COUNTER_SEED_BOUND = 0x800

        /** Top two bits of the low 64 = 0b10 (RFC 4122/9562 variant). */
        const val VARIANT_BITS = Long.MIN_VALUE

        /** The 62 bits of rand_b below the variant. */
        const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
    }
}
