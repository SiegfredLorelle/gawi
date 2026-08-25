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
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
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

    /**
     * The real size, so [OutfitText] knows how much room a name has. The default,
     * `Single`, reports the provider's minimum — 180dp — and would ellipsise every
     * name at the width of the smallest widget however wide the host drew it.
     * `Exact` also recomposes on a resize, which is when the room changes.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

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

/**
 * Draws whatever [body] decided — including whether Momo's still frame sits
 * above it, which is a value on the body and not a branch here. The choice is
 * tested; this is only the drawing.
 *
 * Every string here is an [OutfitText] — a bitmap in the app's face, tinted
 * `onSurface` — rather than a Glance `Text`, since 2026-08-25. [BitmapText] has
 * why a bitmap and what it costs; the colour history is the same as it was for
 * `Text`: the default is not theme-aware while the background is, which drew
 * near-black on `#303030` at 1.59:1 on a Nothing A059 on 2026-08-22, and
 * `WidgetTextColourDarkTest` and its light twin still measure the ratio in both
 * themes — reading the tint now, where they read the style before.
 */
@Composable
internal fun WidgetBody(content: WidgetContent) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).padding(WIDGET_PADDING.dp),
        ) {
            val context = LocalContext.current
            when (val body = content.body(LocalSize.current)) {
                is WidgetBodyContent.Copy -> {
                    body.mood?.let { MomoImage(it, contentDescription = null) }
                    val copy = context.getString(body.text)
                    OutfitText(text = copy, maxWidth = contentWidth(), maxLines = MAX_COPY_LINES, contentDescription = copy)
                }

                is WidgetBodyContent.Rows -> {
                    body.mood?.let { MomoImage(it, contentDescription = context.getString(it.description())) }
                    HabitRows(body.rows)
                }

                WidgetBodyContent.Blank -> Unit
            }
        }
    }
}

/**
 * One row per habit: the glyph, then the name.
 *
 * The `CheckBox` carries no text of its own any more — the name is the
 * [OutfitText] beside it — so the two are one clickable `Row`. The action stays
 * on the checkbox as well, and that is not redundancy: on API 31+ a
 * `CompoundButton` toggles *visually* on a tap with or without an action behind
 * it, so a glyph without its own callback would flip on screen and write
 * nothing. The name goes on the checkbox as its `contentDescription`, so
 * TalkBack still pairs it with the checked state the way `CheckBox(text = …)`
 * did; the image is decorative. Review caught the first cut describing the
 * image instead, which read as an anonymous checkbox beside a named picture.
 */
@Composable
private fun HabitRows(rows: List<WidgetRow>) {
    val nameWidth = contentWidth() - CHECKBOX_SLOT.dp
    val paint = rememberOutfitPaint()
    LazyColumn {
        items(rows) { row ->
            val toggle = actionRunCallback<ToggleHabitAction>(actionParametersOf(HABIT_ID to row.habitId))
            Row(
                modifier = GlanceModifier.fillMaxWidth().clickable(toggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBox(
                    checked = row.completed,
                    onCheckedChange = toggle,
                    modifier = GlanceModifier.semantics { contentDescription = row.name },
                    // No `colors`: see the note below on why the glyph cannot take
                    // the theme's, and what that leaves to check by hand.
                )
                OutfitText(text = row.name, maxWidth = nameWidth, paint = paint)
            }
        }
    }
}

/** The width inside the padding, off the size the host actually gave this instance. */
@Composable
private fun contentWidth() = LocalSize.current.width - (2 * WIDGET_PADDING).dp

/*
 * The checkbox glyph is deliberately NOT pinned, and this is the measurement
 * behind that. Review reasonably suggested it should be.
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
 * in both themes without a day/night provider to express them. That much has
 * not changed with the palette: a designed scheme is still Compose-side, and a
 * `RemoteViews` tree cannot reach it.
 *
 * That used to be the end of it, because the project had no palette to pin the
 * glyph to. It has one now — `GawiTheme` carries designed light and dark
 * schemes — so the reason this is still unpinned has changed and is worth
 * restating rather than leaving as a stale deferral:
 *
 *  - `ColorProvider(Color)` literals are now available, and picking two is no
 *    longer inventing a palette. But `:widget` sees `:core:ui` for the font and
 *    Momo's geometry and nothing else (build.gradle.kts), so it still means *copying*
 *    two hexes into this module, and the widget's own palette is
 *    a separate piece of work with three more surfaces in it
 *    (docs/ux/visual-identity.md §7.4). Doing a third of it here would leave the
 *    widget half-styled and the duplication undocumented.
 *  - Pinning still would not make the glyph assertable, which was the stated
 *    reason to do it. `EmittableCheckBox.colors` is readable, but
 *    `CheckBoxColors` exposes only an `internal` accessor returning
 *    `CheckableColorProvider`, a public interface with no members.
 *
 * So the glyph keeps the host's tint for now and docs/running.md §4 keeps the
 * by-hand check. Revisit with the widget set, not with the palette:
 * docs/ux/visual-identity.md §7.4 is the scope and the price.
 */

private const val WIDGET_PADDING = 8

/**
 * Room reserved for the checkbox glyph beside a name, in dp. Glance's glyph is
 * narrower; the difference is the margin the ellipsis needs to land inside the
 * row rather than under the edge of the widget.
 */
private const val CHECKBOX_SLOT = 48

/** The copy states may wrap: "Can't read your habits" is 159dp at 16sp, wider than the smallest widget. */
private const val MAX_COPY_LINES = 3
