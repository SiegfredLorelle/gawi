package com.gawi.core.domain.serialization.export

/**
 * Why an export file was refused, in full.
 *
 * A refusal is a value rather than an exception because picking the wrong file
 * is a thing a *user* does, and this project models those as data — the same
 * reasoning `CommandResult.Rejected` is built on. `EventCodecException` stays
 * for what it was written for: a stored log that cannot be read, which is
 * corruption and has no user on the other end of it.
 */
sealed interface ExportRejection {

    /** Parsed as JSON, but carries no `gawi.event-log` marker: a different file. */
    data object NotAnExport : ExportRejection

    /**
     * An export written by a newer build of Gawi.
     *
     * Deliberately distinct from [Malformed], and the distinction is the whole
     * reason the reader looks at the version before it decodes anything else.
     * A file from a newer build is intact; telling its owner it is damaged,
     * when it is the only copy of their history, would be a lie with
     * consequences.
     */
    data class UnsupportedFormatVersion(val found: Int, val supported: Int) : ExportRejection

    /**
     * An export this build cannot make sense of: not JSON, a bad id, an unknown
     * event type, an unknown schema version, or a corrupt payload.
     *
     * [detail] names the offending event by position and id where there is one.
     * That is the payoff for the format being open: a file refused for one bad
     * event stays repairable by hand, which is what makes refusing the whole
     * file affordable.
     */
    data class Malformed(val detail: String) : ExportRejection
}

/** The outcome of reading an export file. */
sealed interface ExportRead {

    data class Events(val events: List<EncodedEvent>) : ExportRead

    data class Refused(val reason: ExportRejection) : ExportRead
}
