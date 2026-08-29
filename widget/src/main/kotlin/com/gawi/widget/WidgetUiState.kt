package com.gawi.widget

import androidx.annotation.StringRes
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
import com.gawi.core.domain.mascot.Mascot
import com.gawi.core.domain.mascot.Mood
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * One habit as the widget draws it: a name and whether today's cell is ticked.
 *
 * The id is a plain [String], not a [com.gawi.core.domain.model.HabitId]. It
 * travels to the tap callback through `ActionParameters`, which holds only
 * bundle-able types — and `HabitId`'s constructor *throws* on anything that is
 * not a canonical UUIDv7, so building one from a parameter would move that throw
 * into a broadcast receiver. The callback matches this string against the log
 * instead, so a malformed parameter is a habit that does not exist.
 *
 * No streak: PRD OQ-5 is settled minimal (docs/ux/widget.md §2), and leaving the
 * field out of the type is what keeps that decision from being undone by an
 * accident of what was in scope.
 */
internal data class WidgetRow(val habitId: String, val name: String, val completed: Boolean)

/**
 * Everything the widget draws, and nothing else.
 *
 * The mood is the Today screen's — [Mascot.mood] over the same snapshot — so the
 * widget's still frame and the app's animated one never disagree about which
 * face today gets (docs/ux/momo.md §4). Whether it is *drawn* is decided by
 * [body], from the size the host gave this instance.
 */
internal data class WidgetUiState(val rows: List<WidgetRow>, val mood: Mood)

/**
 * What the widget has to draw right now.
 *
 * Three states rather than a nullable [WidgetUiState], because "not read yet"
 * and "could not be read" have to look different: collapsing them would flash
 * the failure copy on every cold render, and drawing an empty list for a broken
 * database is the failure-towards-silence the export nudge took three review
 * rounds to stamp out.
 */
internal sealed interface WidgetContent {

    /** Before the first emission arrives. */
    data object Loading : WidgetContent

    /**
     * The read threw. `SQLiteException` is a `RuntimeException` and the settings
     * store refuses to guess a cutoff, so neither is hypothetical.
     */
    data object Unavailable : WidgetContent

    data class Ready(val state: WidgetUiState) : WidgetContent
}

/**
 * The one thing the widget's body draws, chosen from [WidgetContent].
 *
 * This exists so the choice is a value rather than a branch inside a composable.
 * Which copy a broken database gets is exactly the kind of rule a later edit can
 * invert by accident — swapping two `Message` calls used to be a one-character
 * change no test could catch — and it is now decided in [body], which is tested.
 */
internal sealed interface WidgetBodyContent {

    /** Momo's face to draw above the body, or null for none. A value, so the size gate is tested without Glance. */
    val mood: Mood?

    data class Rows(val rows: List<WidgetRow>, override val mood: Mood?) : WidgetBodyContent

    /**
     * The large body (docs/ux/widget.md §7): Momo on her ground beside the mood
     * line and the woven day band, above the same rows. The mood is not
     * nullable here — this body exists to draw it — and the band is the rows'
     * own `completed` flags, one segment each, so it can never disagree with
     * the checkboxes beneath it.
     */
    data class Large(val rows: List<WidgetRow>, override val mood: Mood) : WidgetBodyContent

    data class Copy(@StringRes val text: Int, override val mood: Mood? = null) : WidgetBodyContent

    /**
     * Nothing at all. The first frame of a cold render, replaced as soon as the
     * flow emits; a "loading" line would be the only text most renders showed.
     */
    data object Blank : WidgetBodyContent {
        override val mood: Mood? get() = null
    }
}

/**
 * Which of the three the user sees, and whether Momo sits above it. Pure, so it
 * is tested without Glance.
 *
 * A res id rather than a resolved string, so a test asserts the same
 * `R.string` constant the composable reads — the convention
 * `TodayMessage(@StringRes val text: Int)` already sets in `:feature:today`.
 *
 * **Momo appears only when the host gave the widget room**: [size] is what
 * `SizeMode.Exact` reports, and a height under [MOMO_MIN_HEIGHT] keeps the
 * minimal one-cell widget docs/ux/widget.md §2 settled — a name and a
 * checkbox, nothing else. It is a rule about *room*, not about cells or
 * orientation: a two-cell widget that clears 170dp in portrait and not in
 * landscape shows the face in one and not the other, because `Exact`
 * composes once per size the host reports. Accepted — the rows keep their
 * room either way, which is the property the gate exists for. Unavailable
 * never gets a face: nothing was read, and a
 * guessed mood would be the wrong answer [widgetContent] refuses to trade a
 * blank one for.
 */
internal fun WidgetContent.body(size: DpSize): WidgetBodyContent = when (this) {
    WidgetContent.Unavailable -> WidgetBodyContent.Copy(R.string.widget_unavailable)

    WidgetContent.Loading -> WidgetBodyContent.Blank

    is WidgetContent.Ready -> {
        val tall = size.height >= MOMO_MIN_HEIGHT.dp
        val mood = state.mood.takeIf { tall }
        when {
            state.rows.isEmpty() -> WidgetBodyContent.Copy(R.string.widget_no_habits, mood)
            tall && size.width >= LARGE_MIN_WIDTH.dp -> WidgetBodyContent.Large(state.rows, state.mood)
            else -> WidgetBodyContent.Rows(state.rows, mood)
        }
    }
}

/**
 * The least height, in dp, at which Momo is drawn.
 *
 * The provider's `minHeight` is 110dp — one grid cell on every launcher
 * measured — and two cells land at 220 or more, so 170 separates the two
 * without depending on any one launcher's cell size. Above it, [MomoBitmap]'s
 * 72dp face still leaves at least 82dp for rows after the padding, so Momo
 * never displaces every habit. Rather than a resize breakpoint from the
 * provider xml, because the API 31 attributes that would express one are a
 * `res/xml-v31` variant this widget does not carry (visual-identity §7.4).
 */
