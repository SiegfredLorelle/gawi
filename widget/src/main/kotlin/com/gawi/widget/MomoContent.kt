package com.gawi.widget

import androidx.annotation.StringRes
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.mascot.Mascot
import com.gawi.core.domain.mascot.Mood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What the Momo widget has to draw right now (docs/ux/widget.md §7).
 *
 * Three states, for the reason [WidgetContent] has three: "not read yet" and
 * "could not be read" must not look alike, and a guessed face on a failed read
 * would be the wrong answer rather than a blank one.
 */
internal sealed interface MomoContent {

    /** Before the first emission arrives. */
    data object Loading : MomoContent

    /** The read threw, past [retryThenFail]'s retries. */
    data object Unavailable : MomoContent

    /**
     * The mood the Today screen would show, and whether there is anything to
     * have a mood *about*. With no habits `Mascot` says CONTENT, and the widget
     * still draws her — but under the no-habits copy rather than a mood word, so
     * a fresh install reads the same thing the Today widget reads.
     */
    data class Ready(val mood: Mood, val empty: Boolean) : MomoContent
}

/**
 * The one word drawn under the face — the caption the design canvas chose
 * 2026-08-29 over the full sentence (which clips at 110dp) and over no caption
 * (which leaves a greyscale viewer no word). TalkBack does not read these:
 * the face carries the full [description] once, and the word is decorative.
 */
@StringRes
internal fun Mood.caption(): Int = when (this) {
    Mood.THRIVING -> R.string.widget_momo_caption_thriving
    Mood.CONTENT -> R.string.widget_momo_caption_content
    Mood.WORRIED -> R.string.widget_momo_caption_worried
    Mood.REGENERATING -> R.string.widget_momo_caption_regenerating
}

/**
 * The Momo widget's read: `observeToday()` and nothing new, the same call the
 * Today widget and the Today screen make, so the three faces cannot disagree.
 * docs/ux/visual-identity.md §7.4 once priced a read this repository does not
 * serve; `moodInputs()` on the same snapshot is the whole input.
 */
internal fun HabitRepository.momoContent(): Flow<MomoContent> = observeToday()
    .map<TodaySnapshot, MomoContent> { it.toMomoContent() }
    .retryThenFail(MomoContent.Unavailable)

/** The snapshot as the Momo widget sees it. Pure, so it is tested without Glance. */
internal fun TodaySnapshot.toMomoContent(): MomoContent.Ready =
    MomoContent.Ready(mood = Mascot.mood(moodInputs()), empty = habits.isEmpty())
