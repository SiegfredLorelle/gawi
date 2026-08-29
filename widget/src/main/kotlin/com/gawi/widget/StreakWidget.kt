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
 * own unit never has to answer it. Settled on the design canvas 2026-08-29 against
 * a drawn alternative; [toStreakState] carries why the rows are not sorted.
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
        when (val body = content.body(LocalSize.current)) {
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
                        ink = rememberOutfitInk(WidgetPalette.caption),
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
    val slot = if (layout == StreakLayout.Full) FULL_NUMERAL_SLOT else COMPACT_NUMERAL_SLOT
    val nameWidth = contentWidth() - slot.dp
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
                Box(modifier = GlanceModifier.width(slot.dp), contentAlignment = Alignment.CenterEnd) {
                    OutfitText(
                        text = row.streak.label(context, layout),
                        maxWidth = slot.dp,
                        // One paint, one bitmap per string; only the ColorFilter varies by unit.
                        ink = ink.copy(tint = row.streak.tint()),
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
        ink = rememberOutfitInk(WidgetPalette.caption),
        contentDescription = line,
    )
}

/**
 * Room reserved for the streak, in dp, at each layout.
 *
 * Sized to the longest string each form can produce at 16sp: "12 weeks" is about
 * 66dp in Outfit and "was 12" about 50dp, so [FULL_NUMERAL_SLOT] clears both;
 * "3w" is about 17dp. Reserving rather than measuring keeps the column aligned
 * down the widget, and the remainder is what the name ellipsises inside.
 */
private const val FULL_NUMERAL_SLOT = 76
private const val COMPACT_NUMERAL_SLOT = 32
