package com.gawi.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.gawi.core.ui.theme.GawiSpacing

/**
 * One habit, as §5 draws it: a colour-tinted icon, the name, a weekly habit's
 * progress under it, and the streak at the end.
 *
 * The whole row is the tap target, not the checkbox. PRD §6.1 wants a
 * completion in one tap, and giving the row the toggle also means assistive
 * technology reads one node with a checkbox role rather than a label and a
 * control that happen to sit together.
 */
@Composable
internal fun HabitRow(row: HabitRowUi, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val completeLabel = stringResource(if (row.completed) R.string.today_undo else R.string.today_complete)
    Row(
        modifier = modifier
            .toggleable(
                value = row.completed,
                role = Role.Checkbox,
                // Without a label the row announces only its name and state, so
                // the action it performs is left implicit.
                onValueChange = onToggle,
            )
            .semantics {
                onClick(label = completeLabel, action = null)
            }
            .padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        HabitIcon(row)
        HabitTitles(row, Modifier.weight(1f))
        StreakBadge(row.streak)
        Checkbox(
            checked = row.completed,
            // The row owns the click. A checkbox that also handled it would be
            // a second tap target inside the first.
            onCheckedChange = null,
        )
    }
}

/**
 * §5 puts the habit's colour in exactly one place: behind its icon.
 *
 * The icon is drawn as text because `HabitState.icon` has no vocabulary yet —
 * it is a bare string off the event log, and the create form that will give it
 * one does not exist. Text is right if that turns out to be an emoji and is a
 * visible placeholder if it does not, which beats inventing a registry here.
 */
@Composable
private fun HabitIcon(row: HabitRowUi) {
    Box(
        modifier = Modifier
            .size(GawiSpacing.IconBox)
            .clip(CircleShape)
            .background(row.iconTint ?: MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = row.icon, style = MaterialTheme.typography.titleSmall)
    }
}

/** The name, and for a weekly habit the "2/3 this week" §5 asks for. */
@Composable
private fun RowScope.HabitTitles(row: HabitRowUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line)) {
        Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
        row.weekProgress?.let { progress ->
            Text(
                text = stringResource(R.string.today_week_progress, progress.done, progress.target),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
