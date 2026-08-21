package com.gawi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Today's habits on the home screen, one tap each (PRD §4, §6.1).
 *
 * **The flow is collected inside `provideContent`, and that is a correction of
 * an earlier decision here.** This file used to read one snapshot before
 * `provideContent` and rely entirely on [GlanceProjectionListener] to push
 * redraws, with a KDoc arguing that a collected flow was "current-if-lucky".
 * That was backwards, and `/code-review` caught it. Measured against
 * `glance-appwidget-1.1.1` bytecode: `AppWidgetSession` collects
 * `runGlance` — which is what invokes this function — with `collectAsState`,
 * **once per session**. So `update`/`updateAll` arriving while a session is
 * already alive does *not* re-enter `provideGlance`; it re-reads only the state
 * definition, which this widget does not use. A one-shot read therefore froze
 * the content for the life of the session: complete one habit, complete a second
 * five seconds later, and the home screen would show the first ticked and the
 * second not, for up to the provider's 30-minute update period.
 *
 * **So the two mechanisms are both needed, and they cover different cases.**
 * Collecting keeps a *live* session tracking Room, through the same
 * `InvalidationTracker` every screen uses. [GlanceProjectionListener] is what
 * starts a session at all when none is alive, which is the common case, since
 * sessions are short-lived. Neither alone is sufficient.
 *
 * **What still cannot be covered.** A day rollover is not an event and a
 * settings edit is not one either, so neither commits and neither can be pushed.
 * The cutoff is the one that bites, because it decides the logical date and so
 * every `completedToday`. `observeToday()` re-emits on both by itself, which
 * means a live session does follow them — but a widget with no session shows the
 * previous answer until `updatePeriodMillis` comes round. docs/ux/widget.md §4
 * has the whole argument. The consequence for correctness is handled where it
 * matters instead: the tap path re-reads rather than trusting what was drawn.
 *
 * `internal` is a Kotlin visibility statement only — this class is instantiated
 * reflectively, so it compiles to a public JVM class with a no-arg constructor
 * and must keep one. It will also need a keep rule if minification is ever
 * turned on (it is off today, see `AndroidApplicationConventionPlugin`).
 */
internal class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Built here rather than inside provideContent. Flow operators must not
        // be invoked in composition — they allocate a new flow per
        // recomposition, and Android Lint's FlowOperatorInvokedInComposition is
        // fatal here (warningsAsErrors). So the pipeline is assembled once in
        // this suspend function and only the collection happens in composition.
        //
        // The catch sits after the map, so a failed read replaces the whole
        // content rather than one row, which is what Unavailable means. Same
        // accepted pattern as TodayViewModel, including that a caught read
        // completes the flow (docs/ux/settings.md §7).
        val content = repositoryFrom(context)
            .observeToday()
            .map<TodaySnapshot, WidgetContent> { WidgetContent.Ready(it.toWidgetState()) }
            .catch { emit(WidgetContent.Unavailable) }

        provideContent {
            val current by content.collectAsState(initial = WidgetContent.Loading)
            WidgetBody(current)
        }
    }
}

internal fun repositoryFrom(context: Context): HabitRepository =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).habitRepository()

@Composable
private fun WidgetBody(content: WidgetContent) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(WIDGET_PADDING.dp),
        ) {
            when {
                content is WidgetContent.Unavailable -> Message(R.string.widget_unavailable)

                content is WidgetContent.Ready && content.state.rows.isEmpty() -> Message(R.string.widget_no_habits)

                content is WidgetContent.Ready -> HabitRows(content.state.rows)

                // Loading draws nothing rather than copy of its own. It is the
                // first frame of a cold render and is replaced immediately; a
                // "loading" line would be the only text most renders ever showed.
                else -> Unit
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
