package com.gawi.core.domain.model

import com.gawi.core.domain.id.CanonicalUuid

/**
 * Identity of a habit: its own UUIDv7 in canonical form, carried explicitly
 * in every habit-related payload. It is deliberately not "the id of the
 * HabitCreated event" — payloads stay self-describing, and habit identity
 * survives whole-record LWW between HabitCreated and HabitUpdated writes.
 */
@JvmInline
value class HabitId(val value: String) {

    init {
        require(CanonicalUuid.matches(value)) { "not a canonical UUID string: $value" }
    }

    override fun toString(): String = value
}