internal const val MOMO_MIN_HEIGHT = 170

/**
 * The least width, in dp, at which the large body is drawn — with the height
 * gate above, both are needed, the way the streak widget's `FULL_MIN_WIDTH` and
 * `FULL_MIN_HEIGHT` are.
 *
 * The header puts the mood line *beside* Momo rather than under her, and that
 * costs width the face-above-rows body does not: at the provider's 180dp, 164dp
 * of usable width less a 66dp pill and 10dp of gap leaves 88dp for the copy,
 * and no mood line fits that in two lines of caption type — the regenerating
 * one is 47 characters. 220 is the streak widget's threshold for the same kind
 * of reason, and it sits between the 180dp three-cell minimum and the 250dp
 * four-cell placement the canvas drew, so a tall-but-narrow widget keeps the
 * face above the rows and a four-by-three one gets the header
 * (docs/ux/widget.md §7).
 */
internal const val LARGE_MIN_WIDTH = 220

/**
 * What TalkBack reads for the face, one line per mood, in the Today panel's
 * words (`today_mood_*` in `:feature:today`, copied: a feature module is not
 * on the widget's classpath, and sharing them would mean a third thing on the
 * `:core:ui` edge for four sentences). Only the rows body describes its Momo; beside the
 * no-habits copy the face is decorative, so the copy is read once
 * (docs/ux/momo.md §4).
 */
@StringRes
internal fun Mood.description(): Int = when (this) {
    Mood.THRIVING -> R.string.widget_mood_thriving
    Mood.CONTENT -> R.string.widget_mood_content
    Mood.WORRIED -> R.string.widget_mood_worried
    Mood.REGENERATING -> R.string.widget_mood_regenerating
}

/**
 * The widget's read, as a flow of what to draw.
 *
 * Assembled here rather than inside `provideContent`, because flow operators
 * must not be invoked in composition — they allocate a new flow per
 * recomposition, and Android Lint's `FlowOperatorInvokedInComposition` is fatal
 * under `warningsAsErrors`. Being a function also makes it reachable from a
 * test, which is how the failure branch is covered.
 *
 * **A transient failure is retried, because a widget cannot re-subscribe for
 * itself.** `catch` terminates a flow, so without the retry one throw would end
 * collection for the life of the Glance session — and the push cannot repair
 * that, since `update` on a live session never re-enters `provideGlance`
 * ([TodayWidget]). A screen recovers when its `WhileSubscribed` window lapses
 * and it re-subscribes; nothing does that for a widget. That is the one place
 * the two-mechanism argument does not hold on its own, so a bounded retry closes
 * it. Kept short and finite: while retrying nothing is emitted, so the widget
 * shows [WidgetContent.Loading], which draws nothing.
 *
 * A *persistent* failure still lands on [WidgetContent.Unavailable] and stays
 * there, which is correct — there is nothing else to show. How long it stays is
 * not something this code can promise: it clears when the Glance session ends,
 * or when the provider's update period next gets through (§4).
 *
 * The `catch` sits after the `map`, so a failed read replaces the whole content
 * rather than one row. Cancellation is never retried and never caught as a
 * failure.
 */
internal fun HabitRepository.widgetContent(): Flow<WidgetContent> = observeToday()
    .map<TodaySnapshot, WidgetContent> { WidgetContent.Ready(it.toWidgetState()) }
    .retryThenFail(WidgetContent.Unavailable)

/**
 * The bounded retry and the terminal fallback, shared by every widget read.
 *
 * Shared rather than copied because the argument above is the expensive part and
 * it is identical for both providers: a widget cannot re-subscribe for itself, so
 * a bare `catch` would end collection for the life of the session. The *values*
 * differ — each provider has its own "unavailable" — which is what [failure] is.
 * Extracted 2026-08-29 with the streak widget rather than duplicated, so a change
 * to the retry policy cannot apply to one widget and not the other.
 */
internal fun <T> Flow<T>.retryThenFail(failure: T): Flow<T> = this
    .retryWhen { cause, attempt -> cause !is CancellationException && attempt < READ_RETRIES && delayThenRetry() }
    .catch { emit(failure) }

/** Always true; exists so the retry predicate reads as one expression. */
private suspend fun delayThenRetry(): Boolean {
    delay(RETRY_BACKOFF_MILLIS)
    return true
}

/**
 * Three **retries**, at 150ms each — so at most *four* reads, the first plus
 * three, and ~450ms of drawing nothing in the worst case. `retryWhen`'s
 * `attempt` is zero-based, which is what makes the count off by one from the
 * obvious reading; `WidgetContentTest` pins both edges with literal numbers so
 * the distinction cannot drift.
 *
 * Short on purpose: nothing is emitted while a retry is in flight, so a generous
 * backoff would trade a wrong answer for a blank one.
 */
private const val READ_RETRIES = 3L
private const val RETRY_BACKOFF_MILLIS = 150L

/**
 * The snapshot as rows. Pure, so it is tested without Glance or a device.
 *
 * A straight projection in query order, including habits that are already done.
 * Two consequences, both wanted: a completed row can be tapped again to undo it,
 * and the widget has no rule of its own about which habits are worth showing —
 * it shows what the Today screen shows. `observeToday()` has already dropped
 * archived habits, so nothing here filters.
 */
internal fun TodaySnapshot.toWidgetState(): WidgetUiState = WidgetUiState(
    rows = habits.map { WidgetRow(habitId = it.habit.id.value, name = it.habit.name, completed = it.completedToday) },
    // The same call TodayUiMapper makes, on the same snapshot.
    mood = Mascot.mood(moodInputs()),
)
