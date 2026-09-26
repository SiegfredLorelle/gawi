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
 * `internal` is a Kotlin visibility statement only — this class compiles to a
 * public JVM class with a no-arg constructor, and both the receiver and
 * [ToggleHabitAction] construct it directly, so R8 sees that constructor and
 * needs no rule to keep it. Its *identity* is the separate question, and that
 * does need one: Glance resolves a widget's ids by its `GlanceAppWidget` class,
 * so R8 folding this class together with its two siblings gives all three one
 * body. `app/proguard-rules.pro` carries the rule that stops it, and
 * docs/ux/widget.md §8 carries what it costs when it is missing.
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

/** The repository, resolved off the application graph via [WidgetEntryPoint]. */
internal fun repositoryFrom(context: Context): HabitRepository =
    EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).habitRepository()

/**
 * Draws whatever [body] decided — including whether Momo's still frame sits
 * above it, which is a value on the body and not a branch here. The choice is
 * tested; this is only the drawing.
 *
 * Every string here is an [OutfitText] — a bitmap in the app's face, tinted
 * from [WidgetPalette] — rather than a Glance `Text`. [BitmapText] has why a
 * bitmap and what it costs. The colour trap is one bug in two shapes. A default
 * text colour that is not theme-aware, under a background that is, draws
 * near-black on `#303030` at 1.59:1, measured on a Nothing A059 on 2026-08-22.
 * A tint that is theme-aware in a way the background is not below API 31 draws
 * the same mismatch the other way round, which is what [BitmapText] measures.
 * Both are a mismatch between two colours, which is why this surface takes both
 * of them from one place.
 * `WidgetTextColourDarkTest` and its light twin measure every ratio drawn here,
 * the glyph included.
 *
 * **There is no `GlanceTheme { }` here.** Nothing under it would read
 * `GlanceTheme.colors` once every colour comes from [WidgetPalette] — the mark
 * is a tinted bitmap and consults no theme at all — so it would be a wrapper
 * that did nothing except suggest the widget still draws on Glance's default
 * theme, which is the one thing this arrangement is about not doing.
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
 * the rows' own marks do not. Two tinted masks rather than one `Box` per habit —
 * [BandBitmap] has why: Glance caps a container at ten children, and a box per
 * habit truncated the band at six.
 *
 * **"In the rows' own order" is a claim about the picture as well as the list**,
 * and holds in either reading direction: [WovenBand] resolves the direction and
 * [BandBitmap] mirrors on it.
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
 * the launcher's own is not reachable from here. Under a system RTL locale — the
 * case that matters — the two agree. Where they could not, the flag would mirror
 * the band alone and invert a picture that was right, which is why
 * docs/ux/widget.md §8 states it rather than filing it under §2's milder
 * fallback. Without any of this a Hebrew locale mirrors the rows and leaves the
 * band, so the first habit's segment lands where a right-to-left reader stops.
 *
 * Two [BandBitmap] masks in one [Box], each tinted by its own provider, so
 * the band has no child count to hit ([BandBitmap] has the ten-child cap that
 * rules out a box per habit) and both fills still resolve through the palette.
 * Remembered against everything that changes the pixels: the flags, the room,
 * the density and the direction. Not the colour — the masks are white, and the
 * tint is the free half, as with [OutfitText].
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
 * One row per habit: the mark, then the name, as one 48dp clickable `Row` that
 * carries the spoken line — *"Read, done"* — and is the row's only stop.
 *
 * **One stop, because the mark is drawn rather than controlled.** A Glance
 * `CheckBox` lands as a control inside a wrapper `FrameLayout`: `applyModifiers`
 * describes the wrapper while the toggle goes to the control, TalkBack folds a
 * described non-focusable wrapper into its nearest focusable ancestor, and the
 * control is left as a second stop of its own at 32dp — under the floor, and
 * saying only a state the row already says. Nothing in Glance takes it out of
 * the tree or grows it, so [GlyphBitmap] draws the state and no control is
 * emitted. The row says name and state itself
 * (`widget_today_row_description`, the streak widget's pattern, read on a
 * Nothing A059 with TalkBack 17 as *"Water, 7 days"*), and the image inside it
 * is decorative. docs/running.md §4 has the measurement and [ROW_HEIGHT] the
 * height it is owed against.
 */
