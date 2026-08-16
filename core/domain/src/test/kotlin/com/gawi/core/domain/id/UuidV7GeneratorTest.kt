package com.gawi.core.domain.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UuidV7GeneratorTest {

    private var clock = 0L
    private fun generator(seed: Long = 42L) = UuidV7Generator({ clock }, Random(seed))

    private fun timestampOf(id: EventId): Long = id.value.replace("-", "").take(12).toLong(radix = 16)

    @Test
    fun `id is canonical lowercase with version 7 and variant 10`() {
        clock = 1_755_400_000_000

        val id = generator().next().value

        assertEquals(36, id.length)
        assertEquals('7', id[14])
        assertTrue("variant nibble was ${id[19]}", id[19] in "89ab")
        assertEquals(id, id.lowercase())
    }

    @Test
    fun `timestamp field equals the injected clock millis`() {
        clock = 1_755_400_123_456

        val id = generator().next()

        assertEquals(1_755_400_123_456, timestampOf(id))
    }

    @Test
    fun `same-millisecond burst is strictly increasing`() {
        clock = 1_755_400_000_000
        val generator = generator()

        val ids = List(10_000) { generator.next() }

        for (i in 1 until ids.size) {
            assertTrue("ids[$i] did not increase", ids[i] > ids[i - 1])
            assertTrue("string order diverged at $i", ids[i].value > ids[i - 1].value)
        }
    }

    @Test
    fun `ids stay monotonic across a millisecond tick`() {
        clock = 1_755_400_000_000
        val generator = generator()

        val beforeTick = generator.next()
        clock += 1
        val afterTick = generator.next()

        assertTrue(afterTick > beforeTick)
    }

    @Test
    fun `clock going backwards never breaks monotonicity`() {
        clock = 1_000
        val generator = generator()

        val first = generator.next()
        clock = 500
        val second = generator.next()

        assertTrue(second > first)
        assertEquals(1_000, timestampOf(second))
    }

    @Test
    fun `timestamps resume tracking the clock after a regression recovers`() {
        clock = 1_000
        val generator = generator()

        generator.next()
        clock = 500
        generator.next()
        clock = 2_000
        val recovered = generator.next()

        assertEquals(2_000, timestampOf(recovered))
    }

    @Test
    fun `counter overflow advances the timestamp instead of repeating`() {
        clock = 1_000
        val generator = generator()

        val ids = List(5_000) { generator.next() }

        assertTrue("overflow never advanced the timestamp", timestampOf(ids.last()) > 1_000)
        assertEquals(5_000, ids.toSet().size)
    }

    @Test
    fun `a pre-epoch clock is clamped and still yields canonical ids`() {
        clock = -5_000
        val generator = generator()

        val first = generator.next()
        val second = generator.next()

        assertEquals(0, timestampOf(first))
        assertTrue(second > first)
    }

    @Test
    fun `different random seeds give different ids at the same instant`() {
        clock = 1_755_400_000_000

        val a = generator(seed = 1).next()
        val b = generator(seed = 2).next()

        assertNotEquals(a, b)
    }

    @Test
    fun `seeded soak over a jittery clock stays strictly monotonic`() {
        val rng = Random(20_260_817)
        clock = 1_755_400_000_000
        val generator = generator()

        var previous = generator.next()
        repeat(100_000) {
            clock += rng.nextLong(-2, 5)
            val next = generator.next()
            assertTrue("id did not increase after clock jitter", next > previous)
            previous = next
        }
    }
}
