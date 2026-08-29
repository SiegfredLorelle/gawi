package com.gawi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
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

/**
 * Momo alone, on her ground — the Momo widget of docs/ux/visual-identity.md
 * §7.4, built 2026-08-29 as the last of that set (docs/ux/widget.md §7).
 *
 * **Mood only, no rows, no number.** The canvas's argument for it: the tank as
 * a glanceable object answers "how am I doing" without a value that can rot, so
 * it is the cheapest of the four surfaces to keep honest. There is nothing here
 * to date and nothing to tap, so neither the streak widget's "as of" line nor
 * the Today widget's re-reading callback applies.
 *
 * **Her ground is `primaryContainer`, flat.** The Today screen paints the tank
 * as a gradient; a `RemoteViews` background is one colour, and flat was decided
 * anyway. The habitat's weeds and bubbles stay Today's own — nothing about them
 * crosses the widget edge (docs/ux/momo.md §4), and the canvas did not draw them
 * here.
 *
 * Kept current the two ways every widget here is: the content collects
 * `observeToday()`, and [GlanceProjectionListener] names this provider so a
 * committed write starts a session. `SizeMode.Exact` so the copy states know
 * their width; the face itself is a constant size.
 */
internal class MomoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Built outside provideContent: Lint's FlowOperatorInvokedInComposition is fatal here.
        val content = repositoryFrom(context).momoContent()
        provideContent {
            val current by content.collectAsState(initial = MomoContent.Loading)
            MomoBody(current)
        }
    }
}

/**
 * The whole tree, off one [MomoContent]. Separated from [MomoWidget] so a test
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
 */
@Composable
internal fun MomoBody(content: MomoContent) {
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
            MomoContent.Loading -> Unit

            MomoContent.Unavailable -> {
                val copy = context.getString(R.string.widget_unavailable)
                OutfitText(text = copy, maxWidth = contentWidth(), maxLines = MAX_COPY_LINES, ink = ink, contentDescription = copy)
            }

            is MomoContent.Ready -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val sentence = context.getString(content.mood.description())
                val word = context.getString(if (content.empty) R.string.widget_no_habits else content.mood.caption())
                MomoImage(content.mood, contentDescription = if (content.empty) null else sentence)
                Spacer(modifier = GlanceModifier.height(CAPTION_GAP.dp))
                OutfitText(
                    text = word,
                    maxWidth = contentWidth(),
                    ink = ink,
                    contentDescription = if (content.empty) word else null,
                )
            }
        }
    }
}

/** Between the face and its word, dp — the canvas's 3px at the tile's 110dp. */
private const val CAPTION_GAP = 3
