package com.gawi.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.gawi.core.ui.theme.GawiSpacing

/**
 * Centred two-line copy — the shape an empty state and a failed read both take.
 *
 * Takes strings rather than string resource ids, so a caller resolves its own
 * copy and this stays usable from a module whose resources it cannot see.
 *
 * Shared because both feature modules draw it, for the same two states, and
 * because an empty state saying the wrong thing is a bug this project has
 * already shipped once (docs/ux/today-view.md §4's rule 0).
 */
@Composable
fun Notice(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(GawiSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
