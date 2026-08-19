package com.gawi.core.domain.mascot

/**
 * What Momo is feeling: the Phase 1 mood vocabulary (PRD §3.5,
 * docs/ux/today-view.md §3), computed by [Mascot.mood].
 *
 * `REGENERATING` is not a euphemism for sad. A broken streak drains the tank
 * of colour and regrows a gill; the copy names the habit and offers the
 * repair, and it never scolds. That is the whole reason the mascot is an
 * axolotl, and it is why [Mood.THRIVING] outranks it in the precedence table.
 *
 * An `enum` rather than the `sealed interface` plus `data object` this module
 * uses for its other closed vocabularies (`Schedule`, `CommandError`): those
 * model members that carry data or plausibly will, whereas a mood is a bare
 * label forever — it names an artboard. What the enum buys is `entries`, which
 * is what lets the precedence table and [toMvp] be tested exhaustively rather
 * than case by remembered case.
 */
enum class Mood { THRIVING, CONTENT, WORRIED, REGENERATING }

/**
 * The three faces Phase 0 actually draws (PRD §5, docs/ux/today-view.md §4
 * "MVP mapping"). Fewer drawings, same slot, same inputs.
 */
enum class MvpMood { HAPPY, NEUTRAL, WORRIED }

/**
 * Collapses the four Phase 1 moods onto the three the MVP placeholder has art
 * for. `CONTENT` and `REGENERATING` share the neutral face.
 *
 * The mapping lives here rather than in the UI so that Phase 1 adds art and
 * not logic: swapping the static face for the Rive state machine deletes the
 * call to this, and changes nothing about how the mood is decided.
 */
fun Mood.toMvp(): MvpMood = when (this) {
    Mood.THRIVING -> MvpMood.HAPPY
    Mood.CONTENT, Mood.REGENERATING -> MvpMood.NEUTRAL
    Mood.WORRIED -> MvpMood.WORRIED
}
