package com.gawi.core.domain.serialization.export

import com.gawi.core.domain.id.EventId
import com.gawi.core.domain.serialization.EncodedPayload
import java.time.Instant

/**
 * One event of the log in transit, as an export file carries it: the envelope
 * fields plus its payload exactly as stored.
 *
 * Deliberately not a decoded `Event`. The payload text travels verbatim, so a
 * round trip through an export neither upcasts an old payload to the current
 * schema version nor drops a key this build does not know about — both of
 * which a decode-then-re-encode would do silently, and both of which are the
 * log being migrated in place (architecture §3).
 */
data class EncodedEvent(val id: EventId, val occurredAt: Instant, val tzOffsetMin: Int, val payload: EncodedPayload)

/** What the exporter stamps on the envelope. Passed in, so a test can freeze it. */
data class ExportMeta(val exportedAt: Instant, val appVersion: String)
