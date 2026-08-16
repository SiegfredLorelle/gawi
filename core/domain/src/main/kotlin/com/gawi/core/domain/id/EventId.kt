package com.gawi.core.domain.id

/**
 * Identity of a single event in the log: a UUIDv7 in canonical form
 * (lowercase, 8-4-4-4-12). Because the hex is fixed-width and the timestamp
 * occupies the most significant bits, lexicographic order of [value] equals
 * numeric UUID order equals generation order — the property the LWW
 * tiebreak and the events table's primary-key ordering rely on.
 */
@JvmInline
value class EventId(val value: String) : Comparable<EventId> {

    init {
        require(CANONICAL.matches(value)) { "not a canonical UUID string: $value" }
    }

    override fun compareTo(other: EventId): Int = value.compareTo(other.value)

    override fun toString(): String = value

    companion object {
        private val CANONICAL =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
