package com.gawi.widget

import android.content.Context
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.unit.ColorProvider
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.mascot.Mood
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.roundToInt

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
 *
 * **There is no `GlanceTheme { }` here any more**, removed 2026-08-28 with the
 * palette. Nothing under it read `GlanceTheme.colors` once all three colours
 * came from [WidgetPalette] — the `CheckboxDefaults.colors(checked, unchecked)`
 * overload does not consult the theme, only the no-argument one does — so it was
 * a wrapper that did nothing except suggest the widget still draws on Glance's
 * default theme, which is the one thing this change is about not doing.
 */
@Composable
internal fun WidgetBody(content: WidgetContent) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetPalette.surface).padding(WIDGET_PADDING.dp),
    ) {
        val context = LocalContext.current
        when (val body = content.body(LocalSize.current, BitmapText.textScale(context, BitmapText.CAPTION_SIZE_SP))) {
            is WidgetBodyContent.Copy -> {
                body.mood?.let { MomoImage(it, contentDescription = null) }
                val copy = context.getString(body.text)
                OutfitText(text = copy, maxWidth = contentWidth(), maxLines = MAX_COPY_LINES, contentDescription = copy)
            }

            is WidgetBodyContent.Rows -> {
                body.mood?.let { MomoImage(it, contentDescription = context.getString(it.description())) }
                HabitRows(body.rows)
            }

            is WidgetBodyContent.Large -> {
                LargeHeader(body.mood, body.rows)
                Spacer(modifier = GlanceModifier.height(HEADER_GAP.dp))
                HabitRows(body.rows)
            }

            WidgetBodyContent.Blank -> Unit
        }
    }
}

/**
 * The large body's header (docs/ux/widget.md §7): Momo on her ground, and
 * beside her the mood line over the woven day band.
 *
 * **One reading.** The mood line is the description and everything else here is
 * decorative — the face carries `null`, unlike the face-above-rows body, where
 * she is the only place the mood can be read; the band's segments carry
 * nothing, because the rows beneath already announce each habit's state
 * (docs/ux/momo.md §5). Describing the face too would read the same sentence
 * twice.
 *
 * **The band is the rows' own flags, in the rows' own order.** One segment per
 * habit, `bandWoven` when today's cell is ticked and `bandOutstanding` when it is
 * not; nothing is counted, sorted or capped, so the band cannot say something
 * the checkboxes do not. Two tinted masks rather than one `Box` per habit —
 * [BandBitmap] has why: Glance caps a container at ten children, and a box per
 * habit truncated the band at six.
 *
 * **"In the rows' own order" is a claim about the picture as well as the list,
 * and holds in either direction since 2026-08-30.** It did not before: with a
 * Hebrew system locale the rows mirrored — the glyph moves to the right edge —
 * and the band did not, so the first habit's segment landed where a
 * right-to-left reader stops rather than where they start (measured on a
 * launcher, docs/running.md §4). [WovenBand] now reads the direction and
 * [BandBitmap] mirrors on it. Stated here because the sentence above is the one
 * that reads as covering it.
 *
 * The copy is caption-sized and semibold, as the canvas drew it, and it gets the
 * width the pill and the gap leave. Three lines, not the canvas's one: at the
 * gate that is 128dp, and the regenerating line needs three of them there —
 * `HeaderCopyTest` measures it. [LARGE_MIN_WIDTH]'s KDoc has the arithmetic,
 * and the gate divides by the text scale so the room is counted in units of the
 * text that has to go in it, as the streak widget's does.
 */
@Composable
private fun LargeHeader(mood: Mood, rows: List<WidgetRow>) {
    val context = LocalContext.current
    val copy = context.getString(mood.description())
    val copyWidth = (contentWidth() - (MOMO_PILL_WIDTH + HEADER_GAP).dp).coerceAtLeast(0.dp)
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = GlanceModifier
                .size(MOMO_PILL_WIDTH.dp, MOMO_PILL_HEIGHT.dp)
                .background(WidgetPalette.momoGround)
                .cornerRadius(MOMO_PILL_RADIUS.dp),
            contentAlignment = Alignment.Center,
        ) {
            MomoImage(mood, contentDescription = null, heightDp = MomoBitmap.PILL_HEIGHT_DP)
        }
        Spacer(modifier = GlanceModifier.width(HEADER_GAP.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            OutfitText(
                text = copy,
                maxWidth = copyWidth,
                maxLines = HEADER_COPY_LINES,
                ink = rememberOutfitInk(textSizeSp = BitmapText.CAPTION_SIZE_SP, weight = BitmapText.OUTFIT_WEIGHT_SEMIBOLD),
                contentDescription = copy,
            )
            Spacer(modifier = GlanceModifier.height(BAND_GAP.dp))
            WovenBand(rows, copyWidth)
        }
    }
}

