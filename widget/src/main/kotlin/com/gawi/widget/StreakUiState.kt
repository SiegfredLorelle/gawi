package com.gawi.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.unit.ColorProvider
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.ui.streak.StreakUi
import com.gawi.core.ui.streak.toUi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One habit as the streak widget draws it: a name, and its streak.
 *
 * The streak is a [StreakUi], not an `Int`, and that is the whole reason this
 * module now imports from `:core:ui`. `StreakUi` is sealed by *unit* because
 * docs/ux/today-view.md §5 says a count of days and a count of weeks must never
 * be styled as the same number; an `Int` here would drop the distinction at the
 * module edge and leave the widget to reinvent it, which is precisely the drift
 * `StreakUi`'s own KDoc says it exists to prevent.
 *
 * No habit id. This widget has no tap target — see [StreakWidget] — so nothing
 * needs to travel to a callback, and leaving the field out is what keeps a tap
 * from being added without also thinking about the stale-logical-date trap
 * [toggleHabit] exists to avoid.
 */
internal data class StreakRow(val name: String, val streak: StreakUi)

/**
 * Everything the streak widget draws.
 *
 * [asOf] is the snapshot's logical date, and it is not decoration:
 * docs/ux/visual-identity.md §7.1 requires this widget to date its number,
 * because a streak reaches zero with *no new event* and so is the one value whose
 * staleness is not bounded by the user doing nothing — on the one surface with no
 * live query. Dating it turns a possible lie into a stale-but-true reading.
 *
 * A date rather than a time, settled on the canvas: the number only changes at
 * the day cutoff, so a fresh-looking clock time on a stale render would claim a
 * precision the value does not have. It would also be a fact about the widget's
 * last render rather than about the streak.
 */
internal data class StreakUiState(val rows: List<StreakRow>, val asOf: LocalDate)

/**
 * What the streak widget has to draw right now. Three states, for the same
 * reason [WidgetContent] has three: "not read yet" and "could not be read" must
 * not look alike.
 */
internal sealed interface StreakContent {

    /** Before the first emission arrives. */
    data object Loading : StreakContent

    /** The read threw, past [retryThenFail]'s retries. */
    data object Unavailable : StreakContent

    data class Ready(val state: StreakUiState) : StreakContent
}

/** How much room the host gave this instance, and therefore how a streak is written. */
internal enum class StreakLayout {

    /**
     * Numerals only — `12`, `3w`, `0` — and no header. The one-cell widget.
     *
     * Two of `StreakUi`'s three signals survive here: the `w` suffix and the
     * colour role. The third, the unit word, is what does not fit.
     */
    Compact,

    /** `12 days`, `3 weeks`, `was 12`, under a header. All three signals. */
    Full,
}

/** What to draw, resolved from the content and the size the host reported. */
internal sealed interface StreakBodyContent {

    /** Nothing at all — [StreakContent.Loading]. */
    data object Blank : StreakBodyContent

    /** A single centred line: no habits, or unavailable. */
    data class Copy(@StringRes val text: Int) : StreakBodyContent

    data class Rows(val rows: List<StreakRow>, val asOf: LocalDate, val layout: StreakLayout) : StreakBodyContent
}

/**
 * The body for a given size, at a given font scale.
 *
 * **[StreakLayout.Full] needs both dimensions, unlike [MOMO_MIN_HEIGHT].** The
 * unit word costs width and the header costs height, so a widget that is tall and
 * narrow gets [StreakLayout.Compact] — a `3 weeks` that ellipsises to `3 we…`
 * would be worse than the `3w` the compact form draws on purpose.
 *
 * **[textScale] is a parameter and not a detail, because the room is measured in
 * dp and the text is drawn in sp.** `BitmapText` sizes its paint with
 * `COMPLEX_UNIT_SP`, so at a large font setting every string is wider while the
 * widget is exactly as wide as before. Comparing the raw dp against these
 * thresholds would therefore pick the full form at the one setting where the unit
 * word is least able to fit — producing the `3 we…` this gate exists to avoid.
 * Dividing by the scale asks the question that matters: how much room is there
 * *in units of the text that has to go in it*. Pass `BitmapText.textScale`, not
 * `configuration.fontScale`; its KDoc has the 1.75-versus-2.0 measurement.
 *
 * Unavailable and empty both fall to [StreakBodyContent.Copy] at every size:
 * there is no number to date, so the "as of" line would be dating nothing.
 */
