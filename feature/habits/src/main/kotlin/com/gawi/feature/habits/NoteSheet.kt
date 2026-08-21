package com.gawi.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.gawi.core.ui.theme.GawiSpacing

/**
 * The note on one completed day.
 *
 * PRD §5 puts an optional note on a completion, reached by "long-press / detail
 * view", and §6.3 requires that notes "never add friction to the base flow" —
 * which is why this is behind a long-press on a day already logged rather than
 * anywhere on the way to logging one.
 *
 * **Clear is a button, not a disabled Save** (docs/ux/today-view.md §5). An
 * empty note is a real write that clears the note and wins last-write-wins like
 * any other (architecture §4), so the sheet offers it explicitly rather than
 * leaving someone to guess that saving nothing means removing something. It
 * reports through [onSave] with empty text, because it *is* that write — a
 * separate callback would be two names for one event. What §5 asks for is the
 * affordance, and that is what the button is.
 *
 * Stateless apart from the field itself, and separate from the surface that
 * hosts it, so a test can render this directly and assert what each button
 * reports without driving a bottom sheet's animation.
 */
@Composable
internal fun NoteSheetContent(
    date: String,
    initial: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Seeded from the note that is there and edited locally, so Cancel means
    // nothing changed — the rule every dialog in :feature:settings follows.
    // rememberSaveable takes a String directly, unlike the strip's LocalDate.
    var text by rememberSaveable(initial) { mutableStateOf(initial) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(GawiSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        Text(
            text = stringResource(R.string.habits_note_title, date),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { edited -> text = edited },
            label = { Text(stringResource(R.string.habits_note_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.habits_save)) }
            // Offered only when there is something to remove. A clear that wrote
            // an empty note over an already-empty one would append an event that
            // changed nothing.
            if (initial.isNotEmpty()) {
                TextButton(onClick = { onSave("") }) { Text(stringResource(R.string.habits_note_clear)) }
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.habits_cancel)) }
        }
    }
}
