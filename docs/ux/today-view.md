# Today view: layout and the mascot slot

Companion to [the PRD](../prd.md) §3.5 and §5, and to
[the architecture](../architecture.md) §2. The PRD says the Today view
doubles as Momo's habitat; this document fixes *where*, so Phase 1 mascot
work is an addition rather than a rebuild of the home screen.

**Status:** decided 2026-08-19; partly built 2026-08-20. `:core:ui` and
`:feature:today` now exist and the screen is live. What is built is the
list — §5's rows, and §3's slot holding the Phase 0 indicator. What is
**deferred** is §1's *behaviour*: the panel does not yet collapse into an
app-bar chip on scroll, so the list scrolls under nothing and the
remaining count sits in the panel rather than in the chip. That is a
deferral, not a reversal — §1 is still the decision, and the slot is
already the right size in the right place, so adding the collapse moves
one composable. The list came first deliberately: the data path beneath
it had never run on a device, and a scroll animation and a mood state
machine on the same unproven screen is the wrong thing to debug first.

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
§3.5); §4 below fixes what triggers each. "Regenerating" is not a
euphemism for sad: a broken streak drains the tank of colour and regrows a
gill, and the copy names the habit and offers the repair. It never scolds.
That is the whole reason the mascot is an axolotl.

## 4. Mood states

Provisional — this is Phase 1 behaviour, written down now so the MVP
placeholder and the eventual Rive state machine are driven by the same
rule, and so nothing depends on reading the private sketch canvas.

### Inputs

- **`outstanding`** — non-archived habits due today and not yet satisfied.
  A habit completed for today's logical date is never outstanding, whatever
  its schedule; the per-schedule rules below decide the rest.
  - `Schedule.Daily`: outstanding unless completed for today's logical
    date.
  - `Schedule.Weekly(n)`: let `remaining = n − completions this week` and
    `daysLeft` = days left in the week including today. Outstanding iff
    `remaining > 0 && remaining >= daysLeft`.

    The completion gate above matters here and not only for daily habits:
    without it, 3×/week with none done needs 3 in 2 days on Saturday, and
    completing Saturday leaves `remaining` 2 against a `daysLeft` of 2, so
    the habit would still read outstanding on a day the user turned up.

    That is deliberately **now-or-never**: a 1×/week habit stays quiet
    until its last possible day. Weekly targets are not tied to specific
    days (PRD §4), so treating one as due every day until met would nag
    about a Sunday-able habit on Monday and contradict the schedule type.
- **`nearBoundary`** — now is at or past the configured reminder time and
  before the day boundary. Reuses the setting the end-of-day reminder
  already needs; no second threshold to keep in sync.
- **`recentlyBroken`** — a non-archived habit whose streak is `0` today but
  was `≥1` at the previous rollover. Observable exactly where the data
  layer already recomputes streaks on day rollover.

### Precedence — first match wins

| # | Mood | Condition |
|---|---|---|
| 0 | `content` | no non-archived habits at all |
| 1 | `thriving` | `outstanding` is empty |
| 2 | `regenerating` | `recentlyBroken`, inside the window below |
| 3 | `worried` | `outstanding` non-empty **and** `nearBoundary` |
| 4 | `content` | otherwise |

Rule 0 is load-bearing, not a guard: without it a first run with zero
habits satisfies rule 1 and Momo would greet a brand-new user as thriving.
The first-run artboard draws content.

`thriving` outranking `regenerating` is the deliberate call. Finishing the
day is the way out of the recovery state, so it can never sit there as a
quiet scold — which is the entire point of choosing an animal that regrows
limbs.

### Regenerating: entry and exit

Enters at the rollover that zeroes a streak. Exits at the first day with
nothing outstanding (→ `thriving`), or after **3 logical days** without
recovery, whichever comes first — otherwise an abandoned habit would pin
Momo to a permanent guilt face, which is the failure mode this mood exists
to avoid.

The window is measured against `StreakSnapshot.brokenOn` — the day a break
becomes *visible*, not the day the completion was missed — and in logical
days for **both** schedules, so it runs `brokenOn` through `brokenOn + 2`.
A weekly habit's `brokenOn` is a week start, so it regenerates only on the
first three days of the week its streak zeroed. That is deliberate rather
than a rounding of the weekly case: `regenerating` outranks `worried`, and a
window that lasted the whole week would mask the now-or-never warning on
exactly the days a weekly habit still has one chance left.