@Composable
private fun HabitRows(rows: List<WidgetRow>) {
    val context = LocalContext.current
    val nameWidth = contentWidth() - GLYPH_SLOT.dp
    val ink = rememberOutfitInk()
    val done = context.getString(R.string.widget_today_row_done)
    val notDone = context.getString(R.string.widget_today_row_not_done)
    // Both masks once for the list, never once per row: `RemoteViews` shares a
    // bitmap only when it is the same object, since `Bitmap` does not override
    // `hashCode`, so a mask remembered inside an item ships one copy per row.
    val densityDpi = context.resources.displayMetrics.densityDpi
    val checkedMask = remember(densityDpi) { glyphMask(context, checked = true) }
    val uncheckedMask = remember(densityDpi) { glyphMask(context, checked = false) }
    LazyColumn {
        items(rows) { row ->
            val toggle = actionRunCallback<ToggleHabitAction>(actionParametersOf(HABIT_ID to row.habitId))
            val spoken = context.getString(R.string.widget_today_row_description, row.name, if (row.completed) done else notDone)
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT.dp)
                    .clickable(toggle)
                    .semantics { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = GlanceModifier.width(GLYPH_BOX.dp), contentAlignment = Alignment.Center) {
                    CompletionGlyph(row.completed, if (row.completed) checkedMask else uncheckedMask)
                }
                OutfitText(text = row.name, maxWidth = nameWidth, ink = ink)
            }
        }
    }
}

/**
 * The completion mark at [GLYPH_SIZE] for this context's density.
 *
 * The `null` a degenerate size would answer with is a total function's business
 * rather than a state a row reaches — [GLYPH_SIZE] is a constant, so unlike
 * [MomoBitmap], whose height comes from the host, this one is never asked for a
 * size it cannot draw.
 */
private fun glyphMask(context: Context, checked: Boolean): Bitmap? {
    val metrics = context.resources.displayMetrics
    val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, GLYPH_SIZE.toFloat(), metrics).roundToInt()
    return GlyphBitmap.render(sizePx, metrics.densityDpi, checked)
}

/**
 * The completion mark, tinted by the palette and described by nothing — the row
 * above it carries the words. [mask] is the pixels and the tint is the free half,
 * the way [BandMask] and [OutfitText] treat theirs.
 */
@Composable
private fun CompletionGlyph(completed: Boolean, mask: Bitmap?) {
    if (mask == null) return
    Image(
        provider = ImageProvider(mask),
        contentDescription = null,
        modifier = GlanceModifier.size(GLYPH_SIZE.dp),
        colorFilter = ColorFilter.tint(if (completed) WidgetPalette.glyphChecked else WidgetPalette.glyphUnchecked),
    )
}

/**
 * The width inside the padding, off the size the host actually gave this
 * instance. Shared with [StreakWidget]: both providers pad the same way, and two
 * copies of that arithmetic would be two places to get the ellipsis wrong.
 */
@Composable
internal fun contentWidth() = LocalSize.current.width - (2 * WIDGET_PADDING).dp

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
 * Room reserved for the mark in the *name's* width arithmetic, in dp. Wider than
 * [GLYPH_BOX] on purpose: the difference is the margin the ellipsis needs to
 * land inside the row rather than under the edge of the widget.
 */
private const val GLYPH_SLOT = 48

/**
 * The box the mark is centred in, in dp — the width Glance's control occupied,
 * so the name begins where it always did. Laying the mark out in [GLYPH_SLOT]
 * instead would centre it 16dp in and push every name right by the same, which
 * is a visible change to a row that is otherwise drawn exactly as before.
 */
private const val GLYPH_BOX = 32

/**
 * The mark itself, in dp — Material's own glyph size, which is what the row drew
 * when a `CheckBox` drew it. [GlyphBitmap] renders a mark that fills its bitmap,
 * so this is the drawn size and not a board with a margin around it.
 */
private const val GLYPH_SIZE = 18

/**
 * Every habit row's height, in dp — the 48dp touch-target floor. The `Row` is
 * the target, and it has to be stated here because nothing inside it is one: the
 * mark is a decorative image of [GLYPH_SIZE], and a row left to size itself
 * would take that instead.
 * The cost is rows: 94dp of usable height at the 110dp minimum fits one full row
 * and most of a second, and the 4×3 large body fits three. The list scrolls.
 * Chosen with that cost in view (docs/ux/widget.md §8).
 */
private const val ROW_HEIGHT = 48

/** The copy states may wrap: "Can't read your habits" is 159dp at 16sp, wider than the smallest widget. */
internal const val MAX_COPY_LINES = 3
