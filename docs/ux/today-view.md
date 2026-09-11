# Today view: layout and the mascot slot

Companion to [the PRD](../prd.md) §3.5 and §5, and to
[the architecture](../architecture.md) §2. The PRD says the Today view
doubles as Momo's habitat; this document fixes *where*, so Phase 1 mascot
work is an addition rather than a rebuild of the home screen.

**Status:** decided 2026-08-19; built out over 2026-08-20 to 2026-08-31.
`:core:ui` and `:feature:today` exist, the screen is live, and §1 is now
built rather than deferred. The list came first deliberately: the data
path beneath it had never run on a device, and a scroll animation and a
mood state machine on the same unproven screen is the wrong thing to
debug first. It has since run on four.

**§1 landed in a shape §1 did not describe, and the difference is
deliberate.** The panel is not a fixed header above the list; it is the
list's first item, and it scrolls away with everything else. A 250 dp
tank ([momo.md](momo.md) §4) above an unscrollable column puts the
button, or the second row, below the fold on a small screen or at a
large font scale. What survived intact is the chip: once the panel has
scrolled off, the app bar carries the mood and the remaining count in
the title's place — and, since 2026-09-01, the milestone line in the
count's place for the length of a run. The decision — Momo in a bounded
box, rows on plain surface, a chip as the mitigation — is unchanged;
only "fixed" is.

Sketch boards (11 low-fi artboards, 2026-08-19): the "Today sketch (archive)"
page of the Gawi Redesign canvas, where they were folded in on 2026-08-25 when
the standalone sketch link was retired —
<https://claude.ai/code/artifact/f2c92c47-58a4-4547-bff5-695fa3705c17>

---

## 1. The decision: a collapsing hero header

Momo lives in a **fixed-height panel between the app bar and the habit
list**. On scroll it collapses into a chip in the app bar carrying the
mood and the remaining count — or the milestone line in the count's
place, while one runs; the habit list scrolls underneath.

Rationale, in the order it mattered:

1. **The list stays legible.** Habit rows sit on plain surface, never over
   a tinted, animated background. Row contrast is not a function of
   Momo's mood — it is a function of the row's own state, which is what
   lets §5 dim a row that owes nothing today without reopening this.
2. **Mood is a self-contained panel.** A mood change restyles one bounded
   composable. Phase 1 swaps a Rive view into that box and touches nothing
   else on the screen.
3. **It is the cheapest thing to build correctly.** A collapsing header is
   ordinary Compose; an animated full-screen backdrop behind a scrolling
   list is not.

Accepted cost: Momo is off-screen while you scroll a long list — arguably
the moment the mascot is meant to matter. The collapsed chip is the
mitigation, and it is deliberately small. This used to name the 30-day trial
as the thing that would show the mascot going unnoticed; the trial was waived
(PRD §5, 2026-08-23). The occasion was then **Phase 1's real mascot**, the first
build on which this cost is measurable at all — the placeholder was three static
faces, and nobody would notice *those* going unnoticed.

**Both halves exist since 2026-08-31**, so what the chip mitigates is at last a
real thing: a 250 dp animated tank leaves the screen after one flick, and what
replaces it is a face small enough for the app bar and a count. That is a fair
trade rather than a good one, and the honest reading is that this is still an
accepted cost — the chip keeps the mood *reachable*, not present. Revisit it
against a device look, not against this paragraph.

**Open, found on the device the day it was built:** the chip's trigger is "the
panel has left the viewport entirely", and on a short list it never fires. On a
720×1280 screen at 320 dpi the list viewport is 1056 px, the panel is 628 px and
a row is 128 px, so the list can only scroll the panel fully off once there are
**nine habits**; with four it scrolls to a sliver of tank and stops, and the bar
still says "Today". There are two readings and they have not been chosen
between. One: this is correct, because the chip exists for the long list this
section's accepted cost is about, and with four habits Momo has not really gone.
Two: the sliver is the worst of both and the trigger should fire when the panel
is *mostly* gone rather than wholly, which is one threshold and no new state.
Recorded rather than fixed, because it changes when the chip appears for every
user and that is a design call, not a bug fix. `docs/running.md` §4 carries the
measurement as an unticked box.

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

Enters at the rollover that zeroes a streak. **There are two ways out, and a
backstop.** The day goes clear, and rule 1 above draws thriving instead — that
one is precedence rather than an exit, and it is unchanged. Or the broken habit
is mended, which ends the state itself. Failing both, it lapses after **3
logical days**.

**The mended-habit exit is the repair the line asks for**, and it is the one
this section did not have. Rule 1 answers for the whole day: finish everything
and the face is thriving whatever is broken underneath. What nothing answered
was the habit the panel *names* — so the line could say *"pick Stretch back up"*
to somebody who had just picked Stretch back up and go on saying it for two more
days, for as long as some other habit kept the day from going clear.

