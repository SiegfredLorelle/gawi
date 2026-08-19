package com.gawi.core.domain.id

/**
 * Identity of a single event in the log: a UUIDv7 in canonical form
 * (lowercase, 8-4-4-4-12). Because the hex is fixed-width and the timestamp
 * occupies the most significant bits, lexicographic order of [value] equals
 * numeric UUID order equals generation order — the property the LWW
 * tiebreak and the events table's primary-key ordering rely on.
 *
 * That property is why [CanonicalUuid] pins the version and variant nibbles
 * rather than the shape alone: a well-formed v4 carries no time ordering, and
 * accepting one here would quietly break both.
 */
@JvmInline
value class EventId(val value: String) : Comparable<EventId> {

    init {
        require(CanonicalUuid.matches(value)) { "not a canonical UUIDv7 string: $value" }
    }

    override fun compareTo(other: EventId): Int = value.compareTo(other.value)

    override fun toString(): String = value
}
