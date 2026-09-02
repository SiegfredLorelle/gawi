package com.gawi.feature.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign

/**
 * A value over a label, one equal-width column per item — the table view under a
 * [Sparkline], and the thing its dot positions are coupled to.
 *
 * Shared by the rate card and the trend card because `Sparkline` centres its
 * marks at `(index + 0.5) / size` on the promise that the row beneath it is
 * exactly this: `size` equal `weight(1f)` columns. Two hand-copied rows would
 * hold that promise twice, and a padding change to one would misalign one
 * card's dots while the other still lined up, with nothing in the suite able to
 * see it.
 *
 * [spoken] is a column's whole announcement when the visible label is not
 * enough on its own — the trend's initials — and it *replaces* the two texts
 * rather than preceding them: merged under a description, TalkBack 17 read
 * both, *"August, 30 active days. 30. capital A"* (2026-09-02, docs/running.md
 * §4). Null when the two texts already say it, so the rate card's rows keep
 * announcing as they did.
 */
@Composable
internal fun <T> LabelledColumns(
    items: List<T>,
    value: (T) -> String,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    spoken: ((T) -> String)? = null,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            val description = spoken?.invoke(item)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (description != null) {
                            Modifier.clearAndSetSemantics { contentDescription = description }
                        } else {
                            Modifier
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = value(item), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                Text(
                    text = label(item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
