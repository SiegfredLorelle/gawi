package com.gawi.core.data

/**
 * The version of the projection *logic* — how events become derived rows, not
 * what shape those rows have.
 *
 * Bump this whenever a change would make the same log produce different rows:
 * a fix in `ProjectionWriter`, or a domain change to `Projector` or `Streaks`.
 * The stored value is compared on start and a mismatch replays the log.
 *
 * A Room schema version cannot do this job. The columns do not move when the
 * rule that fills them changes, so Room sees nothing wrong and leaves rows in
 * place that current code would never have written.
 *
 * **2, since `HabitState.createdOn`.** That change moved a column *and* changed
 * what fills it, so it needed both versions bumped — the migration adds the
 * column and this replays the log to populate it. A schema bump on its own would
 * have left every habit's start date null.
 */
internal const val PROJECTION_VERSION = 2
