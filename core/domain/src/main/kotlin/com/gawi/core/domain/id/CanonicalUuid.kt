package com.gawi.core.domain.id

/** Canonical UUID string form: lowercase hex, 8-4-4-4-12. */
internal val CanonicalUuid =
    Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
