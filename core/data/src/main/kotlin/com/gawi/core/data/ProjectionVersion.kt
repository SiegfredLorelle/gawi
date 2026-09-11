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
 * **It stands at 3 because of gills**, and for two reasons rather than one. A
 * column moved — `habit_streaks.spare_gills` — so a schema bump was needed
 * too, and a schema bump on its own leaves that column at its default. But
 * `Streaks` also forgives gaps now, so the same log produces a different
 * `current_streak`, `previous_streak` and `broken_on` for any habit that ever
 * ran seven clean units. The second reason is the one a reader is liable to
 * miss, and it is the one this constant exists for.
 */
internal const val PROJECTION_VERSION = 3