**For a weekly habit, mended means the week's target met**, not a completion
written. A completion short of the target leaves the streak at zero with
`brokenOn` still set (`Streaks.weeklySnapshot`), which is the same fact §6 below
already turns into the reason the unnamed line exists. Reusing the streak's own
predicate is what stops a weekly habit leaving this state on a tick that
repaired nothing.

`Mascot.mood` tests only the window today, so the mended check is what step 3
adds to it; `recentlyBrokenHabits` already filters `completedToday` for the
daily case, which is the half that exists.

**The 3 days are a backstop, not a model of recovery.** They exist for the
habit that is never picked up again: without them an abandoned habit pins Momo
to a permanent guilt face, which is the failure this mood exists to avoid, and
no completion is ever coming to end it. That is a narrower job than the number
used to have — it no longer has to say how long recovery takes, only how long
to keep asking — and 3 is an adequate answer to it. The guess is retired by
shrinking what it decides rather than by being measured.

The backstop is counted against `StreakSnapshot.brokenOn` — the day a break
becomes *visible*, not the day the completion was missed — and in logical days
for **both** schedules, so it runs `brokenOn` through `brokenOn + 2`. A weekly
habit's `brokenOn` is a week start, so it regenerates only on the first three
days of the week its streak zeroed. That is deliberate rather than a rounding
of the weekly case: `regenerating` outranks `worried`, and a window that lasted
the whole week would mask the now-or-never warning on exactly the days a weekly
habit still has one chance left. It is also why the window did not simply grow
to match the seven clean days a gill takes to regrow: the gills carry that arc
themselves (momo.md §3), on their own clock, and the face does not have to.

**Gills make this face rarer, and mean more by it.** A streak no longer breaks
on a miss — it breaks on the miss after the last spare gill is spent — so
reaching this mood can take four missed days rather than one. What the face
says is unchanged; what it costs to see it is not.

### MVP mapping — history since 2026-08-25

All four moods are drawn now ([momo.md](momo.md) §3), each with its own line,
and `Mood.toMvp` is gone. Kept because it records what shipping three faces
meant while it lasted. Phase 0 shipped three states, not four (PRD §5,
"happy/neutral/worried"):

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
- **What a row speaks is not what it draws (2026-09-02).** The icon is
  decorative — the name is beside it, and TalkBack 17 had been reading the
  emoji's Unicode name ahead of it — and the streak is spoken in the panel's
  own phrase, *"3 days in a row"*, *"1 week in a row"*, *"Streak broken, was 12
  days"*, because a `3` or `3w` read aloud cannot say what it counts (the widget
  made the same call in `spokenLabel`). The words are `:core:ui`'s
  `spokenStreak`, shared with habit detail's panel (habits.md §7). Both via
  `clearAndSetSemantics`, which is why the drawn `3` is not in the row's text;
  `row_doesNotSpeakTheIcon`, `streak_speaksItsUnit` and
  `brokenStreak_speaksWhatWasLost` pin it, and hearing it is owed
  (docs/running.md §4) — including the order: the badge's description is on a
  child node after the name, so the name should still lead.