internal fun StreakContent.body(size: DpSize, textScale: Float): StreakBodyContent = when (this) {
    StreakContent.Unavailable -> StreakBodyContent.Copy(R.string.widget_unavailable)

    StreakContent.Loading -> StreakBodyContent.Blank

    is StreakContent.Ready ->
        if (state.rows.isEmpty()) {
            StreakBodyContent.Copy(R.string.widget_no_habits)
        } else {
            StreakBodyContent.Rows(
                rows = state.rows,
                asOf = state.asOf,
                layout = if (fitsFullForm(size, textScale)) StreakLayout.Full else StreakLayout.Compact,
            )
        }
}

/**
 * The least width and height, in dp, at which the unit word and the header are
 * drawn.
 *
 * The provider's minimum is 180×110dp — the three-by-two cell the canvas drew —
 * and the four-by-three it also drew lands at 250×200. These two numbers sit
 * between the pairs without depending on any one launcher's cell size, the way
 * [MOMO_MIN_HEIGHT] does. Above them there is room for `3 weeks` beside a name
 * and a header above both; below, the numeral carries the unit as a suffix.
 */
internal const val FULL_MIN_WIDTH = 220
internal const val FULL_MIN_HEIGHT = 150

/**
 * Whether [size] has room for the unit word and the header once [textScale] is
 * taken into account — `BitmapText.textScale`, which is what the ink actually
 * does rather than what `fontScale` reports.
 */
private fun fitsFullForm(size: DpSize, textScale: Float): Boolean {
    val scale = textScale.coerceAtLeast(1f)
    return size.width / scale >= FULL_MIN_WIDTH.dp && size.height / scale >= FULL_MIN_HEIGHT.dp
}

/**
 * The trailing text for one streak, in the given layout.
 *
 * Takes a [Context] rather than being `@Composable` so it is testable without a
 * Glance harness, and because plurals are the point: `1 day` and `12 days` are
 * different strings and only `getQuantityString` picks between them correctly in
 * every locale. [StreakUi.None] draws an em dash rather than a zero — a zero is
 * what a *broken* streak reads, and a habit with no history has not broken
 * anything.
 */
internal fun StreakUi.label(context: Context, layout: StreakLayout): String = when (this) {
    is StreakUi.Days -> when (layout) {
        StreakLayout.Compact -> context.getString(R.string.widget_streak_days_compact, count)
        StreakLayout.Full -> context.resources.getQuantityString(R.plurals.widget_streak_days, count, count)
    }

    is StreakUi.Weeks -> when (layout) {
        StreakLayout.Compact -> context.getString(R.string.widget_streak_weeks_compact, count)
        StreakLayout.Full -> context.resources.getQuantityString(R.plurals.widget_streak_weeks, count, count)
    }

    is StreakUi.Broken -> when (layout) {
        // A zero is a zero in either unit, so the compact form has nothing to
        // disambiguate and the `w` would be claiming a count that is not there.
        StreakLayout.Compact -> context.getString(R.string.widget_streak_broken_compact)

        StreakLayout.Full -> context.getString(
            if (weekly) R.string.widget_streak_broken_weeks else R.string.widget_streak_broken_days,
            previous,
        )
    }

    StreakUi.None -> context.getString(R.string.widget_streak_none)
}

