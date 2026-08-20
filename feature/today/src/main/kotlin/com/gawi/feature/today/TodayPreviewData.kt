package com.gawi.feature.today

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.gawi.core.domain.mascot.Mood
import com.gawi.core.domain.model.HabitId
import com.gawi.core.ui.theme.GawiTheme
import com.gawi.core.ui.theme.parseHabitColor
import java.time.LocalDate

/**
 * The screen's states, drawn.
 *
 * These are the review surface for docs/ux/today-view.md §5 while there is no
 * create form to make real rows with: every variant the rules produce — a live
 * daily streak, a weekly one in weeks with its progress, a broken one with what
 * was lost, and a habit that has never run — is an artboard someone can look at.
 */
// A canonical UUIDv7 with the last digit varied, spelled out rather than
// computed so this file holds no arithmetic worth reading.
private fun previewId(last: String): HabitId = HabitId("00000000-0000-7000-8000-00000000000$last")

// Named so each artboard says which §5 rule it is there to show.
private const val LIVE_DAY_STREAK = 12
private const val LIVE_WEEK_STREAK = 3
private const val LOST_DAY_STREAK = 4
private const val WEEKLY_DONE = 2
private const val WEEKLY_TARGET = 3
private const val OUTSTANDING_COUNT = 2

private val PREVIEW_ROWS = listOf(
    HabitRowUi(
        id = previewId("1"),
        name = "read",
        icon = "📖",
        iconTint = parseHabitColor("#7E57C2"),
        completed = true,
        weekProgress = null,
        streak = StreakUi.Days(count = LIVE_DAY_STREAK),
    ),
    HabitRowUi(
        id = previewId("2"),
        name = "exercise",
        icon = "🏃",
        iconTint = parseHabitColor("#26A69A"),
        completed = false,
        weekProgress = WeekProgress(done = WEEKLY_DONE, target = WEEKLY_TARGET),
        streak = StreakUi.Weeks(count = LIVE_WEEK_STREAK),
    ),
    HabitRowUi(
        id = previewId("3"),
        name = "journal",
        icon = "✍",
        iconTint = parseHabitColor("#EF6C00"),
        completed = false,
        weekProgress = null,
        streak = StreakUi.Broken(previous = LOST_DAY_STREAK, weekly = false),
    ),
    HabitRowUi(
        id = previewId("5"),
        // A parseable but unusable colour. The theme's content role would be
        // invisible on this in light mode, so the glyph picks its own.
        name = "meditate",
        icon = "M",
        iconTint = parseHabitColor("#000000"),
        completed = false,
        weekProgress = null,
        streak = StreakUi.None,
    ),
    HabitRowUi(
        id = previewId("6"),
        // Translucent, so what the glyph really sits on is this blended with
        // the surface behind it rather than the colour as written.
        name = "walk",
        icon = "W",
        iconTint = parseHabitColor("#40FFFFFF"),
        completed = false,
        weekProgress = null,
        streak = StreakUi.None,
    ),
    HabitRowUi(
        id = previewId("4"),
        // An unparseable colour, which the event log can hold and a row has to
        // survive: this one falls back to a theme role.
        name = "stretch",
        icon = "?",
        iconTint = parseHabitColor("not a colour"),
        completed = false,
        weekProgress = null,
        streak = StreakUi.None,
    ),
)

private val PREVIEW_STATE = TodayUiState.Habits(
    rows = PREVIEW_ROWS,
    mood = Mood.WORRIED,
    remaining = OUTSTANDING_COUNT,
    logicalDate = LocalDate.parse("2026-08-17"),
)

/** Inert: a preview has nowhere to navigate and nothing to write. */
private val PREVIEW_ACTIONS = TodayActions(
    onToggle = { _, _, _ -> },
    onAddHabit = {},
    onManageHabits = {},
)

@Preview(name = "habits", showBackground = true)
@Composable
private fun TodayHabitsPreview() {
    GawiTheme {
        TodayScreen(PREVIEW_STATE, PREVIEW_ACTIONS, SnackbarHostState())
    }
}

@Preview(name = "habits dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayHabitsDarkPreview() {
    GawiTheme {
        TodayScreen(PREVIEW_STATE, PREVIEW_ACTIONS, SnackbarHostState())
    }
}

@Preview(name = "empty", showBackground = true)
@Composable
private fun TodayEmptyPreview() {
    GawiTheme {
        TodayScreen(TodayUiState.Empty(Mood.CONTENT), PREVIEW_ACTIONS, SnackbarHostState())
    }
}

@Preview(name = "loading", showBackground = true)
@Composable
private fun TodayLoadingPreview() {
    GawiTheme {
        TodayScreen(TodayUiState.Loading, PREVIEW_ACTIONS, SnackbarHostState())
    }
}
