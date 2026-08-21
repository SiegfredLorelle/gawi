package com.gawi.widget

import androidx.annotation.StringRes
import com.gawi.core.data.model.TodaySnapshot
import com.gawi.core.data.repository.HabitRepository
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

/** Everything the widget draws, and nothing else. */
internal data class WidgetUiState(val rows: List<WidgetRow>)

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

    data class Rows(val rows: List<WidgetRow>) : WidgetBodyContent

    data class Copy(@StringRes val text: Int) : WidgetBodyContent

    /**
     * Nothing at all. The first frame of a cold render, replaced as soon as the
     * flow emits; a "loading" line would be the only text most renders showed.
     */
    data object Blank : WidgetBodyContent
}

/**
 * Which of the three the user sees. Pure, so it is tested without Glance.
 *
 * A res id rather than a resolved string, so a test asserts the same
 * `R.string` constant the composable reads — the convention
 * `TodayMessage(@StringRes val text: Int)` already sets in `:feature:today`.
 */
internal fun WidgetContent.body(): WidgetBodyContent = when (this) {
    WidgetContent.Unavailable -> WidgetBodyContent.Copy(R.string.widget_unavailable)

    WidgetContent.Loading -> WidgetBodyContent.Blank

    is WidgetContent.Ready ->
        if (state.rows.isEmpty()) WidgetBodyContent.Copy(R.string.widget_no_habits) else WidgetBodyContent.Rows(state.rows)
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
 * there, which is correct — there is nothing else to show — and is bounded by
 * the session's own lifetime and the provider's update period.
 *
 * The `catch` sits after the `map`, so a failed read replaces the whole content
 * rather than one row. Cancellation is never retried and never caught as a
 * failure.
 */
internal fun HabitRepository.widgetContent(): Flow<WidgetContent> = observeToday()
    .map<TodaySnapshot, WidgetContent> { WidgetContent.Ready(it.toWidgetState()) }
    .retryWhen { cause, attempt -> cause !is CancellationException && attempt < READ_RETRIES && delayThenRetry() }
    .catch { emit(WidgetContent.Unavailable) }

/** Always true; exists so the retry predicate reads as one expression. */
private suspend fun delayThenRetry(): Boolean {
    delay(RETRY_BACKOFF_MILLIS)
    return true
}

/**
 * Three attempts at 150ms. Short on purpose: the widget draws nothing while a
 * retry is in flight, so a generous backoff would trade a wrong answer for a
 * blank one.
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
internal fun TodaySnapshot.toWidgetState(): WidgetUiState =
    WidgetUiState(rows = habits.map { WidgetRow(habitId = it.habit.id.value, name = it.habit.name, completed = it.completedToday) })
