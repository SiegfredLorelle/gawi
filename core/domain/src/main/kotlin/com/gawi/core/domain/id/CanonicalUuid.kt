package com.gawi.core.domain.id

/**
 * Canonical UUIDv7 string form: lowercase hex, 8-4-4-4-12, version nibble 7,
 * RFC 9562 variant nibble (8, 9, a or b).
 *
 * Two deliberate strictnesses, both load-bearing:
 *
 * - **Version and variant are pinned, not just the shape.** [EventId] promises
 *   that lexicographic order equals generation order, which is only true of a
 *   v7 id; a v4 would satisfy a shape-only pattern and silently carry no time
 *   ordering, breaking the LWW tiebreak and the events table's primary-key
 *   ordering (architecture §3). [HabitId] never relies on that ordering, but
 *   shares the pattern anyway: its own contract declares a UUIDv7, one
 *   generator mints both, and a single rule is easier to keep honest than two.
 * - **Lowercase only.** Stored ids stay byte-comparable, which is what makes an
 *   `ORDER BY id` read equal log order. RFC 9562 §4 asks readers to accept
 *   either case, so a foreign log with uppercase ids is legal input that this
 *   pattern rejects. That is the right place for the strictness today (the MVP
 *   log has a single writer), but when sync lands the wire→domain mappers in
 *   `serialization/wire` should lowercase on the way in — normalise at the
 *   writer, not by loosening the reader.
 */
internal val CanonicalUuid =
    Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
