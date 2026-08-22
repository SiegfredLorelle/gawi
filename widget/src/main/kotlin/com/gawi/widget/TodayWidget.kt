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
import androidx.glance.text.TextStyle
import com.gawi.core.data.repository.HabitRepository
import dagger.hilt.android.EntryPointAccessors

/**
 * Today's habits on the home screen, one tap each (PRD §4, §6.1).
 *
 * **Two mechanisms keep it current, and both are needed.** The content collects
 * `observeToday()`, which keeps a live Glance session tracking Room through the
 * same `InvalidationTracker` every screen uses. [GlanceProjectionListener] is
 * what starts a session at all when none is alive — the common case, since
 * sessions are short-lived. Glance collects this function *once per session*, so
 * a push cannot re-enter it and collection cannot survive without the push.
 * Removing either one leaves a widget that freezes; docs/ux/widget.md §4 has the
 * measurement.
 *
 * **What neither covers.** A day rollover is not an event and neither is a
 * settings edit, so nothing commits and nothing can be pushed. `observeToday()`
 * re-emits on both, so a live session follows them, but a widget with no session
 * shows the previous answer until `updatePeriodMillis` comes round. The
 * consequence for correctness is handled in the tap path instead, which re-reads
 * rather than trusting what was drawn (see [toggleHabit]) — though across a
 * rollover that makes the tap's *visible* result invert, which
 * docs/ux/widget.md §4 spells out.
 *
 * **And they do not cover each other when the read throws**, because `catch`
 * terminates a flow and a push cannot re-enter this function. That is why
 * [widgetContent] retries before it gives up; without the retry one transient
 * failure would strand the widget on the error copy for the whole session.
 *
 * `internal` is a Kotlin visibility statement only — this class is instantiated
 * reflectively, so it compiles to a public JVM class with a no-arg constructor
 * and must keep one. It will also need a keep rule if minification is ever
 * turned on (it is off today, see `AndroidApplicationConventionPlugin`).
 */
internal class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = repositoryFrom(context).widgetContent()
        provideContent {
            val current by content.collectAsState(initial = WidgetContent.Loading)
            WidgetBody(current)
        }
    }
}

/**
 * How the widget reaches the graph.
 *
 * A `GlanceAppWidget` and an `ActionCallback` are built by the framework, not by
 * Hilt, so neither is an injection site. Resolving off the application reaches
 * the same repository singleton the app uses, which matters: it owns the command
 * mutex and the in-memory projection, so a second instance would be a second
 * command authority disagreeing in silence.
 */
internal fun repositoryFrom(context: Context): HabitRepository =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).habitRepository()

/** Draws whatever [body] decided. The choice is tested; this is only the drawing. */
@Composable
internal fun WidgetBody(content: WidgetContent) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(WIDGET_PADDING.dp),
        ) {
            when (val body = content.body()) {
                is WidgetBodyContent.Copy -> Text(text = LocalContext.current.getString(body.text), style = widgetTextStyle())
                is WidgetBodyContent.Rows -> HabitRows(body.rows)
                WidgetBodyContent.Blank -> Unit
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
                style = widgetTextStyle(),
                // No `colors`: see the note below on why the glyph cannot take
                // the theme's, and what that leaves to check by hand.
            )
        }
    }
}

/**
 * The one text style the widget draws with, and the reason it is not the default.
 *
 * Every `Text` and `CheckBox` here **must** name a colour. Glance's default text
 * colour is not theme-aware, while the container above sets its background from
 * `GlanceTheme.colors.widgetBackground`, which is — so leaving the style off gives
 * a widget whose background follows dark mode and whose text does not. Measured on
 * a Nothing A059 (Android 16) on 2026-08-22: near-black text on `#303030`, a
 * contrast ratio of **1.59:1** against WCAG's 4.5:1 floor. It rendered, so
 * `WidgetHostTest` was green throughout; it was only ever visible on a device in
 * dark mode. `WidgetTextColourDarkTest` and its light-mode twin now measure the
 * ratio in both themes, and docs/running.md §4's widget block gained the
 * by-hand check that block was missing when this shipped.
 *
 * `onSurface` rather than `onBackground` because `widgetBackground` is the surface
 * this text sits on. Both resolve correctly in either mode; this one is the pair.
 */
@Composable
private fun widgetTextStyle() = TextStyle(color = GlanceTheme.colors.onSurface)

/*
 * The checkbox glyph is deliberately NOT pinned, and this is the measurement
 * behind that — raised in review on 2026-08-22 with the reasonable suggestion
 * that it should be.
 *
 * The glyph does have the same defect shape as the label had. Left unset it
 * takes Glance's `res/color/glance_default_check_box.xml`, which is
 * `?android:attr/colorControlActivated` when checked and
 * `?android:attr/colorControlNormal` otherwise: theme attributes, with no
 * `-night` variant. A widget's `RemoteViews` are inflated by the host, so both
 * resolve in the *launcher's* theme against a background this module chose.
 *
 * `CheckBox` does take `colors`, and the obvious fix does not work.
 * `CheckboxDefaults.colors(checkedColor = GlanceTheme.colors.primary, …)`
 * compiles and then **throws at runtime** — `IllegalArgumentException: Cannot
 * provide resource-backed ColorProviders to CheckBoxColors`, from
 * `CheckedUncheckedColorProvider.<init>`. Every `GlanceTheme` colour is
 * resource-backed, so no theme colour can be handed to a checkbox. The new
 * render test caught this on the first run; the decision-only tests could not
 * have. Glance 1.1.1 offers only `ColorProvider(Color)` and
 * `ColorProvider(@ColorRes Int)` publicly, and the second is the kind being
 * rejected — so pinning means two hardcoded literals per state, chosen to work
 * in both themes without a day/night provider to express them.
 *
 * That is a palette decision, and this project does not have one yet:
 * `HabitPalette` says in as many words that it is "not a design system" because
 * Momo's palette is PRD OQ-4 and undesigned. Inventing two widget-only
 * constants here to fix an unobserved contrast problem is the wrong trade, so
 * the glyph keeps the host's tint and docs/running.md §4 keeps the by-hand
 * check. Revisit with OQ-4.
 *
 * Note also that pinning would NOT have made the glyph testable, which was the
 * stated reason to do it: `EmittableCheckBox.colors` is readable, but
 * `CheckBoxColors` exposes only an `internal` accessor returning
 * `CheckableColorProvider`, a public interface with no members.
 */

private const val WIDGET_PADDING = 8