/**
 * The ink one streak's numeral is drawn in — the third of `StreakUi`'s signals.
 *
 * `primary` for days and `tertiary` for weeks is the pairing `StreakBadge` uses
 * on the Today row, so the two surfaces agree; docs/ux/visual-identity.md §4.1
 * makes both semantic rather than decorative. A break and an empty history share
 * `outline` because both are the absence of a run, and neither should read louder
 * than a live one.
 */
internal fun StreakUi.tint(): ColorProvider = when (this) {
    is StreakUi.Days -> WidgetPalette.streakDays
    is StreakUi.Weeks -> WidgetPalette.streakWeeks
    is StreakUi.Broken, StreakUi.None -> WidgetPalette.streakBroken
}

/**
 * One streak as TalkBack reads it.
 *
 * Always the [StreakLayout.Full] wording, even while the widget draws
 * [StreakLayout.Compact]: a spoken "12" cannot say whether it counts days or
 * weeks, and the `w` suffix that disambiguates it visually is silent. So the
 * compact form is a visual compression only. [StreakUi.None] gets words rather
 * than the em dash, which speaks as nothing useful.
 */
internal fun StreakUi.spokenLabel(context: Context): String = when (this) {
    StreakUi.None -> context.getString(R.string.widget_streak_none_spoken)

    // "was 12w" would be read out as "was 12 w". The drawn form abbreviates; the
    // spoken one cannot, and it is the unit that must not be lost.
    is StreakUi.Broken -> context.resources.getQuantityString(
        if (weekly) R.plurals.widget_streak_was_weeks_spoken else R.plurals.widget_streak_was_days_spoken,
        previous,
        previous,
    )

    else -> label(context, StreakLayout.Full)
}

/**
 * [asOf] as the "as of" line reads it.
 *
 * `getBestDateTimePattern` rather than a hand-written pattern or
 * `FormatStyle.MEDIUM`: the skeleton asks the platform for *this locale's* way of
 * writing a weekday, a day and a month, which is what a hand-written `EEE d MMM`
 * gets wrong outside English and what MEDIUM overshoots by adding a year nobody
 * needs on a date that is almost always this week.
 */
internal fun formatAsOf(date: LocalDate, locale: Locale): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, AS_OF_SKELETON)
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** Weekday, day-of-month, abbreviated month — no year. */
private const val AS_OF_SKELETON = "EEEdMMM"

/**
 * The streak widget's read.
 *
 * `observeToday()` and nothing new, which is what makes this widget cheap:
 * `TodayHabit` already carries a `StreakSnapshot` and its habit's `Schedule` —
 * both halves of `toUi` — and `TodaySnapshot` carries the logical date the "as
 * of" line needs. docs/ux/visual-identity.md §7.4 had priced a read this
 * repository does not serve; it serves it.
 *
 * The retry and the fallback are [retryThenFail]'s, whose KDoc carries why a
 * widget read needs them at all.
 */
internal fun HabitRepository.streakContent(): Flow<StreakContent> = observeToday()
    .map<TodaySnapshot, StreakContent> { StreakContent.Ready(it.toStreakState()) }
    .retryThenFail(StreakContent.Unavailable)

/**
 * The snapshot as streak rows. Pure, so it is tested without Glance or a device.
 *
 * **Query order, and no sorting.** The mock on the canvas happens to read as
 * sorted and the widget deliberately is not: ordering by the numeral means
 * comparing a count of days against a count of weeks, which is the unsound
 * comparison the one-headline-number direction was rejected for. Sorting within
 * each unit and interleaving the groups would be arbitrary in a different way. So
 * this shows what the Today screen shows, in the order it shows it — the same
 * rule [toWidgetState] follows, and for the same reason: the widget has no view
 * about which habits matter.
 *
 * Habits with no history are kept rather than filtered. They draw an em dash, and
 * dropping them would make the widget's list silently disagree with Today's.
 */
internal fun TodaySnapshot.toStreakState(): StreakUiState = StreakUiState(
    rows = habits.map { StreakRow(name = it.habit.name, streak = it.streak.toUi(it.habit.schedule)) },
    asOf = today,
)
