package com.gawi.feature.insights

import com.gawi.core.data.model.TagEffort

/**
 * The sentence, or none.
 *
 * "Focus" is the tag with the largest total, and only a **tagged** one: untagged
 * is what is left over, not a thing the user chose to work on. Ties break the
 * way the bars sort — largest first, then by name — so the sentence can never
 * name a tag the list draws second.
 *
 * A shift or a hold is claimed only for a [complete] period, against the one
 * before it, and only when both had a tagged completion: a habit tagged for the
 * first time this quarter did not shift the focus from anywhere. The current
 * period is compared with nothing — it is a partial figure against a whole one,
 * and on its first day a single completion would carry the claim — so it reads
 * "so far" and names the leader.
 */
internal fun focusOf(previous: List<TagEffort>, current: List<TagEffort>, complete: Boolean): FocusShiftUi? {
    val now = current.topTag()
    val before = previous.topTag()
    return when {
        now == null -> null
        !complete -> FocusShiftUi.SoFar(now)
        before == null -> null
        before == now -> FocusShiftUi.Held(now)
        else -> FocusShiftUi.Shifted(from = before, to = now)
    }
}

private fun List<TagEffort>.topTag(): String? = filter { it.tag != null && it.completions > 0 }
    .sortedWith(compareBy({ -it.completions }, { it.tag }))
    .firstOrNull()
    ?.tag
