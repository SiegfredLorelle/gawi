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
 * is what lets the precedence table be tested exhaustively rather than case by
 * remembered case.
 *
 * All four are drawn since 2026-08-25 (docs/ux/momo.md §3). `MvpMood`, which
 * folded them onto Phase 0's three faces, went with the placeholder it served.
 */
enum class Mood { THRIVING, CONTENT, WORRIED, REGENERATING }
