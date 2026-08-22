package com.gawi.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.gawi.core.ui.component.Notice
import com.gawi.core.ui.streak.StreakUi
import com.gawi.core.ui.theme.GawiSpacing
import com.gawi.core.ui.theme.glyphColorOn

/**
 * One habit, stateless.
 *
 * PRD §6.6's second streak surface. The streak is the screen's subject rather
 * than a trailing badge — this is where a streak is read deliberately, which is
 * the whole argument widget.md §2 used to keep it off the widget — so it is
 * drawn large and captioned in its own unit.
 *
 * Editing lives behind the app bar's one action rather than on this screen.
 * Detail is for looking at a habit you are doing; the editor is for changing
 * what it is, and keeping them apart is what lets this screen be read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitDetailScreen(
    state: HabitDetailUiState,
    actions: HabitDetailActions,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.habits_detail_title)) },
                navigationIcon = { GlyphButton("←", R.string.habits_back, actions.onBack) },
                actions = {
                    // Only offered once there is a habit to edit. On Loading and
                    // Unavailable there is no id to hand back, and an action that
                    // navigates nowhere is worse than an absent one.
                    if (state is HabitDetailUiState.Detail) {
                        GlyphButton("✎", R.string.habits_detail_edit) { actions.onEdit(state.id) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        // targetSdk 37 draws edge to edge with no opt-out, so every branch has
        // to honour the insets or its first row sits under the status bar.
        when (state) {
            // Blank rather than a spinner: the first emission is one Room query.
            HabitDetailUiState.Loading -> Box(Modifier.fillMaxSize().padding(insets))

            HabitDetailUiState.Unavailable -> Notice(
                title = stringResource(R.string.habits_editor_unavailable_title),
                body = stringResource(R.string.habits_editor_unavailable_body),
                modifier = Modifier.fillMaxSize().padding(insets),
            )

            is HabitDetailUiState.Detail -> HabitDetail(
                state = state,
                actions = actions,
                modifier = Modifier.fillMaxSize().padding(insets),
            )
        }
    }
}

@Composable
private fun HabitDetail(state: HabitDetailUiState.Detail, actions: HabitDetailActions, modifier: Modifier = Modifier) {
    // The cell awaiting the honesty prompt, as an epoch day.
    //
    // A LocalDate cannot go in a Bundle, so rememberSaveable stores the Long and
    // the cell is looked back up from the state — the same trap SettingsPickers
    // documents for a value class over an Int. Only the date is held: whether
    // that day is completed comes from the strip, so a write in flight from
    // elsewhere cannot leave the prompt confirming a stale intent.
    var pendingDay by rememberSaveable { mutableLongStateOf(NO_PENDING_DAY) }
    val pending = state.strip.firstOrNull { it.date.toEpochDay() == pendingDay }

    // The cell whose note is open, held the same way and for the same reason.
    // Two values rather than one enum, unlike SettingsDialog: each carries which
    // day it is about, and they cannot both be set — a cell is either awaiting
    // the prompt or being annotated, never both.
    var noteDay by rememberSaveable { mutableLongStateOf(NO_PENDING_DAY) }
    val noted = state.strip.firstOrNull { it.date.toEpochDay() == noteDay }

    Column(
        // Scrolls like the editor and settings do. At a large system font scale
        // the header, the displaySmall streak and the 48dp strip overflow a
        // short screen, and the strip is the one thing here you can touch.
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(GawiSpacing.Row),
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Row),
    ) {
        HabitHeader(state)
        StreakPanel(state.streak)
        TodayLine(state)
        RetroStrip(
            strip = state.strip,
            // Today writes straight through; PRD §6.4 keeps same-day logging and
            // undo frictionless. Any earlier day is an edit to the past and takes
            // the prompt, whichever direction it goes in.
            onCell = { cell ->
                if (cell.isToday) {
                    actions.onToggle(state.id, cell.date, cell.completed)
                } else {
                    pendingDay = cell.date.toEpochDay()
                }
            },
            onCellNote = { cell -> noteDay = cell.date.toEpochDay() },
        )
    }

    if (pending != null) {
        HonestyPrompt(
            onConfirm = {
                actions.onToggle(state.id, pending.date, pending.completed)
                pendingDay = NO_PENDING_DAY
            },
            onDismiss = { pendingDay = NO_PENDING_DAY },
        )
    }

    if (noted != null) {
        NoteSheet(
            cell = noted,
            onSave = { text ->
                actions.onNote(state.id, noted.date, text)
                noteDay = NO_PENDING_DAY
            },
            onDismiss = { noteDay = NO_PENDING_DAY },
        )
    }
}

/**
 * The note sheet's surface.
 *
 * A `ModalBottomSheet` because docs/ux/today-view.md §5 calls this a sheet, and
 * the first one in the app — every other overlay here is an `AlertDialog`. Its
 * content is [NoteSheetContent], a separate stateless composable, so what the
 * buttons report can be asserted without driving a sheet's animation.
 *
 * Clearing goes through the same [onSave], with empty text — one write either
 * way (architecture §4: an empty note is a real write that wins
 * last-write-wins), so a second callback would be two names for one event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(cell: RetroCellUi, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        NoteSheetContent(
            dayOfMonth = cell.dayOfMonth,
            initial = cell.note.orEmpty(),
            onSave = onSave,
            onCancel = onDismiss,
        )
    }
}

/**
 * PRD §5's confirmation, in PRD §5's words.
 *
 * §6.4 asks retroactive edits to "carry deliberate friction but stay possible".
 * It is friction and nothing more: architecture §5 is explicit that the 3-day
 * window is a *command* validation and that this prompt has nothing enforcing
 * it, so dismissing has to leave the log untouched rather than merely
 * postponing a write.
 *
 * Shown for an undo as well as a completion. Both are edits to a day that has
 * already passed, and un-ticking a day you did do is as much a rewrite of the
 * record as ticking one you did not.
 */
