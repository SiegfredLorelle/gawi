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
import androidx.glance.appwidget.CheckboxDefaults
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
 * Every string here is an [OutfitText] — a bitmap in the app's face, tinted from
 * [WidgetPalette] — rather than a Glance `Text`, since 2026-08-25. [BitmapText]
 * has why a bitmap and what it costs. The colour history is one bug twice: the
 * default text colour was not theme-aware while the background was, which drew
 * near-black on `#303030` at 1.59:1 on a Nothing A059 on 2026-08-22; then the
 * *fix* was theme-aware in a way the background was not below API 31, which is
 * the 2026-08-28 measurement in [BitmapText]. Both were a mismatch between two
 * colours, which is why this surface now takes both of them from one place.
 * `WidgetTextColourDarkTest` and its light twin measure every ratio drawn here,
 * the glyph included.
 */
@Composable
internal fun WidgetBody(content: WidgetContent) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(WidgetPalette.surface).padding(WIDGET_PADDING.dp),
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
                    colors = CheckboxDefaults.colors(
                        checkedColor = WidgetPalette.glyphChecked,
                        uncheckedColor = WidgetPalette.glyphUnchecked,
                    ),
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
 * The checkbox glyph is pinned, since 2026-08-28, and this records what changed
 * — because for two phases this comment argued the opposite, and the argument
 * was sound at the time rather than lazy.
 *
 * Left unset, the glyph takes Glance's `res/color/glance_default_check_box.xml`:
 * `?android:attr/colorControlActivated` when checked and `colorControlNormal`
 * otherwise, theme attributes with no `-night` variant. That was read as "the
 * host resolves it in the launcher's theme", and on API 31 and up it is very
 * nearly that. **Below 31 it is not**, and that is the correction the API 29/30
 * pass forced: `CheckBoxTranslator` branches at 31, and under it the glyph is
 * resolved in *our* process and baked into the `RemoteViews` as one colour. The
 * selector never reaches the host, so its missing `-night` variant was never the
 * cause. [WidgetPalette] has the full path-by-path account and the measurements;
 * it is not repeated here, because two copies of it would drift.
 *
 * The other half of the old argument was that no provider could be handed to a
 * checkbox at all: `CheckboxDefaults.colors(checkedColor = GlanceTheme.colors.primary, …)`
 * compiles and throws `IllegalArgumentException: Cannot provide resource-backed
 * ColorProviders to CheckBoxColors` from `CheckedUncheckedColorProvider.<init>`.
 * That was too strong a reading of a true observation. The guard rejects
 * **resource-backed** providers only — every `GlanceTheme` colour is one, which
 * is why every attempt hit it — and a day/night provider is not resource-backed.
 * So the glyph can carry the palette, in both schemes, without inventing the
 * flat literals this comment used to price.
 *
 * One more claim here has been retired: that pinning still would not make the
 * glyph assertable. `CheckBoxColors` does expose its provider only through an
 * `internal` accessor returning a memberless public interface — but the object
 * behind it has a public `getColor(context, isNightMode, isChecked)`, so one
 * reflective hop reaches it, which is the bargain `WidgetRowTest` already makes
 * for `CompoundButtonAction`. `WidgetTextColourTest` now measures both glyph
 * states in both themes, and removing the `colors` argument above turns it red.
 *
 * What a JVM test still cannot see is which translation path a real host takes,
 * which is where the defect lived, so docs/running.md §4 keeps its by-hand
 * toggle on API 29 or 30.
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