- **A row that owes nothing today is dimmed, and that is not the same as
  ticked.** Twelve empty checkboxes under a panel reading *"8 of 15 left
  today"* is the screen contradicting itself: four of them were weekly habits
  that had already met their target, or whose now-or-never day had not arrived.
  The checkbox says *completed*; nothing said *owed*. So the rows §4's
  `outstanding` excludes are drawn at reduced emphasis, and
  `Mascot.isOutstanding` is what decides — the same predicate the panel already
  counts with, computed per row in the mapper rather than a second time in the
  composable. **Decided on the canvas-fidelity pass and not yet scheduled**
  (PRD §5's 1.x); three forms were drawn and dimming won because it adds no
  element, spends no width and needs no legend. It changes what a row *speaks*
  as well as what it draws: a set of rows visually set aside that a screen
  reader does not mention is information only some users get.
- **The empty screen centres its notice in the space below the tank.**
  `EmptyToday` already asks for `Arrangement.Center` and it decides nothing,
  because the column wraps its content: the notice and its button sit directly
  under the 250 dp tank with the rest of the screen blank beneath them. The
  notice takes the height that is left instead, which keeps §3's promise that
  the mascot slot does not move when the character replaces the placeholder — a
  shorter tank and a floating one were both drawn, and both buy composition by
  making Momo one size on a fresh install and another the moment a habit
  exists. **The leftover height is a minimum, not a weight.** A weighted child
  of a scrolling column is measured to exactly the space left over and cannot
  grow past it, so at a large font scale the notice is clipped by the scroll
  that exists to prevent exactly that; a minimum height centres the same way
  and still lets the column push past the viewport. Decided on the
  canvas-fidelity pass. The habit list and Insights already centre theirs
  through `:core:ui`'s `Notice`, and Settings on a fresh install is not an
  empty state at all — an unwritten preferences file reads as defaults, so it
  draws the full list.
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

## 6. Open, and what closed

- **PRD OQ-4** — Momo's art style — is decided and built:
  [momo.md](momo.md). The slot is the redesign's 250 dp tank rather than a
  96 dp floor for copy; momo.md §4 records why that keeps §3's promise.
- **PRD OQ-5** — whether the widget shows streaks — is settled as **minimal, no
  streak**. Reasoning in [widget.md](widget.md) §2.
- The Phase 1 mascot treatment **in the widget and the reminder** (PRD §5).
  Only the Today-view slot is fixed here.
- Milestone celebrations (7/30/100 days) have their treatment
  ([momo.md](momo.md) §5 and §6): the tank plays a bigger sequence than finishing the day's, the panel's line swaps to the
  milestone line for the run, and §5's streak badge sits on a pill of its
  container role and swells — the day/week distinction holds on the pill as
  it does on the number.
- §1's collapse into an app-bar chip, and the chip itself, are built. The panel
  keeps its place as the list's first item — the status note says why that is not a reversal — and the chip
  appears once it has scrolled off, carrying a small face and a short count in
  the title's place. It replaces the title rather than joining it, because a
  title, a chip and three action icons do not fit across one bar at a large font
  scale.

  **It is not a live region, and the first rationale for that was wrong.** It
  said the panel's copy is "only scrolled off rather than gone", so a second
  live region would read the mood twice. A review pointed out that the panel is
  a `LazyColumn` item and is *disposed* once the chip is up — `HabitList` says so
  itself, and the chip's own test proves it by asserting the panel's count no
  longer exists. The two never coexist, so there was never a double read.

  What that leaves is a **silence**, and it is now a choice rather than a side
  effect: a tick made while the chip is up changes only its description, and a
  description change on a non-live node is not announced. Kept, for three
  reasons. The row's own checkbox announces its state change, so the tick is not
  feedback-free. A live region fires when its node *appears* as well as when it
  changes, so every scroll past the tank would speak the whole sentence — a poor
  trade for something this section calls "deliberately small". And no emulator
  image here has TalkBack, so a live region would ship unheard. **Open**: make it
  a polite live region, on a device that can verify what it actually sounds
  like. `chip_isNotALiveRegion` pins today's answer either way. **Heard on a
  device 2026-09-02** (Nothing A059, TalkBack 17, docs/running.md §4): with the
  chip up, ticking a row spoke *"checked"* from the row and nothing from the
  chip — the silence is exactly as designed, and the question of trading it
  for a live region now has its recording.

  **A review caught the chip saying less than it showed, and the shape of the
  miss is worth keeping.** A node carrying a `contentDescription` has its `text`
  ignored by a screen reader. The chip has both — a short drawn label, a spoken
  sentence — and the first build described only the mood, so the count was drawn
  and never announced. Every test passed, because they all read the label back,
  and the label is exactly the part TalkBack does not read. The description is
  now built from the panel's own mood line and count, and
  `chip_speaksTheCountItShows` pins it. The general form, worth carrying past
  this chip: where a node's shown and spoken strings differ on purpose,
  asserting the shown one proves nothing about the other.

  **The premise was half right, and a device said which half (2026-09-02).**
  TalkBack 17 on the Nothing A059 read the chip as *"Momo is pottering about.
  3 of 14 left today. 3 left"* — the description **and then** the drawn label.
  So a described node's text is not ignored; it is read after the description,
  and the chip now says its count twice in two forms. The same shape leaks on
  the retro strip, the history grid and the trend columns (docs/running.md §4's
  accessibility block has each recording). The fix is one modifier,
  `clearAndSetSemantics` in place of `semantics` where the description is set —
  **made 2026-09-02** here, on the grid and the columns, and on the retro strip
  with the `combinedClickable` kept ahead of it so its role and toggle state
  survive (habits.md). `chip_speaksTheCountItShows` passes either side of it,
  so `chip_doesNotAlsoReadItsLabel` is the pin: the chip's node carries no
  text, and the label exists only in the unmerged tree. Hearing it again is
  owed (docs/running.md §4). The rule above stands and gains a clause:
  asserting the *spoken* string proves the description is complete, not that it
  is all that is spoken.

  **The chip carries the milestone line; the announcing is what is left.** The
  panel swaps that line in for the length of a celebration and
  [momo.md](momo.md) §6 makes the swap the announcement. It did not reach the
  chip at first because the milestone lived on `TodayMotion`, which `HabitList`
  owned one level below the app bar, so a rung crossed while scrolled down was
  neither drawn nor spoken — a gap older than the chip, since the panel was
  already disposed by then and nothing was drawn there either.

  `rememberTodayMotion` now sits above the `Scaffold` rather than above the
  `LazyColumn`, which is the distinction that mattered: the bar is the list's
  *sibling*, so above the list would still have been out of its reach. The chip
  takes the milestone line **in place of its count** for the run — the same
  swap the panel makes, and for the same reason the mood line never sat beside
  the count here, which is that the bar has room for one string. Its
  description takes the line in the *mood line's* place instead and keeps the
  count, so what is spoken never narrows to less than the panel would have
  said. `chip_carriesTheMilestoneLine` and `chip_speaksTheMilestoneLine` are
  separate tests on purpose: the paragraph above is exactly why asserting the
  drawn string cannot cover the spoken one, and each fails on a mutation the
  other survives.

  **The drawn line is a shorter string than the spoken one, and that is a
  device's finding rather than a design preference.** The first build reused the
  panel's sentence, and on the bar it truncated to "7 days in a row. Mom…" at
  font scale 1.0 — not at 200 %, where this section had expected the pressure.
  So the chip has its own plural, the way it already has its own count string:
  "7 days!" drawn, the panel's full sentence spoken. Worth keeping because of
  *how* it escaped: a Compose assertion that a string is present passes on a
  node drawing it clipped, so no JVM test at any font scale could have seen it,
  and the JVM tests were green throughout. The general form, beside §6's other
  shown-versus-spoken lesson: a test can prove a string is *there* and say
  nothing about whether it is legible. [running.md](../running.md) §4 carries
  the measurement.

  The complication recorded here — that the `Empty` branch built its own motion
  with different arguments and `Loading` and `Unavailable` built none — was
  settled by making the mood **nullable** rather than by giving the states
  without one a stand-in. That is load-bearing, not tidiness: one motion now
  outlives the change from one branch to another, and `celebrates` fires only
  against a non-null `previous`, so a stand-in mood during `Loading` would turn
  the first real thriving into a party for a day that was already over when the
  app opened. `CelebrationGuardTest` pins it, with the animations gate passed
  `true` as a parameter — under [running.md](../running.md) §4's animations-off
  rule a celebration cannot be observed at all, so that test lives outside
  `TodayScreenTest` and carries a deliberate control.

  **The chip carries the milestone line in its description and does not
  announce the change.** A description change on a non-live node is not
  announced, so this is the live-region question above rather than a second
  gap — and that question has had its recording since 2026-09-02, so what is
  open is the decision rather than a measurement.
- **The `regenerating` copy has somewhere to come from.** §3 asks it to name
  the habit and offer the repair, which needed a second pure function beside
  `Mascot.mood` — `recentlyBrokenHabits(inputs)`, returning ids rather than a
  wider `Mood`, because one type should not carry both "which drawing" and
  "which habit". That is the shape built, and `HabitMoodState` gained the one
  field it needed.

  What that shape did not settle was **which** habit, once more than
  one sits inside the window. The line names one, so the ordering *is* the
  copy: most recently broken first, ties keeping the user's own habit
  order. Most recent rather than the largest lost streak, because naming
  the biggest loss is a way of scolding, and §3 says this state must never
  do that. The mood still gates it — `thriving` outranks `regenerating`,
  so a finished day holds a live break with nothing to say about it.
- **Momo's own copy is settled, and it is the copy that is already there.**
  Every line was written to make the Phase 0 states distinguishable rather
  than to be read, and the drafting round for 1.0.0 (PRD §5, step 3) set
  alternatives beside each of them and kept the originals. The rule those
  lines had already found is why: **the mood line says how she is, and the
  count line directly beneath it says how the day is.** A mood line that also
  counts is the panel saying one thing twice, which is what every drafted
  alternative that reached for more warmth ended up doing. The regenerating
  line is the model rather than the exception — it names the habit, offers the
  repair and blames nobody — and the other three are now held to it as
  written.
- **`regenerating` is visible**, with its own face and its own line
  ([momo.md](momo.md) §3), and the line names the habit. The unnamed line is
  kept for the state it belongs to. **A habit already ticked today is never
  named** —
  the line offers a repair, and there is nothing to repair today. Only a weekly
  habit can be both ticked and broken: a completion short of the week's target
  leaves the streak at zero with `brokenOn` set, and without the rule the line
  read "pick X back up" directly above X's own ticked row while the habit actually
  left undone went unmentioned. The mood is untouched by this — the streak really
  is broken and §4's table is unchanged — so a regenerating face with no habit to
  name is exactly when the unnamed line shows.
