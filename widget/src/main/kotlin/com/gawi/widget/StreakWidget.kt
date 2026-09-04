package com.gawi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import java.time.LocalDate

/**
 * Every habit's current run, dated — the streak widget of
 * docs/ux/visual-identity.md §7.4, drawn as that section's "direction B".
 *
 * **Why a second provider rather than a streak column on [TodayWidget].** PRD
 * OQ-5 settled the Today widget as minimal, and §7.4 keeps that: a streak column
 * costs the width the rows need, and the one-tap claim is what that widget is
 * for. The streak reading is a different question asked at a different moment, so
 * it gets its own surface and the user chooses whether to place it.
 *
 * **It shows every habit rather than one number, and that is the design
 * decision.** A single headline streak has to rank habits, and the only ordering
 * available compares a count of days against a count of weeks — the comparison
 * `StreakUi` is a sealed type specifically to prevent. Showing each habit in its
 * own unit never has to answer it. Settled on the design canvas against a drawn
 * alternative; [toStreakState] carries why the rows are not sorted.
 *
 * **Read-only, deliberately.** There is no tap target. A widget that wrote would
 * have to re-read the log first, because a stale drawn logical date writes a
 * completion to *yesterday* and §5's three-day retro window silently accepts it —
 * the trap [toggleHabit] exists to avoid. Nothing here needs that, so nothing
 * here takes it on. Adding a tap means adding an `ActionCallback` with the same
 * re-read, not passing the drawn date.
 *
 * Kept current the same two ways [TodayWidget] is, and the second one is easy to
 * forget: the content collects `observeToday()` so a live session tracks Room,
 * and [GlanceProjectionListener] starts a session when none is alive. That
 * listener has to name **both** providers — a provider missing from it freezes
 * for the life of a session, which looks exactly like a widget nobody placed.
 *
 * `SizeMode.Exact` for the reason [TodayWidget] gives, plus one of its own: the
 * unit word and the header are gated on the reported size ([StreakContent.body]),
 * and `Single` would report the provider minimum forever and never draw either.
 */
internal class StreakWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Built outside provideContent: Lint's FlowOperatorInvokedInComposition is fatal here.
        val content = repositoryFrom(context).streakContent()
        provideContent {
            val current by content.collectAsState(initial = StreakContent.Loading)
            StreakBody(current)
        }
    }
}

/**
 * The whole tree, off one [StreakContent].
 *
 * Separated from [StreakWidget] so a test can compose it with a value rather than
 * a repository, the way `WidgetBody` is.
 *
 * **The "as of" line is pinned, not the last row.** It sits outside the
 * [LazyColumn], which takes the remaining height, so the line stays visible while
 * the rows scroll. That ordering is what makes §7.1's requirement hold at the
 * smallest size: 94dp of usable height at [WIDGET_PADDING] buys three 20dp rows
 * and the line, or four rows and no line — and the line is the half that turns a
 * possible lie into a stale-but-true reading, so the rows are what give way.
 */
@Composable
internal fun StreakBody(content: StreakContent) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(WidgetPalette.surface).padding(WIDGET_PADDING.dp),
    ) {
        val context = LocalContext.current
        when (val body = content.body(LocalSize.current, BitmapText.textScale(context))) {
            is StreakBodyContent.Copy -> {
                val copy = context.getString(body.text)
                OutfitText(text = copy, maxWidth = contentWidth(), maxLines = MAX_COPY_LINES, contentDescription = copy)
            }

            is StreakBodyContent.Rows -> {
                // Decorative: the launcher's own label already says "Streaks", and every row
                // is announced with its unit, so announcing this too is noise.
                if (body.layout == StreakLayout.Full) {
                    OutfitText(
                        text = context.getString(R.string.widget_streak_header),
                        maxWidth = contentWidth(),
                        ink = rememberOutfitInk(
                            tint = WidgetPalette.caption,
                            textSizeSp = BitmapText.CAPTION_SIZE_SP,
                            weight = BitmapText.OUTFIT_WEIGHT_SEMIBOLD,
                        ),
                    )
                }
                StreakRows(body.rows, body.layout, GlanceModifier.defaultWeight())
                AsOfLine(body.asOf)
            }

            StreakBodyContent.Blank -> Unit
        }
    }
}

