package com.gawi.core.data.model

/**
 * How many completions one tag accounted for over a period — the read behind
 * PRD §5's tag effort distribution (docs/ux/insights.md §5).
 *
 * **A total, never a share.** The query counts and the screen divides, on
 * purpose. Percentages computed down here would be correct exactly as long as
 * a completion belongs to at most one tag, and PRD §8's OQ-1 settled that
 * multi-tag is coming: the day a completion can carry two tags, shares stop
 * summing to 100% and someone has to choose between fractional and full
 * attribution. That choice belongs to the schema bump. A number that silently
 * becomes wrong is worse than one that has to be divided at the call site.
 *
 * **[tag] is null for untagged habits, and that row is real.** Untagged effort
 * is a visible slice rather than a silent omission — drop it and the remaining
 * percentages describe a whole that is not the whole. Callers must render it,
 * not filter it.
 */
data class TagEffort(val tag: String?, val completions: Int)
