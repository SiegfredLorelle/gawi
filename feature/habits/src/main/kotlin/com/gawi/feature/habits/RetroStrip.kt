package com.gawi.feature.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.gawi.core.ui.theme.GawiSpacing

/**
 * The last few days, and the ones still open to a correction.
 *
 * PRD §5 allows retroactive logging up to three days back. docs/ux/today-view.md
 * §5 decides how that limit is shown: **days outside the retro window are drawn
 * shut, not tapped and refused** — "the command rule should be readable before
 * it is hit". So the strip draws one day more than it can write to, struck
 * through, and that cell carries no click at all. A tap that produced a
 * snackbar would be the refusal §5 is arguing against.
 *
 * A shut day still shows whether it was done. It is refused, not hidden.
 *
 * Not a calendar. PRD Phase 1's Insights v1 is where a per-habit heatmap goes;
 * this is the writable window and the one day past its edge.
 */
@Composable
internal fun RetroStrip(
    strip: List<RetroCellUi>,
    onCell: (RetroCellUi) -> Unit,
    onCellNote: (RetroCellUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line)) {
        Text(
            text = stringResource(R.string.habits_strip_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
        ) {
            strip.forEach { cell -> RetroCell(cell, onCell, onCellNote) }
        }
    }
}

@Composable
private fun RowScope.RetroCell(cell: RetroCellUi, onCell: (RetroCellUi) -> Unit, onCellNote: (RetroCellUi) -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            // 48dp is the touch-target floor, and `toggleable` does not apply
            // minimumInteractiveComponentSize the way a Material component does
            // — the same note HabitEditorPickers carries about `selectable`.
            .defaultMinSize(minHeight = TOUCH_TARGET)
            .cellSurface(cell)
            .cellAction(cell, onCell, onCellNote)
            .padding(vertical = GawiSpacing.Gap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        Text(
            text = stringResource(cell.dayLabel),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = cell.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            // Struck through is §5's treatment of a shut day. The dimmed outline
            // and glyph carry it too, so the state survives a reader who cannot
            // make out a thin line through a two-digit number.
            textDecoration = if (cell.open) null else TextDecoration.LineThrough,
            color = if (cell.open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
        Text(
            text = if (cell.completed) DONE_GLYPH else EMPTY_GLYPH,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                cell.completed && cell.open -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            },
        )
    }
}

/**
 * Today is the only cell with a filled ground; the rest are outlined.
 *
 * A shut cell's outline is dimmed rather than dashed. §5 asks for "struck
 * through and dashed", and a true dash needs a drawn stroke rather than a
 * border — the strike-through above carries the meaning, and this keeps the
 * shut cell visibly quieter than an open empty one without a custom draw.
 */
@Composable
private fun Modifier.cellSurface(cell: RetroCellUi): Modifier {
    val shape = RoundedCornerShape(CELL_CORNER)
    return when {
        cell.isToday -> background(MaterialTheme.colorScheme.secondaryContainer, shape)
        cell.open -> border(BorderStroke(CELL_BORDER, MaterialTheme.colorScheme.outline), shape)
        else -> border(BorderStroke(CELL_BORDER, MaterialTheme.colorScheme.outlineVariant), shape)
    }
}

/**
 * A shut cell gets no click at all, and says so to assistive technology.
 *
 * `semantics { disabled() }` rather than a disabled `toggleable`: the cell is
 * not a control that happens to be off, it is a day that cannot be written to,
 * and its description already says why.
 *
 * Long-press opens the note, and only on an open day that is already completed.
 * A note hangs off a completion — architecture §4 has notes die with the add
 * they belong to — so there is nothing to annotate on an empty day, and
 * `updateNote` would reject it with `CompletionNotFound`.
 *
 * A **shut** completed day carries no note action either, even though
 * `updateNote` has no retro-window check and the domain would accept it. Shut
 * means inert here: §5's argument is that the cell should read as closed, and a
 * day that refuses a tap but answers a long-press does not.
 *
 * `combinedClickable` rather than `toggleable`, since a cell now has two
 * gestures. The checkbox role and the toggle's own semantics are restated by
 * hand so what assistive technology hears does not change.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.cellAction(cell: RetroCellUi, onCell: (RetroCellUi) -> Unit, onCellNote: (RetroCellUi) -> Unit): Modifier {
    val label = stringResource(
        when {
            !cell.open -> R.string.habits_strip_shut
            cell.completed -> R.string.habits_strip_done
            else -> R.string.habits_strip_not_done
        },
        cell.dayOfMonth,
    )
    val action = stringResource(if (cell.completed) R.string.habits_strip_undo else R.string.habits_strip_complete)
    val noteLabel = stringResource(R.string.habits_strip_note)
    // Inside the open branch below, so completion is the only condition left
    // to ask about: a shut cell never reaches it.
    val notable = cell.completed
    return if (cell.open) {
        combinedClickable(
            role = Role.Checkbox,
            onClick = { onCell(cell) },
            onLongClickLabel = noteLabel.takeIf { notable },
            onLongClick = if (notable) ({ onCellNote(cell) }) else null,
        ).semantics {
            contentDescription = if (notable) "$label. $action. $noteLabel" else "$label. $action"
            toggleableState = ToggleableState(cell.completed)
        }
    } else {
        semantics {
            contentDescription = label
            disabled()
        }
    }
}

private const val DONE_GLYPH = "✓"
private const val EMPTY_GLYPH = "·"
private val TOUCH_TARGET = 48.dp
private val CELL_CORNER = 8.dp
private val CELL_BORDER = 1.dp
