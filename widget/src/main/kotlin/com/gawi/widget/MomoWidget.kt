package com.gawi.widget

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import com.gawi.core.domain.mascot.Mood

/**
 * Momo alone, on her ground — the Momo widget of docs/ux/visual-identity.md
 * §7.4, the last of that set (docs/ux/widget.md §7).
 *
 * **Mood only, no rows, no number.** The canvas's argument for it: the tank as
 * a glanceable object answers "how am I doing" without a value that can rot, so
 * it is the cheapest of the four surfaces to keep honest. There is nothing here
 * to date and nothing to tap, so neither the streak widget's "as of" line nor
 * the Today widget's re-reading callback applies.
 *
 * **It reads what the Today widget reads.** [widgetContent] — the same flow,
 * the same three states, the same `Mascot.mood` over the same snapshot — and
 * this body takes what it needs from the state: the mood, and whether there are
 * any rows. A parallel `MomoContent` type would be `WidgetContent` with two
 * fields fewer.
 *
 * **Her ground is `primaryContainer`, flat.** The Today screen paints the tank
 * as a gradient; a `RemoteViews` background is one colour, and flat was decided
 * anyway. The habitat's weeds and bubbles stay Today's own — nothing about them
 * crosses the widget edge (docs/ux/momo.md §4), and the canvas did not draw them
 * here.
 *
 * Kept current the two ways every widget here is: the content collects
 * `observeToday()`, and [GlanceProjectionListener] names this provider so a
 * committed write starts a session. `SizeMode.Exact` so the copy knows its
 * width and the face knows its room.
 */
internal class MomoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Built outside provideContent: Lint's FlowOperatorInvokedInComposition is fatal here.
        val content = repositoryFrom(context).widgetContent()
        provideContent {
            val current by content.collectAsState(initial = WidgetContent.Loading)
            MomoBody(current)
        }
    }
}

/**
 * The whole tree, off one [WidgetContent]. Separated from [MomoWidget] so a test
 * can compose it with a value rather than a repository, like `WidgetBody`.
 *
 * **One reading, and it is the face's.** The face carries the full mood sentence
 * (`widget_mood_*`, the Today panel's words); the one-word caption under it is
 * decorative, so TalkBack says "Momo is pottering about." once and never "Momo
 * is pottering about. pottering." With no habits the roles swap — the copy is
 * read and the face is decorative — which is the Today widget's rule for the
 * same state. Unavailable draws the failure copy and no face: nothing was read,
 * so there is no mood to guess.
 *
 * Every string is an [OutfitText] in [WidgetPalette.momoCaption], the one ink
 * measured against this ground; `MomoTextColourTest` holds it in both schemes.
 * The face is [momoFaceHeight] tall — her usual 72dp until the caption needs
 * the room, and absent when the no-habits copy leaves none.
 */
@Composable
internal fun MomoBody(content: WidgetContent) {
    Box(
        modifier = GlanceModifier.fillMaxSize().background(WidgetPalette.momoGround).padding(WIDGET_PADDING.dp),
        contentAlignment = Alignment.Center,
    ) {
        val context = LocalContext.current
        val ink = rememberOutfitInk(
            tint = WidgetPalette.momoCaption,
            textSizeSp = BitmapText.CAPTION_SIZE_SP,
            weight = BitmapText.OUTFIT_WEIGHT_SEMIBOLD,
        )
        when (content) {
            WidgetContent.Loading -> Unit

            WidgetContent.Unavailable -> {
                val copy = context.getString(R.string.widget_unavailable)
                OutfitText(text = copy, maxWidth = contentWidth(), maxLines = MAX_COPY_LINES, ink = ink, contentDescription = copy)
            }

            is WidgetContent.Ready -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val empty = content.state.rows.isEmpty()
                val mood = content.state.mood
                val sentence = context.getString(mood.description())
                val word = context.getString(if (empty) R.string.widget_no_habits else mood.caption())
                val lines = if (empty) EMPTY_COPY_LINES else 1
                val face = momoFaceHeight(LocalSize.current, BitmapText.textScale(context, BitmapText.CAPTION_SIZE_SP), lines)
                if (face != null) {
                    MomoImage(mood, contentDescription = if (empty) null else sentence, heightDp = face)
                    Spacer(modifier = GlanceModifier.height(CAPTION_GAP.dp))
                }
                OutfitText(
                    text = word,
                    maxWidth = contentWidth(),
                    maxLines = lines,
                    ink = ink,
                    contentDescription = if (empty) word else null,
                )
            }
        }
    }
}

/**
 * The one word drawn under the face — the caption the design canvas chose over
 * the full sentence (which clips at 110dp) and over no caption (which leaves a
 * greyscale viewer no word). TalkBack does not read these: the face carries the
 * full [description] once, and the word is decorative.
 */
@StringRes
internal fun Mood.caption(): Int = when (this) {
    Mood.THRIVING -> R.string.widget_momo_caption_thriving
    Mood.CONTENT -> R.string.widget_momo_caption_content
    Mood.WORRIED -> R.string.widget_momo_caption_worried
    Mood.REGENERATING -> R.string.widget_momo_caption_regenerating
}

/**
 * How tall the face is drawn, dp, or `null` for no face: [MomoBitmap.HEIGHT_DP]
 * when there is room, and no more than the room leaves once the padding, the
 * gap and [captionLines] of caption type at [textScale] are taken out. Pure, so
 * `MomoBodyTest` pins it.
 *
 * Bounded rather than constant because the provider's minimum is 110dp — 94
 * usable — and 72 + 3 + a 15dp line is 90 at the default scale, so a caption at
 * 1.3× would already push the face or the word off the tile. Shrinking is the
 * direction the cost argument in [MomoBitmap] allows: the bitmap never grows
 * with the host, it only gives way to the text.
 *
 * **The reservation matches what is drawn, line for line**, and that is the
 * thing to re-check when either moves. A mood word is one line and ellipsises
 * past it, which never happens on the 110dp tile (94 − 3 −
 * 30 leaves 61dp of face at 2×). The no-habits copy is two lines
 * ([EMPTY_COPY_LINES]), and when those leave less than [MIN_FACE_DP] the face
 * goes rather than a sliver of her or a clipped word — reachable only in that
 * state on the minimum tile at a large scale, where she is decorative and the
 * copy is what is read, so nothing is lost.
 */
internal fun momoFaceHeight(size: DpSize, textScale: Float, captionLines: Int): Float? {
    val caption = captionLines * CAPTION_LINE_DP * textScale.coerceAtLeast(1f)
    val room = size.height.value - 2 * WIDGET_PADDING - CAPTION_GAP - caption
    return room.coerceAtMost(MomoBitmap.HEIGHT_DP).takeIf { it >= MIN_FACE_DP }
}

/** The no-habits copy may take two lines; a mood word takes one. */
internal const val EMPTY_COPY_LINES = 2

/** Between the face and its word, dp — the canvas's 3px at the tile's 110dp. */
private const val CAPTION_GAP = 3

/** One line of caption type at scale 1, dp: Outfit's ascent-to-descent at 12sp is about 1.25em. */
internal const val CAPTION_LINE_DP = 15f

/** The least face worth drawing; below this the eyes are a smudge. */
internal const val MIN_FACE_DP = 40f
