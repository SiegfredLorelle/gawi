package com.gawi.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gawi.core.ui.component.Notice
import com.gawi.core.ui.theme.GawiSpacing
import com.gawi.core.ui.theme.glyphColorOn

/**
 * The habit list, stateless.
 *
 * Both sections are always drawn. There is no show-archived toggle, because a
 * habit you have put away is exactly the one you need to be able to find to
 * bring back, and a heading says where it went without holding any state that
 * can get out of step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitListScreen(
    state: HabitListUiState,
    actions: HabitListActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.habits_title)) },
                navigationIcon = { GlyphButton("←", R.string.habits_back, actions.onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { AddHabitButton(actions.onAdd) },
    ) { insets ->
        // targetSdk 37 draws edge to edge with no opt-out, so every branch has
        // to honour the insets or its first row sits under the status bar.
        when (state) {
            // Blank rather than a spinner: the first emission is one Room query.
            HabitListUiState.Loading -> Box(Modifier.fillMaxSize().padding(insets))

            HabitListUiState.Unavailable -> Notice(
                title = stringResource(R.string.habits_unavailable_title),
                body = stringResource(R.string.habits_unavailable_body),
                modifier = Modifier.fillMaxSize().padding(insets),
            )

            HabitListUiState.Empty -> Notice(
                title = stringResource(R.string.habits_empty_title),
                body = stringResource(R.string.habits_empty_body),
                modifier = Modifier.fillMaxSize().padding(insets),
            )

            is HabitListUiState.Habits -> HabitList(
                state = state,
                actions = actions,
                modifier = Modifier.fillMaxSize().padding(insets),
            )
        }
    }
}

@Composable
private fun HabitList(state: HabitListUiState.Habits, actions: HabitListActions, modifier: Modifier = Modifier) {
    // Scaffold's content padding covers the bars and the window insets but not
    // the floating action button, which is drawn over the content. Without this
    // the last row sits under the FAB and the FAB takes the touch, so its
    // archive button cannot be reached once there are enough habits to fill the
    // screen.
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = FAB_CLEARANCE)) {
        items(state.active, key = { it.id.value }) { row -> HabitManageRow(row, actions) }
        if (state.archived.isNotEmpty()) {
            item(key = ARCHIVED_HEADER_KEY) {
                Text(
                    text = stringResource(R.string.habits_archived_header),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = GawiSpacing.Row, vertical = GawiSpacing.Gap),
                )
            }
            items(state.archived, key = { it.id.value }) { row -> HabitManageRow(row, actions) }
        }
    }
}

/**
 * One habit, with the two things you can do to it.
 *
 * The title block opens the editor and the trailing button archives, rather
 * than the row itself toggling archived. Archiving is the one of the two that
 * feels destructive, so it gets its own target and its own word instead of
 * being what happens when you meant to tap a name.
 */
@Composable
private fun HabitManageRow(row: HabitListRowUi, actions: HabitListActions, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = GawiSpacing.Row, end = GawiSpacing.Gap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        HabitIcon(row)
        HabitTitles(row, Modifier.weight(1f), onEdit = { actions.onEdit(row.id) })
        TextButton(onClick = { actions.onArchiveToggle(row.id, row.archived) }) {
            Text(stringResource(if (row.archived) R.string.habits_unarchive else R.string.habits_archive))
        }
    }
}

/** The habit's colour, in the one place it appears — behind its icon. */
@Composable
private fun HabitIcon(row: HabitListRowUi) {
    val tint = row.iconTint
    Box(
        modifier = Modifier
            .size(GawiSpacing.IconBox)
            .clip(CircleShape)
            .background(tint ?: MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = row.icon,
            style = MaterialTheme.typography.titleSmall,
            // The stored colour is unvalidated, so the glyph cannot take a theme
            // role — a black habit would draw a dark glyph on itself in light
            // mode. The background is passed because a translucent tint means
            // what the glyph really sits on is the two composited.
            color = tint?.let { glyphColorOn(it, MaterialTheme.colorScheme.background) }
                ?: MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * The name and its schedule, and the target that opens the editor.
 *
 * The whole block is the target rather than the name alone, so a habit is not
 * harder to open for having a short name. `onClickLabel` rather than
 * `clearAndSetSemantics`, so the name and schedule stay readable to assistive
 * technology — and to the tests — instead of being replaced by one description.
 */
@Composable
private fun RowScope.HabitTitles(row: HabitListRowUi, modifier: Modifier = Modifier, onEdit: () -> Unit) {
    Column(
        modifier = modifier
            .clickable(onClickLabel = stringResource(R.string.habits_edit), onClick = onEdit)
            .padding(vertical = GawiSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        Text(text = row.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = when (val schedule = row.schedule) {
                ScheduleUi.Daily -> stringResource(R.string.habits_schedule_daily)
                is ScheduleUi.Weekly -> stringResource(R.string.habits_schedule_weekly, schedule.timesPerWeek)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddHabitButton(onAdd: () -> Unit) {
    val label = stringResource(R.string.habits_add)
    FloatingActionButton(onClick = onAdd, modifier = Modifier.semantics { contentDescription = label }) {
        // A glyph rather than a Material icon: material-icons-extended is not a
        // dependency here, and a whole icon pack for one plus sign is not worth
        // the download. The description above is what names it either way.
        Text(text = "+", style = MaterialTheme.typography.headlineSmall)
    }
}

/**
 * An icon button with no icon font behind it, named for assistive technology.
 *
 * Internal rather than private since habit detail landed: both screens in this
 * module draw glyph buttons, and material-icons-extended is still not a
 * dependency. `:feature:settings` keeps its own copy — feature modules cannot
 * see each other, which is the same reason `commandOrNull` exists twice.
 */
@Composable
internal fun GlyphButton(glyph: String, labelRes: Int, onClick: () -> Unit) {
    val label = stringResource(labelRes)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(text = glyph, style = MaterialTheme.typography.titleLarge)
    }
}

private const val ARCHIVED_HEADER_KEY = "archived-header"

/** The FAB's own height plus the margin above and below it. */
private val FAB_CLEARANCE = 88.dp