The 3-day figure is a guess and was flagged for the 30-day trial, alongside
PRD OQ-3. **That flag does not work, and this section used to contradict its
own §6 by carrying it** (corrected 2026-08-22): the MVP mapping below folds
`regenerating` onto `neutral`, so the trial shows the same face whether this
window is 3 days, 30, or absent. §6 says as much already — decided, tested and
unobservable. The window therefore waits for Phase 1's fourth face, or is
settled on the streak rules alone; PRD §8's OQ-3 records the same. Grace
mechanics, if they ever land, change `recentlyBroken` and therefore this whole
section.

### MVP mapping

Phase 0 ships three states, not four (PRD §5, "happy/neutral/worried"):

| Phase 1 | MVP placeholder |
|---|---|
| `thriving` | happy |
| `content`, `regenerating` | neutral |
| `worried` | worried |

Same slot, same inputs, fewer drawings. That is the §3 contract restated as
behaviour rather than layout: Phase 1 adds art, not logic.

### Where this is computed

A pure function of the projected state, today, now and settings, living in
`:core:domain` beside `Streaks`. It is not stored and not folded into
projection, for the reason already written into `Streaks`' KDoc — it
depends on "today", which is not in the event log, so applying it during
replay would break the incremental-≡-rebuild invariant (architecture §4).

## 5. What the sketch also pins

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

  Those two are **about habit detail, not this screen** — they were settled
  here because the sketch drew a strip, and they were the only record of
  either decision. Both were built on 2026-08-21 and now live in
  [habits.md](habits.md) §7, which is where to change them. Two notes on how
  they came out: the shut day renders struck through and *dimmed* rather than
  dashed, a real dash needing a drawn stroke rather than a border; and the
  strip is five cells, because "Fri 15 renders" is only true if the strip
  reaches a day further back than it can write to.
- **Habit colour appears as the tint behind the row's icon** — one place,
  set once in the create/edit form.
- **The widget carries no mascot at MVP.** It is a bare checklist; the
  emotive indicator is a Today-view element only. Phase 1 does put Momo in
  the widget and the reminder (PRD §5) — that treatment is not designed
  yet and is not decided here.

## 6. Still open

- **PRD OQ-4** — Momo's art style. The canvas art is placeholder line
  work; species and name are the only settled parts.
- ~~**PRD OQ-5** — whether the widget shows streaks.~~ **Settled 2026-08-21:
  minimal, no streak.** This entry said "both answers are drawn; neither is
  chosen" until 2026-08-22, by which point `docs/prd.md` §8 had struck OQ-5
  through and [widget.md](widget.md) §2 carried the reasoning. Left stale, it
  invites a reader to re-open a decided question — which is the failure mode
  AGENTS.md warns about, arriving in a sketch rather than a summary.
- The Phase 1 mascot treatment **in the widget and the reminder** (PRD §5).
  Only the Today-view slot is fixed here.
- Milestone celebrations (7/30/100 days) have no visual treatment yet.
- §1's collapse into an app-bar chip, and the chip itself. See the status
  note above.
- **The `regenerating` copy has nowhere to come from yet.** §3 says it
  "names the habit and offers the repair", but the mood is a bare label —
  it names an artboard, not a habit — and `HabitMoodState` deliberately
  carries no habit identity. Both halves are missing, and the input is the
  cheap one. When this lands the shape is a second pure function beside
  `Mascot.mood`, something like `recentlyBrokenHabits(inputs)` returning
  the ids, rather than a wider `Mood`: one type should not have to carry
  both "which drawing" and "which habit". Recorded because two review
  rounds have now rediscovered it.
- Momo's own copy. Every line the panel shows is placeholder, chosen to
  make the three Phase 0 states distinguishable rather than to be read.
- **`regenerating` is currently invisible.** The MVP mapping above folds it
  onto `neutral`, which shows one line of copy, so nothing on screen
  separates a user recovering from a broken streak from one merely
  pottering. That is what shipping three faces instead of four means, and
  it is fine at Phase 0 — but it does mean `Mood.REGENERATING` is decided,
  tested, and unobservable, so the mood rules cannot be checked by looking
  at the app. Resolved by the same work as the copy gap above.