@Composable
private fun HonestyPrompt(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habits_retro_title)) },
        text = { Text(stringResource(R.string.habits_retro_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.habits_retro_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.habits_cancel)) }
        },
    )
}

/** The habit's identity: its colour, its icon, its name, and what it asks for. */
@Composable
private fun HabitHeader(state: HabitDetailUiState.Detail) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        HabitGlyph(state)
        Column(verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line)) {
            Text(text = state.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = when (val schedule = state.schedule) {
                    ScheduleUi.Daily -> stringResource(R.string.habits_schedule_daily)
                    is ScheduleUi.Weekly -> stringResource(R.string.habits_schedule_weekly, schedule.timesPerWeek)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Both are absent far more often than present, so neither reserves
            // a line. An untagged, unarchived habit draws nothing here at all.
            state.tag?.let { tag ->
                Text(
                    text = stringResource(R.string.habits_detail_tag, tag),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.archived) {
                Text(
                    text = stringResource(R.string.habits_detail_archived),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** The habit's colour, in the one place it appears — behind its icon. */
@Composable
private fun HabitGlyph(state: HabitDetailUiState.Detail) {
    val tint = state.iconTint
    Box(
        modifier = Modifier
            .size(GawiSpacing.IconBox)
            .clip(CircleShape)
            .background(tint ?: MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.icon,
            style = MaterialTheme.typography.titleMedium,
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
 * The streak, drawn by unit.
 *
 * docs/ux/today-view.md §5 says a daily streak and a weekly one must never be
 * styled as the same number. They differ here three ways over — the count
 * carries a `w`, the caption names its unit, and the colour role differs — so
 * the distinction survives a reader who cannot tell the two colours apart.
 *
 * The rendering is this screen's own; only the `StreakUi` decision behind it is
 * shared with the Today row, which draws the same state as a compact badge.
 */
@Composable
private fun StreakPanel(streak: StreakUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line),
    ) {
        when (streak) {
            // today-view §5's rule about never reading zero is about a live streak, which
            // this is not: nothing has ever run, so there is no number to draw.
            StreakUi.None -> Text(
                text = stringResource(R.string.habits_detail_streak_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            is StreakUi.Days -> StreakCount(
                count = stringResource(R.string.habits_detail_streak_days, streak.count),
                caption = stringResource(R.string.habits_detail_streak_days_caption),
                color = MaterialTheme.colorScheme.primary,
            )

            is StreakUi.Weeks -> StreakCount(
                count = stringResource(R.string.habits_detail_streak_weeks, streak.count),
                caption = stringResource(R.string.habits_detail_streak_weeks_caption),
                color = MaterialTheme.colorScheme.tertiary,
            )

            is StreakUi.Broken -> BrokenStreak(streak)
        }
    }
}

@Composable
private fun StreakCount(count: String, caption: String, color: Color) {
    Text(text = count, style = MaterialTheme.typography.displaySmall, color = color)
    Text(
        text = caption,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Zero, with what was lost kept beside it — today-view §5's "was 4" and its cut thread. */
@Composable
private fun BrokenStreak(streak: StreakUi.Broken) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        Text(
            text = stringResource(R.string.habits_detail_streak_broken_glyph),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = stringResource(R.string.habits_detail_streak_broken),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
    Text(
        text = stringResource(
            if (streak.weekly) R.string.habits_detail_streak_was_weeks else R.string.habits_detail_streak_was_days,
            streak.previous,
        ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Where the habit stands on the day being shown.
 *
 * The week line is drawn for a weekly habit only, which is today-view §5's rule and the
 * same one the Today row follows — a detail screen that disagreed with the row
 * that led to it would be its own bug.
 */
@Composable
private fun TodayLine(state: HabitDetailUiState.Detail) {
    Column(verticalArrangement = Arrangement.spacedBy(GawiSpacing.Line)) {
        Text(
            text = stringResource(
                if (state.completedToday) R.string.habits_detail_done else R.string.habits_detail_not_done,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        state.weekProgress?.let { progress ->
            Text(
                text = stringResource(R.string.habits_detail_week_progress, progress.done, progress.target),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** No cell is awaiting confirmation. Not a valid epoch day for any real date drawn here. */
private const val NO_PENDING_DAY = Long.MIN_VALUE
