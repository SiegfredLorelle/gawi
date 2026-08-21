package com.gawi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import com.gawi.core.data.repository.HabitRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * Today's habits on the home screen, one tap each (PRD §4, §6.1).
 *
 * **The read is a snapshot, deliberately not a collected `Flow`.** Glance's
 * session is started and torn down by the framework around update requests, so
 * a `collectAsState` here keeps the widget current only for as long as a session
 * happens to be alive — which is exactly why architecture §4 makes the refresh
 * explicit rather than reactive. What keeps this current is
 * [GlanceProjectionListener], called after the projection transaction commits.
 * A reviewer will suggest `collectAsState`; this paragraph is the answer.
 *
 * **What the snapshot cannot cover, and what follows from it.** Nothing commits
 * at a day boundary — a rollover is not an event — so no listener can fire for
 * one, and a widget left on a launcher across the cutoff shows yesterday's
 * ticks. The provider's `updatePeriodMillis` bounds how long that lasts; an
 * exact boundary refresh wants WorkManager and arrives with the reminder
 * (docs/ux/widget.md §4). The consequence for correctness is handled where it
 * matters instead: the tap path re-reads rather than trusting what was drawn.
 *
 * `internal` is a Kotlin visibility statement only — this class is instantiated
 * reflectively, so it compiles to a public JVM class with a no-arg constructor
 * and must keep one. It will also need a keep rule if minification is ever
 * turned on (it is off today, see `AndroidApplicationConventionPlugin`).
 */
internal class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = readState(repositoryFrom(context))
        provideContent { WidgetBody(state) }
    }
}

/**
 * Reads one snapshot, or null if reading failed.
 *
 * A widget has no snackbar and no retry, so the only honest thing a failure can
 * do is say so — silence would draw an empty habit list, which is
 * indistinguishable from having no habits and is the same
 * failure-resolving-towards-silence the export nudge took three rounds to stamp
 * out. Both reads behind this can genuinely throw: `SQLiteException` is a
 * `RuntimeException` unrelated to `IOException`, and the settings store refuses
 * to guess a cutoff rather than serve a default.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
private suspend fun readState(repository: HabitRepository): WidgetUiState? = try {
    repository.observeToday().first().toWidgetState()
} catch (e: Exception) {
    // Rethrows cancellation, which is the behaviour wanted, and sidesteps
    // detekt's RethrowCaughtException at the same time.
    currentCoroutineContext().ensureActive()
    null
}

internal fun repositoryFrom(context: Context): HabitRepository =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).habitRepository()

@Composable
private fun WidgetBody(state: WidgetUiState?) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(WIDGET_PADDING.dp),
        ) {
            when {
                state == null -> Message(R.string.widget_unavailable)
                state.rows.isEmpty() -> Message(R.string.widget_no_habits)
                else -> HabitRows(state.rows)
            }
        }
    }
}

@Composable
private fun HabitRows(rows: List<WidgetRow>) {
    LazyColumn {
        items(rows) { row ->
            CheckBox(
                checked = row.completed,
                onCheckedChange = actionRunCallback<ToggleHabitAction>(actionParametersOf(HABIT_ID to row.habitId)),
                text = row.name,
            )
        }
    }
}

@Composable
private fun Message(resId: Int) {
    Text(text = LocalContext.current.getString(resId))
}

private const val WIDGET_PADDING = 8
