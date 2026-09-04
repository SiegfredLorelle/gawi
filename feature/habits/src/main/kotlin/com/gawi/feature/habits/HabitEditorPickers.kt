package com.gawi.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.gawi.core.domain.model.Schedule
import com.gawi.core.ui.component.GawiIconButton
import com.gawi.core.ui.component.GawiIcons
import com.gawi.core.ui.theme.GawiSpacing
import com.gawi.core.ui.theme.HabitPalette
import com.gawi.core.ui.theme.glyphColorOn
import com.gawi.core.ui.theme.parseHabitColor

// The editor's three choosers, and the wrap they share.
//
// A separate file from the screen because together they are more than half of
// it, and because what they have in common is being pickers rather than being
// layout: each one is a fixed set of options where a free-text field would
// otherwise let an invalid value be typed.

/**
 * The icon, chosen from a fixed list.
 *
 * Emoji rather than drawables, because `HabitMetadata.icon` is a String that
 * has to survive an export and an import — a drawable id would not.
 *
 * **Each option is announced by the platform's name for the emoji** ("books"
 * for 📖), because here the emoji *is* the label — unlike `HabitIcon`, which
 * sits beside a name and is decorative. The same defect class the list and
 * Today fixed, left open here: a positional `ICON_LABELS` mirroring
 * [COLOR_LABELS] is the recorded follow-up (docs/ux/habits.md §4).
 */
