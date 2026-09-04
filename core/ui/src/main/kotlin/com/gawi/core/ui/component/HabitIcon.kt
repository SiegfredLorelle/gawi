package com.gawi.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import com.gawi.core.ui.theme.GawiSpacing
import com.gawi.core.ui.theme.glyphColorOn

/**
 * A habit's icon on its colour — the one place a habit's colour appears
 * (docs/ux/today-view.md §5).
 *
 * The icon is drawn as text because `HabitState.icon` has no vocabulary yet: it
 * is a bare string off the event log, and the create form that will give it one
 * does not exist. Text is right if that turns out to be an emoji and is a
 * visible placeholder if it does not, which beats inventing a registry here.
 *
 * **Shared rather than duplicated**, for the reason
 * [com.gawi.core.ui.theme.parseHabitColor] gives for itself: three copies of a
 * contrast decision is three answers to "what colour is legible on this". The
 * Today row, the habits list and habit detail all drew this separately, so
 * changing [glyphColorOn]'s contract or either fallback below meant changing
 * three files, and fixing two of the three would have looked exactly like
 * fixing it. That is not hypothetical here — the widget shipped a whole phase
 * drawing near-black text on a near-black background (docs/ux/widget.md §5).
 *
 * [tint] is nullable because the stored colour is unvalidated and may not parse;
 * null falls back to the theme's `secondaryContainer`, which is how a habit with
 * no colour, or a corrupt one, still draws.
 *
 * **It has no semantics.** The three callers all put the habit's name beside
 * it, so to a screen reader the badge is decoration — and a device reads it as
 * a stop of its own on the habit list and as a leading "books" on a Today row.
 * The editor's icon picker is the one place an emoji is the label rather than
 * beside one, and it draws its own `Text` for that reason.
 * `row_doesNotSpeakTheIcon` (today) and `iconBadge_isDecorative` (habits) hold
 * this from the two features, since this module has no Compose tests of its
 * own.
 */
@Composable
fun HabitIcon(icon: String, tint: Color?, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleSmall) {
    Box(
        modifier = modifier
            .size(GawiSpacing.IconBox)
            .clip(CircleShape)
            .background(tint ?: MaterialTheme.colorScheme.secondaryContainer)
            // Decorative: every caller draws the habit's name beside this, and
            // the icon is a free string off the log with no name to give it.
            // Cleared here rather than at each call site for the reason the
            // badge is shared at all.
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = icon,
            style = style,
            // The glyph cannot take a theme role, because what it sits on is
            // unvalidated: parseHabitColor checks that the stored string is a
            // colour, deliberately not that it is a usable one. A pure black
            // habit would otherwise draw a dark glyph on itself in light mode.
            //
            // The background is passed in because the tint may be translucent,
            // in which case what the glyph really sits on is the two composited
            // — see glyphColorOn.
            color = tint?.let { glyphColorOn(it, MaterialTheme.colorScheme.background) }
                ?: MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
