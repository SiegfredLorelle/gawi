package com.gawi.core.domain.rate

/**
 * How much of a habit's target a window actually held, carried together with
 * the schedule that decided the denominator.
 *
 * Two subtypes rather than one `Double`, because **"completion rate" is two
 * different fractions** (docs/ux/insights.md §4). A daily habit's rate is
 * completions over days elapsed; a weekly habit's is completions over
 * `timesPerWeek × weeks elapsed`. A bare number returned from one function
 * would be read as comparable between the two, and nothing about the value
 * says it is not — so the schedule travels with it and a caller has to look at
 * which subtype it holds before it can render a percentage.
 */
sealed interface CompletionRate {

    /** Target met, capped at what the window could hold. Never exceeds [opportunities]. */
    val completed: Int

    /** The chances the window actually offered. Zero when no unit in it has finished. */
    val opportunities: Int

    /**
     * [completed] over [opportunities], or **null when the window held no
     * finished unit at all**.
     *
     * Null rather than `0.0` deliberately. A habit created this morning has not
     * failed anything, and `0.0` renders as "0%" — the screen telling the user
     * they missed everything, on no evidence. A caller must draw a dash for
     * null rather than defaulting it to zero.
     */
    val fraction: Double?
        get() = if (opportunities == 0) null else completed.toDouble() / opportunities

    /** Completions over days elapsed. */
    data class Daily(override val completed: Int, override val opportunities: Int) : CompletionRate

    /**
     * Completions over `timesPerWeek × weeks elapsed`.
     *
     * [timesPerWeek] is kept even though [opportunities] already folds it in,
     * because a screen that wants to say "11 of 12, 3× a week" cannot recover
     * the target by dividing — the week count and the target are two different
     * numbers and only one of them is interesting to a reader.
     */
    data class Weekly(val timesPerWeek: Int, override val completed: Int, override val opportunities: Int) : CompletionRate
}