@Composable
internal fun IconPicker(form: HabitEditorUiState.Form, onEdit: (HabitEditorUiState.Form) -> Unit) {
    // Wrapped rather than scrolled: a horizontal scroller hides options, and
    // there are few enough that a fixed grid of rows fits any phone.
    FlowingRow(HabitPalette.Icons) { icon ->
        val selected = icon == form.icon
        Box(
            modifier = Modifier
                // The swatch itself is the touch target here, not a row
                // containing it — see GawiSpacing.TouchTarget.
                .size(GawiSpacing.TouchTarget)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .selectableBorder(selected)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onEdit(form.copy(icon = icon)) },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * The names assistive technology reads instead of the hex.
 *
 * Positional, matching [HabitPalette.Colors]. `HabitsUiMapperTest` asserts the
 * two lists stay the same length, so adding a swatch without a name is a
 * failing test rather than a swatch that announces itself as
 * "number sign F 2 2 9 3 5".
 *
 * "Gold" and not "Yellow": the retuned hue at that slot is `#9C851F`, which is
 * a gold and not a yellow. These strings are content descriptions rather than
 * decoration (docs/ux/visual-identity.md §4.3), so a label that no longer names
 * its colour is an accessibility defect, and nothing in the suite can catch it
 * — a name is not a checkable property of a hex. §6.2 moved the string with the
 * value for that reason.
 *
 * The leading "current" swatch is deliberately **not** in this list. It has no
 * fixed position and names no hue, so indexing it here would shift every label
 * by one against the palette.
 */
internal val COLOR_LABELS = listOf(
    R.string.habits_color_red,
    R.string.habits_color_pink,
    R.string.habits_color_purple,
    R.string.habits_color_blue,
    R.string.habits_color_teal,
    R.string.habits_color_green,
    R.string.habits_color_gold,
    R.string.habits_color_orange,
)

/** A swatch to draw: the hex it writes, and what assistive technology calls it. */
private data class Swatch(val hex: String, val label: String)

/**
 * Whether two stored hexes are the same swatch — which means the same *colour*,
 * not the same string.
 *
 * Comparing strings does not work here: a colour off the event log does not
 * have to be spelled the way the palette spells it. `HabitState.color` is
 * unvalidated and [parseHabitColor] accepts six digits or eight, upper case or
 * lower, by design. So an imported `"#f22935"` is not a palette member and not
 * equal to the palette's `"#F22935"`: the row grows a leading swatch carrying the
 * lowercase value **and showed it selected**, while the identical colour sat
 * beside it unselected. Two swatches, one colour, the wrong one ticked.
 *
 * Comparing what the two *draw* also covers `"#FFF22935"`, the eight-digit
 * opaque form, which a case-insensitive comparison would still duplicate. A
 * translucent `"#80F22935"` is genuinely a different colour and still gets its
 * own swatch.
 *
 * The string comparison is not a shortcut, it is the fallback for a value
 * [parseHabitColor] cannot read: those parse to null, and null must not match
 * null, or every unparseable hex would be "the same colour" as every other. An
 * unparseable stored value still selects its own swatch this way.
 *
 * Nothing here rewrites the stored value. Casing changes only when the user taps
 * a swatch, which is a write they asked for.
 */
private fun sameColour(a: String, b: String?): Boolean = b != null &&
    (a.equals(b, ignoreCase = true) || parseHabitColor(a)?.let { it == parseHabitColor(b) } == true)

/**
 * The colour, chosen from a fixed palette, which is what keeps it valid — plus
 * one exception the palette cannot cover.
 *
 * **The exception, and why it has to exist.** A habit's colour is raw hex in an
 * append-only log, so retuning [HabitPalette.Colors] migrates nothing and must
 * not: rewriting history to restyle a habit is the thing an event log exists not
 * to do. A habit created before the retune therefore holds a hex this list no
 * longer offers, and selection here is exact string equality, so its editor
 * would open with **nothing** selected. That is worse than untidy — the obvious
 * repair is to tap a swatch, which silently changes a colour the user never
 * asked to change. So a colour the palette does not offer is shown as a leading
 * swatch of its own (docs/ux/visual-identity.md §6.3), already selected — and it
 * **stays** for as long as the form is open, so picking a hue and changing your
 * mind is possible rather than a one-way door.
 *
 * No new mechanism is needed for it: [parseHabitColor] survives arbitrary hex by
 * design and [glyphColorOn] already picks a glyph for anything.
 */
@Composable
internal fun ColorPicker(form: HabitEditorUiState.Form, onEdit: (HabitEditorUiState.Form) -> Unit) {
    // Carrying the label on the item rather than looking it up by index. The
    // leading swatch has no slot in COLOR_LABELS, so a synthetic index would
    // shift every name one place along the palette — a swatch announcing itself
    // as the wrong colour, which §4.3 calls an accessibility defect and which no
    // test can catch.
    val swatches = buildList {
        // form.originalColor, never form.color: see its KDoc. Derived from the
        // live colour this entry vanishes on the first tap and takes the row's
        // layout with it.
        form.originalColor
            ?.takeIf { dropped -> HabitPalette.Colors.none { sameColour(it, dropped) } }
            ?.let { dropped -> add(Swatch(dropped, stringResource(R.string.habits_color_current))) }
        HabitPalette.Colors.forEachIndexed { index, hex ->
            // getOrNull, not [index]. The palette lives in :core:ui while these
            // labels live here, so whoever adds a swatch is in the other module
            // and will not see HabitsUiMapperTest's length assertion until CI.
            // Reading the hex aloud is a poor announcement; taking the editor
            // down with an IndexOutOfBoundsException is worse.
            val labelRes = COLOR_LABELS.getOrNull(index)
            add(Swatch(hex, if (labelRes != null) stringResource(labelRes) else hex))
        }
    }
    FlowingRow(swatches) { (hex, label) ->
        val selected = sameColour(hex, form.color)
        // Never null for a palette entry — HabitColorTest pins that — so the
        // fallback here is for a colour that arrived from somewhere else, which
        // is exactly what the leading swatch above can be.
        val tint = parseHabitColor(hex) ?: MaterialTheme.colorScheme.secondaryContainer
        Box(
            modifier = Modifier
                .size(GawiSpacing.TouchTarget)
                .clip(CircleShape)
                .background(tint)
                .selectableBorder(selected)
                .selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onEdit(form.copy(color = hex)) },
                )
                // Clearing, not merging: the selected swatch draws a tick, and
                // TalkBack 17 reads a merged node's child text after its
                // description (docs/running.md §4) — "Gold,
                // selected, check mark". After `selectable`, which carries the
                // role, the state and the click: a clearing modifier discards
                // every semantics modifier later in the chain.
                .clearAndSetSemantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleSmall,
                    color = glyphColorOn(tint, MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}

/**
 * Daily or weekly, and for weekly a target the stepper cannot take out of range.
 *
 * The clamp is the point. `Schedule.Weekly` validates with `require`, so a
 * target outside 1..7 throws rather than being rejected — an unbounded stepper
 * would crash on save rather than showing an error.
 */
@Composable
internal fun SchedulePicker(form: HabitEditorUiState.Form, onEdit: (HabitEditorUiState.Form) -> Unit) {
    val weekly = form.schedule as? ScheduleUi.Weekly
    Column(verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
            FilterChip(
                selected = weekly == null,
                onClick = { onEdit(form.copy(schedule = ScheduleUi.Daily)) },
                label = { Text(stringResource(R.string.habits_schedule_daily_option)) },
            )
            FilterChip(
                selected = weekly != null,
                // Keeps the target it already has. Writing the default
                // unconditionally meant tapping the already-selected chip
                // silently knocked a Weekly(6) habit back to 3.
                onClick = {
                    onEdit(form.copy(schedule = ScheduleUi.Weekly(weekly?.timesPerWeek ?: DEFAULT_WEEKLY_TARGET)))
                },
                label = { Text(stringResource(R.string.habits_schedule_weekly_option)) },
            )
        }
        if (weekly != null) {
            WeeklyTargetStepper(weekly.timesPerWeek) { onEdit(form.copy(schedule = ScheduleUi.Weekly(it))) }
        }
    }
}

@Composable
private fun WeeklyTargetStepper(target: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap),
    ) {
        GawiIconButton(GawiIcons.Minus, R.string.habits_target_fewer, enabled = target > MIN_WEEKLY_TARGET) {
            onChange(target - 1)
        }
        Text(
            text = stringResource(R.string.habits_weekly_target, target),
            style = MaterialTheme.typography.bodyLarge,
        )
        GawiIconButton(GawiIcons.Plus, R.string.habits_target_more, enabled = target < Schedule.DAYS_PER_WEEK) {
            onChange(target + 1)
        }
    }
}

/**
 * A fixed-width wrap, because `FlowRow` is still experimental in material3 and
 * this needs one behaviour from it.
 */
@Composable
private fun <T> FlowingRow(items: List<T>, content: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
        items.chunked(SWATCHES_PER_ROW).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(GawiSpacing.Gap)) {
                chunk.forEach { content(it) }
            }
        }
    }
}

/** The selected ring. A border rather than a scale, so nothing reflows on tap. */
@Composable
private fun Modifier.selectableBorder(selected: Boolean): Modifier =
    if (selected) border(SELECTION_RING, MaterialTheme.colorScheme.onSurface, CircleShape) else this

private val SELECTION_RING = 3.dp

private const val SWATCHES_PER_ROW = 5
private const val MIN_WEEKLY_TARGET = 1
private const val DEFAULT_WEEKLY_TARGET = 3
