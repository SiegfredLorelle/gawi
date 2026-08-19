# Today view: layout and the mascot slot

Companion to [the PRD](../prd.md) §3.5 and §5, and to
[the architecture](../architecture.md) §2. The PRD says the Today view
doubles as Momo's habitat; this document fixes *where*, so Phase 1 mascot
work is an addition rather than a rebuild of the home screen.

**Status:** decided 2026-08-19. Nothing is implemented yet —
`:feature:today` and `:core:ui` do not exist.

Sketch canvas (private artifact, 11 low-fi artboards):
<https://claude.ai/code/artifact/83307fe6-0ec3-43c1-bcc4-f2e8a3453f95>

---

## 1. The decision: a collapsing hero header

Momo lives in a **fixed-height panel between the app bar and the habit
list**. On scroll it collapses into a chip in the app bar carrying the
mood and the remaining count; the habit list scrolls underneath.

Rationale, in the order it mattered:

1. **The list stays legible.** Habit rows sit on plain surface, never over
   a tinted, animated background. Row contrast is not a function of
   Momo's mood.
2. **Mood is a self-contained panel.** A mood change restyles one bounded
   composable. Phase 1 swaps a Rive view into that box and touches nothing
   else on the screen.
3. **It is the cheapest thing to build correctly.** A collapsing header is
   ordinary Compose; an animated full-screen backdrop behind a scrolling
   list is not.

Accepted cost: Momo is off-screen while you scroll a long list — arguably
the moment the mascot is meant to matter. The collapsed chip is the
mitigation, and it is deliberately small. If the trial (PRD §5, 30-day
success criterion) shows the mascot going unnoticed, revisit *this* line
before revisiting the decision.

## 2. Rejected, and why

**A — ambient tank.** The whole screen is the habitat; habit cards float
over water and Momo drifts behind them. The strongest read of PRD §3.5 and
the most charming. Rejected on two counts: every row needs an opaque card
to stay readable, which costs the list its calm; and mood becomes a
whole-screen restyle, the largest possible Phase 1 surface area.

**C — bottom dock.** A shallow habitat strip pinned below the list. Momo
is visible at every scroll position and the list keeps nearly full height.
Rejected because at ~120dp the mascot reads as a status bar with a face —
the least room for Phase 1 animation to land, which defeats the point of
committing to Rive.

Both remain on the canvas so the tradeoff that was accepted stays legible
later.

## 3. The mascot slot contract

**The mascot has one slot.** The MVP emotive indicator (PRD §5, Phase 0)
and the Phase 1 Rive character occupy the same box, at the same size, in
the same place. MVP paints a static face; Phase 1 swaps in a state
machine. Nothing around it moves.

Mood vocabulary is **thriving / content / worried / regenerating** (PRD
§3.5) and the trigger for each is on the canvas mood sheet.
"Regenerating" is not a euphemism for sad: a broken streak drains the
tank of colour and regrows a gill, and the copy names the habit and offers
the repair. It never scolds. That is the whole reason the mascot is an
axolotl.

## 4. What the sketch also pins

Small decisions that were easier to make once drawn:

- **A daily habit's streak is a count; a weekly habit's is in weeks.** A
  weekly row reads `2/3 this week` in its subtitle and `3w` in the streak
  slot. The two must never be styled as the same number.
- **An incomplete daily habit still shows its live streak.** Per
  `Streaks.dayStreak`, an unfinished current day has not broken anything —
  it simply has not extended it. A row unchecked at 09:00 must not read
  `0`.
- **A broken streak keeps its old value as context** (`was 4`) next to the
  `0`, with a cut-thread glyph.
- **Days outside the retro window are drawn shut**, not tapped and
  refused. With today at Tue 19, Sat 16 is the oldest open day and Fri 15
  renders struck through and dashed. The command rule should be readable
  before it is hit.
- **"Clear note" is a button, not a disabled Save.** An empty note is a
  real write that wins LWW like any other (architecture §4), so the sheet
  offers it explicitly.
- **Habit colour appears as the tint behind the row's icon** — one place,
  set once in the create/edit form.
- **The widget carries no mascot** at any phase. Glance cannot observe
  Room and has no room for a habitat.

## 5. Still open

- **PRD OQ-4** — Momo's art style. The canvas art is placeholder line
  work; species and name are the only settled parts.
- **PRD OQ-5** — whether the widget shows streaks. Both answers are drawn;
  neither is chosen.
- Milestone celebrations (7/30/100 days) have no visual treatment yet.