/**
 * The day, woven so far: one segment per habit, the rows' order, the rows'
 * flags. Decorative — both images carry no description.
 *
 * **"The rows' order" is read in the host's direction, and this is where the
 * direction is resolved.** [BandBitmap] is pure and takes it as a flag; the
 * value is the app's configuration, because Glance composes in our process and
 * the launcher's own is not reachable from here. A per-app locale can therefore
 * disagree with the launcher — the caveat docs/ux/visual-identity.md §2 already
 * carries for the text bitmaps — while a system RTL locale, the case that
 * matters, agrees. Said here as well as on [BandBitmap] because this is the
 * composable that emits the band, so it is the doc a reader lands on first.
 *
 * Two [BandBitmap] masks in one [Box], each tinted by its own provider, so
 * the band has no child count to hit ([BandBitmap] has the ten-child cap this
 * replaced) and both fills still resolve through the palette. Remembered
 * against everything that changes the pixels: the flags, the room, the density
 * and the direction. Not the colour — the masks are white, and the tint is the
 * free half, as with [OutfitText].
 */
@Composable
private fun WovenBand(rows: List<WidgetRow>, width: Dp) {
    val resources = LocalContext.current.resources
    val metrics = resources.displayMetrics
    val mirrored = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    val flags = rows.map { it.completed }
    val masks = remember(flags, width, metrics.densityDpi, mirrored) {
        val widthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width.value, metrics).roundToInt()
        val heightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BAND_HEIGHT.toFloat(), metrics).roundToInt()
        val gapPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BAND_GAP.toFloat(), metrics)
        val geometry = BandBitmap.Geometry(widthPx, heightPx, gapPx, metrics.densityDpi)
        listOf(true, false).map { woven -> BandBitmap.render(flags, geometry, woven, mirrored) }
    }
    Box(modifier = GlanceModifier.width(width).height(BAND_HEIGHT.dp)) {
        masks[0]?.let { BandMask(it, WidgetPalette.bandWoven, width) }
        masks[1]?.let { BandMask(it, WidgetPalette.bandOutstanding, width) }
    }
}

@Composable
private fun BandMask(mask: Bitmap, tint: ColorProvider, width: Dp) {
    Image(
        provider = ImageProvider(mask),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = GlanceModifier.width(width).height(BAND_HEIGHT.dp),
        colorFilter = ColorFilter.tint(tint),
    )
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
    val ink = rememberOutfitInk()
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
                OutfitText(text = row.name, maxWidth = nameWidth, ink = ink)
            }
        }
    }
}

/**
 * The width inside the padding, off the size the host actually gave this
 * instance. Shared with [StreakWidget]: both providers pad the same way, and two
 * copies of that arithmetic would be two places to get the ellipsis wrong.
 */
@Composable
internal fun contentWidth() = LocalSize.current.width - (2 * WIDGET_PADDING).dp

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

internal const val WIDGET_PADDING = 8

/*
 * The large body's geometry, in dp, as the canvas drew it at 250×200
 * (docs/ux/widget.md §7): a 66×52 pill with a 12dp radius, 10dp to the copy,
 * a 5dp band with 3dp gaps and 3dp radius. The pill is what LARGE_MIN_WIDTH's
 * arithmetic subtracts.
 */
internal const val MOMO_PILL_WIDTH = 66
internal const val MOMO_PILL_HEIGHT = 52
private const val MOMO_PILL_RADIUS = 12
internal const val HEADER_GAP = 10
internal const val HEADER_COPY_LINES = 3
internal const val BAND_HEIGHT = 5
internal const val BAND_GAP = 3

/**
 * Room reserved for the checkbox glyph beside a name, in dp. Glance's glyph is
 * narrower; the difference is the margin the ellipsis needs to land inside the
 * row rather than under the edge of the widget.
 */
private const val CHECKBOX_SLOT = 48

/** The copy states may wrap: "Can't read your habits" is 159dp at 16sp, wider than the smallest widget. */
internal const val MAX_COPY_LINES = 3