/**
 * One row per habit: the name, and its streak at the right-hand edge.
 *
 * Both halves sit in fixed-width [Box]es rather than leaning on a weight, the
 * same way [TodayWidget] reserves `CHECKBOX_SLOT`. A rasterised string is an
 * `Image` whose width is its own ink, so without a slot a short name would pull
 * the numeral in beside it and the column would not line up.
 *
 * The whole row carries one content description and both images are decorative,
 * so TalkBack says "Reading, 12 days" once instead of reading two fragments.
 */
@Composable
private fun StreakRows(rows: List<StreakRow>, layout: StreakLayout, modifier: GlanceModifier) {
    val context = LocalContext.current
    val ink = rememberOutfitInk()
    // One semibold paint for every numeral: the canvas draws them at 600 against
    // names at 400, which is a fourth signal separating a streak from its name and
    // the only one that survives both greyscale and the narrow slot.
    val numeralInk = rememberOutfitInk(weight = BitmapText.OUTFIT_WEIGHT_SEMIBOLD)
    // The slot is dp and the numeral inside it is sp, so it has to be scaled by
    // hand or a large font setting ellipsises the number the widget exists to
    // show — and in the compact form the `w` is the first character to go.
    // Measured: "99w" is 30dp of ink at the default and 54dp at a reported
    // fontScale of 2.0, against an unscaled 32dp slot.
    val base = if (layout == StreakLayout.Full) FULL_NUMERAL_SLOT else COMPACT_NUMERAL_SLOT
    val slot = (base * BitmapText.textScale(context)).dp
    // Never negative: Glance's Exact size reports zero until the host has told it
    // anything, and a negative width collapses the column to nothing rather than
    // ellipsising, which is the failure OutfitText's own MIN_WIDTH_DP floor avoids
    // for the bitmap but cannot avoid for the Box around it.
    val nameWidth = (contentWidth() - slot).coerceAtLeast(0.dp)
    LazyColumn(modifier = modifier) {
        items(rows) { row ->
            val spoken = context.getString(
                R.string.widget_streak_row_description,
                row.name,
                row.streak.spokenLabel(context),
            )
            Row(
                modifier = GlanceModifier.fillMaxWidth().semantics { contentDescription = spoken },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = GlanceModifier.width(nameWidth), contentAlignment = Alignment.CenterStart) {
                    OutfitText(text = row.name, maxWidth = nameWidth, ink = ink)
                }
                Box(modifier = GlanceModifier.width(slot), contentAlignment = Alignment.CenterEnd) {
                    OutfitText(
                        text = row.streak.label(context, layout),
                        maxWidth = slot,
                        // One paint, one bitmap per string; only the ColorFilter varies by unit.
                        ink = numeralInk.copy(tint = row.streak.tint()),
                    )
                }
            }
        }
    }
}

/** §7.1's mandatory provenance line, in the caption ink and announced. */
@Composable
private fun AsOfLine(asOf: LocalDate) {
    val context = LocalContext.current
    val locale = context.resources.configuration.locales[0]
    val line = context.getString(R.string.widget_streak_as_of, formatAsOf(asOf, locale))
    OutfitText(
        text = line,
        maxWidth = contentWidth(),
        ink = rememberOutfitInk(tint = WidgetPalette.caption, textSizeSp = BitmapText.CAPTION_SIZE_SP),
        contentDescription = line,
    )
}

/**
 * Room reserved for the streak, in dp, at each layout.
 *
 * Sized to the widest string each form can produce **at a text scale of 1**,
 * measured rather than estimated (`StreakSlotTest` holds the numbers): the full
 * form's worst case is "99 weeks" at 66dp and the compact form's is "99w" at
 * 30dp. [COMPACT_NUMERAL_SLOT] was 32 until review measured it — a 2dp margin,
 * which is not one. Reserving rather than measuring at draw time keeps the column
 * aligned down the widget, and the remainder is what the name ellipsises inside.
 *
 * Both are multiplied by the font scale at the call site. They have to be: the
 * numeral is drawn in sp and the slot is declared in dp, so leaving them fixed
 * ellipsises the number itself at 200% — and truncating the payload is worse than
 * truncating a name, which is why the name is what gives up the room.
 */
internal const val FULL_NUMERAL_SLOT = 76
internal const val COMPACT_NUMERAL_SLOT = 36
