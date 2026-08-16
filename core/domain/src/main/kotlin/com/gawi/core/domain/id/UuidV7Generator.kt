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
 */
class UuidV7Generator(private val nowMillis: () -> Long = System::currentTimeMillis, private val random: Random = Random.Default) {

    private var lastMillis = -1L
    private var counter = 0

    @Synchronized
    fun next(): EventId {
        val now = nowMillis()
        if (now > lastMillis) {
            lastMillis = now
            counter = random.nextInt(COUNTER_SEED_BOUND)
        } else if (counter < COUNTER_MAX) {
            counter++
        } else {
            lastMillis++
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

        /** Seeding below half the range leaves ≥2048 same-millisecond ids of burst headroom. */
        const val COUNTER_SEED_BOUND = 0x800

        /** Top two bits of the low 64 = 0b10 (RFC 4122/9562 variant). */
        const val VARIANT_BITS = Long.MIN_VALUE

        /** The 62 bits of rand_b below the variant. */
        const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
    }
}
