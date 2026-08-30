# Insights v1: history, trends and where the effort goes

Companion to [the PRD](../prd.md) §5 Phase 1 and §8's OQ-1, and to
[the architecture](../architecture.md) §2. The PRD asks for three things —
*"per-habit heatmap/calendar history, completion-rate trends"* and
*"tag-based effort distribution: share of completions per tag over a selected
period"* — and leaves every visual and definitional choice open. This document
is where those get decided.

**Status: written as a sketch on 2026-08-23; all three of its surfaces were built
on 2026-08-24.** So this file has stopped being a sketch and now records what was
decided by building, like [habits.md](habits.md), [widget.md](widget.md),
[settings.md](settings.md) and [reminder.md](reminder.md). What the sketch was
for was stopping three questions being answered by accident: which module this
lives in, what a cell in a heatmap is allowed to mean, and what happens to the
tag metric when OQ-1 lands. All three are now settled by code, and §8 is the
record of what building them decided — including the four places the reasoning
here, and the artboard it came from, turned out to be wrong.

~~What is *not* built is PRD §5's Phase 1.5, this module's second job.~~ **Built
2026-08-29**, and it grew out of the app-wide screen (§8.8) exactly as this
paragraph predicted rather than becoming a screen of its own: a stepper walks
the period back through the calendar, and three facts joined the numbers it
already drew. §9 is the record. What that paragraph called "the first line of
it" — adherence per habit and per tag across a period — was already there, which
is why the retrospective cost a stepper and three facts rather than a module.

`:feature:insights` **exists as of 2026-08-24**, and it arrived the way this
file said it had to: with its first real file. `insights` was already in
`scope-enum` in `.commitlintrc.yaml`, added 2026-08-23 ahead of the module rather
than with it, because the `commit-msg` hook rejects a module's *first* commit if
its scope is missing — so the entry had to exist before the module did.

The module was deliberately **not** created empty, and that is worth keeping
because it was tried and reverted. An Android library always has test sources
configured for its unit-test variant, so Gradle 9 fails
`:feature:insights:testDebugUnitTest` with "there are test sources present …
but the test task did not discover any tests" the moment the module is included
with nothing in it. The available workarounds were setting
`failOnNoDiscoveredTests = false`, which would mask a real misconfiguration
later, or inventing a placeholder test, which asserts nothing. Neither was worth
it, and neither was needed: **the module was created together with its first
real file**, `HistoryScreen.kt` and the three test classes that cover it.

Two pieces of shared presentation moved to `:core:ui` on the way, both because
this screen would otherwise have been the third copy — AGENTS.md's rule, and the
habit icon badge's scar. `GlyphButton` was written identically in
`:feature:habits` and `:feature:settings`; the seven weekday labels existed as
letters in `:feature:habits` and as spelled-out names in `:feature:settings`.
Both now live in `:core:ui`, which also gained its first `res/` directory in the
process — the one [visual-identity.md](visual-identity.md) §5 expects to hold the
bundled font.

The two layers underneath were built on 2026-08-23 — §6's owed tag aggregate
query, and §4's completion-rate denominator. Both were unblocked while the
screens were not, for the reason PRD §5 records: they have no colour in them.

---

## 1. Its own module, not a corner of `:feature:habits`

**Decision: `:feature:insights`.** Architecture §2's table now carries the row.

The obvious counter-argument is that the heatmap is *per habit* and reached from
habit detail, so it belongs beside it. That argument dies on this repo's own
navigation rule (architecture §2): `:app` owns navigation and a feature module
exposes Route composables taking plain lambdas. "See full history" on habit
detail is therefore a lambda, and `:app` decides it lands in a different
module's Route. Being reached from habit detail costs a cross-module dependency
of exactly zero.

What actually decides it is the third surface. **Tag effort distribution is not
per-habit at all** — it is one number per tag across every habit — so it has no
home in `:feature:habits` under any reading. Splitting the two would put them in
modules that cannot see each other, and any shared piece (a colour scale, a
period picker, a chart axis) would have to be promoted to `:core:ui` on its
first reuse. One module for all three keeps that from happening.

The future load-bears too, and it is committed rather than speculative: PRD §5's
**Phase 1.5 retrospectives** — quarterly and yearly review screens — are this
module's second job, not a new one.

## 2. What the PRD asks for, in build order

1. **Per-habit heatmap / calendar history.** The cheapest and the one with no
   open question in front of it. PRD §5's readiness order puts it first.
   **Built 2026-08-24** — one calendar month at a time, stepped by two arrows,
   reached from habit detail's "see full history". §8 below is what building it
   settled and what it cost.
2. **Completion-rate trends.** Needs §4's definition settled before it can be a
   number at all. **Built 2026-08-24** — five trailing months under the month
   grid, per habit, plus a row per habit on the app-wide screen. §8.7 and §8.8.
3. **Tag effort distribution.** Needs a new aggregate query and carries §5's
   problem. **Built 2026-08-24** as one breakdown of the app-wide screen (§8.8);
   the query landed on 2026-08-23.

## 3. The heatmap is read-only

Habit detail's five-cell strip is the **writable** window: `today−3..today` open,
`today−4` drawn shut, sized against `Commands.RETRO_WINDOW_DAYS` so the screen
and the domain cannot drift ([habits.md](habits.md) §7). A month grid is the
other thing — history, not editing — and it stays read-only for a reason that is
not taste: **the domain rejects a completion write outside the retro window**, so
a writable month grid would draw twenty-odd cells that look identical to the
four that work and refuse the rest. `Commands` would hold the line, and the
screen would be lying.

This is also what [habits.md](habits.md) §8 already decided by refusing to grow
the strip into a month view. Same decision, recorded from the other side.

**Shipped that way**, and pinned rather than merely intended: `HistoryScreenTest`
asserts that a cell carries no click handler at all. A read-only screen is easy
to make writable by accident — a `clickable` added for a ripple would do it —
and the test is what makes that a red build instead of a cell that answers a tap
and then refuses.

## 4. A weekly habit has no scheduled days. This shapes everything

`Schedule.Weekly(timesPerWeek)` is *"n times per week on any days"*
(`core/domain/.../model/Schedule.kt`). There is no set of days a weekly habit
was *supposed* to be done on. Two consequences, and neither is cosmetic:

- **A heatmap cell cannot be coloured "missed" for a weekly habit.** It can say
  completed or not-completed, and not-completed is not a miss — the week is the
  unit that can be missed, not the day. A three-state day cell (done / missed /
  not scheduled) is honest for `Daily` and dishonest for `Weekly`.
  **Settled 2026-08-23: two-state days for every schedule.** The alternative — a
  grid of weeks for weekly habits — is marginally more honest and is a second
  layout to build, test and explain, and the honesty it buys is already bought
  by the cells simply not claiming a miss. One grid, two states, both schedules.
- **"Completion rate" is two different fractions.** Daily is completions over
  days elapsed. Weekly is completions over `timesPerWeek × weeks elapsed`. One
  percentage label over both is comparing unlike things, and a single query that
  returns "rate" without the schedule beside it will get used as though it were
  comparable. The denominator has to come from `:core:domain`'s schedule rules,
  not from counting rows.

  **Built 2026-08-23** as `Rates.completionRate` in `:core:domain`, beside
  `Streaks`. Three decisions were forced by writing it, none of them visible from
  the requirement:

  - It returns `CompletionRate.Daily` or `CompletionRate.Weekly`, never a bare
    `Double`. The schedule travels with the number so a caller has to look at it
    before rendering a percentage; that is the whole defence against the two
    fractions being compared.
  - **Only finished units count.** An unfinished day, or a week still below
    target, is not a miss — the same liveness rule `Streaks` follows. It matters
    more here: a rate that charged the current week in full would read as a
    collapse every Monday morning. Whole weeks only at *both* ends, so a period
    starting mid-week does not get billed for a week it only partly contains.
  - `fraction` is **null**, not `0.0`, when nothing in the window has finished.
    A habit created this morning has not failed anything, and `0.0` renders as
    "0%" — the screen accusing the user on no evidence. Callers draw a dash.

  ~~One limitation is the screen's to solve and is recorded rather than hidden:
  nothing in the projection stores when a habit was created — `HabitState` has no
  such field — so a window reaching back before the habit existed yields a rate
  that is arithmetically right and meaningless. The earliest completed date is
  the available proxy, and whether to clamp to it is a presentation decision.~~

  **Fixed 2026-08-24, and not with the proxy.** `HabitState.createdOn` is
  projected from the `HabitCreated` event's own envelope, so both rate surfaces
  clip their window to it and a habit younger than the period draws a dash
  instead of a low number. The proxy this paragraph offered — the earliest
  completed date — was rejected on the way: it biases every rate *upward*,
  because a window that begins at the first completion always begins on a day
  the habit succeeded, and a habit created and then ignored for two weeks loses
  those two weeks silently.

  It cost the first schema migration in the repo, and it was the cheap kind:
  `GawiDatabase`'s KDoc had already scoped a derived-table change as bump the
  version, drop or extend the table, bump the projection version, replay. The
  event log is untouched, so no payload changed and nothing upcasts. §8.7 has
  the rest.

Recorded here because both mistakes are cheap to make and invisible once made —
a heatmap full of grey cells for a 3-per-week habit looks like a user with a
problem rather than a screen with one.

## 5. Tag effort distribution, and the 100% that stops adding up

Single tag today: `HabitMetadata.tag` is one nullable field, in the domain and
in the wire format. So the v1 metric is well-defined — each completion belongs
to at most one tag, shares sum to 100% once untagged habits are accounted for,
and "untagged" has to be a visible slice rather than a silent omission or the
percentages lie.

**OQ-1 was settled on 2026-08-23: multi-tag is committed, unscheduled** (PRD
§8). When it lands, a completion can belong to several tags and the shares stop
summing to 100% unless attribution is chosen — fractional (a completion with
three tags contributes a third to each) or full (it counts once to each, and the
total exceeds the whole). That choice belongs to the schema bump, not to this
screen. What this screen owes it is not to be *shaped* as though one tag were
permanent: the query returns per-tag totals and the screen computes shares from
them, rather than the query returning percentages that would silently become
wrong.

## 6. What the data layer already gives, and what it owes

Already there: **`HabitRepository.observeCompletedDates(habitId, from, to)`**
returns `Flow<Map<LocalDate, String?>>` — completed logical dates in an
arbitrary range mapped to the note showing on each
(`core/data/.../repository/HabitRepository.kt`, DAO query in `ReadModelDao`,
covered by `TodayQueryTest`). This is the whole read the heatmap needs. It was
built for habit detail's five cells and takes a range because ranges were
cheaper than a special case, which is why §1's first surface is unblocked.

Was owed, **built 2026-08-23**: the **tag aggregate query**, the one piece of
Insights v1 that is not a read of something already served. It lives in
`:core:data` beside the other projections — `ReadModelDao.observeTagEffort`,
surfaced as `HabitRepository.observeTagEffort(from, to)` returning
`Flow<List<TagEffort>>`, covered by `TagEffortQueryTest`.

It returns per-tag **totals**, never shares, for §5's reason. Two further
decisions came out of writing it:

- **Archived habits count.** Effort spent is history, and archiving is a decision
  about the future; hiding an archived habit's past completions would make a
  period's distribution change retroactively every time something was tidied
  away. This is deliberately the opposite of `observeToday`, which is about what
  to do now.
- **A completion whose `HabitCreated` has not arrived is excluded**, because it
  has no known tag and folding it into the untagged slice would invent data and
  make that slice mean two things. Unreachable before Phase 2 sync — locally a
  completion cannot precede its habit — and pinned by a test so it stays a
  decision rather than an accident.
- **`Health`, `health` and `health ` are one slice, not three.** Tags are
  unnormalized free text: the editor stores `tag.ifBlank { null }` with no trim
  and no case fold, and `Commands` validates only the name. SQLite groups on
  BINARY by default, so the obvious query splits one human tag into three
  slices that each understate its share — and, because the ordering is
  `COLLATE NOCASE`, drops them side by side where the split is most visible.
  The query groups on a trimmed, case-insensitive key and picks its label with
  `MIN` so it is deterministic. **This is a patch over a gap, not the fix:**
  what a tag *is* — whether two spellings are one thing — is a domain question,
  and answering it on write is the real answer. It is worth settling before the
  multi-tag schema bump rather than after, since that bump is where tags stop
  being one nullable string.
- **Re-tagging a habit re-attributes its whole history.** The query joins on
  current metadata, so re-tagging "run" from `health` to `fitness` makes last
  January's distribution say `fitness`. The log could answer otherwise —
  `HabitUpdated` carries the old value — but the read model does not keep it,
  and the reading taken is that a tag describes the habit rather than the
  completion. Recorded because it is a narrower guarantee than the archiving
  decision above sounds: archiving cannot *remove* effort, which is not the
  same as saying no edit can move it.

Not owed but worth stating: the heatmap needs **no new domain logic**. Trends do
— §4's denominator — and that lands in `:core:domain` where the schedule rules
already live, never in the feature module.

## 7. Still open

- ~~**Weekly habits' grid**: two-state days for everyone, or a grid of weeks for
  weekly habits (§4).~~ **Settled 2026-08-23: two-state days for everyone.** §4
  carries the reasoning.
- ~~**The period picker.** "Over a selected period" (PRD §5) does not say which
  periods.~~ **Settled 2026-08-24: a fixed set of three — Month, Quarter,
  Year — and calendar periods rather than trailing windows.** The labels say so,
  and the reason this was worth deciding once is Phase 1.5's *quarterly and
  yearly* review screens: a trailing thirty days cannot serve a quarterly
  retrospective, so a trailing window would have meant deciding it twice after
  all. Known consequence, recorded rather than hidden: on the 1st of a month,
  Month holds almost nothing and Quarter is the answer.

  **The heatmap deliberately did not answer this**, and that held. Its two month
  arrows are not a picker and were not a down-payment on one: a month grid can
  only show a month, so "which month" is the whole of its range. The picker lives
  on the app-wide screen and governs that screen alone — the grid keeps its
  arrows and the rate trend keeps its fixed five months, because a trend needs
  several buckets and so "which period" is the wrong question to ask of it.
  Three surfaces, three different time questions, one control each.
- ~~**The colour scale**, and specifically whether intensity encodes anything at
  all.~~ **Settled 2026-08-24**, and §8 has the values and the measurements. The
  question was never hard once the palette existed — completions are idempotent
  per logical date (architecture §4), so there is nothing to count and a
  count-per-day scale has no input. Two colours, `primary` for done as this
  paragraph predicted, and the constraint it named held: every colour comes from
  a `MaterialTheme` role rather than a literal, so the palette reaches this grid
  the way it reaches every other screen. What the paragraph did not anticipate is
  that the *pair* would need measuring rather than the roles individually, and
  that the obvious way to mark today would fail that measurement.
- **Where it is reached from.** ~~Habit detail for the per-habit grid is the
  obvious door~~ — **taken, 2026-08-24**: a "See full history" text button under
  habit detail's retro strip, reported as a lambda, routed by `:app` to
  `Destination.HabitHistory`. Architecture §2 used this door as its own worked
  example of why the heatmap can live outside `:feature:habits`, and building it
  cost the cross-module dependency that section predicted: none. **The tag
  distribution still has no door**, and inventing a top-level destination is a
  navigation decision that belongs to `:app`.

  **Closed 2026-08-24.** `Destination.Insights` is that top-level destination,
  reached from a third action in Today's app bar — the app's only top-level
  surface, so it was that or nowhere. What made it the right shape rather than a
  convenience is that the screen turned out to want more than the tag bars: the
  question it answers is "how am I doing overall", which nothing in the app
  answered, and the tag distribution is one breakdown of it. §8.8 has the screen.
- **Whether Momo appears here.** PRD §5 puts him in the Today view, the widget
  and the reminder. This screen is not on that list and should probably stay off
  it until OQ-4 is settled.
- **Export of a review as an image or PDF.** PRD §5's Phase 1.5 nice-to-have,
  and the one part of that phase **not built on 2026-08-29** — recorded here as a
  later feature rather than dropped. It needs a bitmap capture of the review
  column and a share sheet, neither of which exists in the app yet, and nothing
  in §9 is shaped to prevent it: the column is one `Column` with no scroll state
  of its own to fight.
- **A per-tag trend** — a tag's active days per month, beside §9's app-wide one.
  Waits for OQ-1: a tag's monthly line is exactly the figure fractional
  attribution would redefine, so drawing it before the multi-tag decision would
  be drawing it twice.

## 8. What building it settled

Written 2026-08-24, after the screens. Everything above §7 was reasoning; this is
what the code decided, including the several things the reasoning got wrong.

§§8.1–8.6 are the heatmap. §§8.7–8.9 are the two surfaces that followed it the
same week — the completion-rate trend and the app-wide screen.

### 8.1 The two grounds, and why the *pair* is what gets measured

**`primary` for a completed day, `surfaceContainerHighest` for a finished day
that was not**, with the day number on top in `onPrimary` and `onSurfaceVariant`
respectively. So a cell carries its state twice — in the fill and in the colour
of the number — and it is a calendar rather than a bare heatmap, which is what
PRD §5's "heatmap/**calendar** history" asks for anyway.

`GawiColorSchemeTest` gained one pairing for this, and it is a new *kind* of
pairing: two fills against each other rather than content against a fill.

| Pair | Light | Dark | Floor |
|---|---|---|---|
| `primary` vs `surfaceContainerHighest` | 4.41 | 6.94 | 3.0 — WCAG 1.4.11 |
| `onPrimary` on `primary` | *already held* | | 4.5 |
| `onSurfaceVariant` on `surfaceContainerHighest` | *already held* | | 4.5 |

That file's KDoc had declined to hold a fill against the page, and still does:
`surfaceContainerHighest` against `surface` is **1.26 light / 1.50 dark** and is
meant to be. A month of quiet cells is a calendar; a month of 3:1 cells is a
keypad. What makes an unfilled cell readable against the page is the number in
it, not the fill.

### 8.2 Today is a ring, because the obvious answer measured 1.04

`RetroStrip` marks today by filling its cell with `secondaryContainer`. Copying
that here does not work, and the number says how badly: **`secondaryContainer`
against `surfaceContainerHighest` is 1.04 in light and 1.05 in dark.** A not-done
today would have been indistinguishable from every other not-done day — the same
failure [visual-identity.md](visual-identity.md) §3's published `tertiary` had at
1.02, and the same reason it was replaced.

So today keeps its state's own ground and takes a 1dp ring instead: `primary`
when the day is not done, `onPrimary` when it is. Both are pairs already proven
against the ground they land on, which is the point — the marker reuses a
measurement rather than adding one.

Worth stating because the two screens now mark today *differently* and that is
not drift. The strip has five wide cells and today is the one you can tap; the
grid has thirty-one small ones and none of them are tappable.

### 8.3 A day that has not happened draws nothing

Not a quiet cell — nothing at all. `Rates` refuses to count an unfinished unit
(§4), and this is the same rule in pixels: a grid that drew the rest of the month
as not-done would read as a month already half lost, every month, from the 1st.

A consequence worth knowing before it looks like a bug: the last week row of the
current month can be entirely future, and it then collapses to no height. That is
what should happen to a week that has not started.

### 8.4 The columns are hidden from a screen reader, and the cells pay for it

The seven column letters are `M T W T F S S`, and read aloud that is noise: `T`
and `S` each name two days, so a header cannot identify a column spoken. The row
carries `clearAndSetSemantics` — the only place in this app that hides content
from assistive technology.

**What replaces it is more than it removes.** Every cell announces its own
weekday spelled out: *"Friday 14, done"*. A sighted reader gets the weekday from
the column position; a screen-reader user now gets it from the cell, which is
strictly better than seven ambiguous stops ahead of the grid. "I keep missing
Sundays" is a thing this grid should be able to tell someone who cannot see it,
and now can.

This is what moved the spelled-out weekday names into `:core:ui`. They were
`:feature:settings`' — the week-start picker's options — and this screen needed
the same seven.

### 8.5 The month is an offset, not a date

The ViewModel holds "months from the month containing today", clamped at zero,
rather than a `YearMonth`. `observeHabitDetail` re-emits when the day rolls over,
so zero keeps meaning "this month" across a month boundary with nothing on the
screen's side holding a clock, a zone or the day cutoff — architecture §5's rule,
which a stored absolute month would have quietly broken on the one night it
matters.

Stepping forward past this month is disabled rather than merely hidden: the
stepper is not drawn at zero *and* the clamp is in the state holder, because a
screen holding a rule that is not the screen's is how the rule gets lost.

### 8.6 What it cost in `:core:ui`, and what that unblocks

`:core:ui` now has a `res/` directory. It had none, which
[visual-identity.md](visual-identity.md) §5 noted while committing to bundle a
variable font there — so the directory the font needs already exists, and
whichever typeface wins the experiment lands in a module that already ships
resources.

`GlyphButton` moved there too, from two identical copies. Nothing about the
heatmap needed that beyond not being the third one.

### 8.7 The completion-rate trend, and the artboard's two wrong captions

The redesign canvas artboard **"The screen the palette was blocking"** is what
prompted the rest of this. It draws four cards — the heatmap, a rate sparkline,
tag-effort bars, and a Month/Quarter/Year picker marked *"a proposal, not a
decision"*. It was recorded in neither this file nor
[visual-identity.md](visual-identity.md), so by §7's own rule it was a drawing.
It is a decision now, and building it corrected it twice.

**The current month draws a real number, not the artboard's dash.** Its caption
justified the dash by saying `Rates.completionRate` returns null for a
part-month. It does not. It excludes unfinished units from *both* sides of the
fraction, so a month three weeks in is 17 of 17 rather than 17 of 31 — already
comparable to a finished month, which is the whole point of the liveness rule the
caption was citing. Withholding that number would be withholding one the
calculator went to some trouble to make safe. A dash now means only what
`fraction`'s null means: nothing in the window had finished.

**Five months, oldest first, ending on this one**, and the y scale is fixed at
0–100% rather than scaled to the data. Five points spanning 71–90% auto-scaled
would fill the plot and read as a collapse and a recovery; against a fixed scale
they read as what they are, four flat months.

**A null point breaks the line rather than being skipped over.** Joining the
months either side of a gap draws a segment through a month that has no value.

**The card carries the schedule**, because §4 forbids reading a daily habit's
percentages as a weekly habit's and nothing else on the card distinguishes them.

**The plot is omitted entirely when every point is a dash** — found on a device,
where a reserved-but-empty 56dp reads as a chart that failed to draw rather than
as a chart with nothing in it. The labelled dashes already said it.

**The trend is not governed by the grid's steppers**, and the two reads are
deliberately separate: the month offset is collected around the grid's query
alone, so stepping a month does not re-read five months of rows. Pinned by a test
that counts how many times the trend window is asked for.

### 8.8 The screen that reports on everything

Raised while reviewing the heatmap, and correct: every surface in the app was
about one habit or one day. `Destination.Insights` is the answer — one period,
two numbers about it, and one breakdown with a **Habits ⇄ Tags** toggle. One
list with a toggle rather than two lists, so the screen reads as one view of a
period rather than two screens sharing a title.

**The headline is active days and completions.** Both are exact and neither needs
a denominator, which is why it is not the *perfect days* count that was asked
for first. Whether **every** habit was done on a past day is not answerable: a
weekly habit has no due day at all (§4), and the honest definitions available all
required either inventing due days for weekly habits or guessing which habits
existed then. "You turned up on 18 days" claims nothing it cannot support.

**A row per habit, never an average.** An app-wide completion rate would add the
two fractions §4 exists to keep apart, so each row carries its own denominator
and its own schedule label. Ordered by the habit list's order rather than by
rate — a list that re-sorted itself as the numbers moved would make the same
habit hard to find twice running.

**Archived habits are out of the adherence list and still counted in the tag
totals.** That looks inconsistent and is the asymmetry the data layer already
draws: `observeTagEffort` counts them because effort spent does not stop having
happened, and `observeToday` hides them because archiving is a decision about the
future. Adherence is a question in the present tense. The visible consequence,
seen on a device: an archived habit's completion is in the headline and in the
tag bars but has no row under Habits, and Tags is where it is accounted for.

**Every bar is `primary`, including untagged.** The artboard drew that one grey.
Measured against `primary`, every candidate for "a quieter bar" is
indistinguishable — `outline` 1.07 light / 1.97 dark, `secondary` 1.07 / 1.13,
`tertiary` 1.32 / 1.42, `onSurfaceVariant` 1.37 / 1.25. Same class of defect as
§8.2's 1.04. So the distinction moved to channels that survive a greyscale
reading: the label is drawn in `onSurfaceVariant` where a tag's is `onSurface`,
and untagged sorts **last regardless of size**, because it is the residual rather
than a competitor.

**The number is a total and no percentage is drawn** — narrower than PRD §5's
word "share", deliberately. A total cannot become wrong the day OQ-1's multi-tag
change lets a completion carry two tags; a percentage has to be redefined by it
(§5). Bars scale to the largest total rather than to the sum, so they compare
rather than sum: a bar whose length were its share of the whole would leave the
longest one a third of the track on any realistic spread.

**No new `GawiColorSchemeTest` pairing was needed**, which is worth saying so
nobody adds one for completeness. The bar on its track is `primary` against
`surfaceContainerHighest` — the pair §8.1 added — and everything else on the
screen is text on `surface`. Verified on a device by sampling pixels in both
themes: fill `#1F6F78` / `#7FD4DC`, track `#D3E3E6` / `#2C3A3D`.

### 8.9 What the creation date bought, and what it cost

§4's recorded limitation — nothing knew when a habit began — was load-bearing for
both rate surfaces, and it is now `HabitState.createdOn`, projected from the
`HabitCreated` event's own envelope. A window is clipped to it, so a habit made
three weeks into a quarter is measured over three weeks rather than reading as
though it had missed the first nine.

Seen on a device, which is the clearest way to put it: a habit created today
reads **—** across all five months of its trend. Without the clip it would have
read about 13% — a number that is arithmetically right and accuses the user of
twenty days that were never offered.

It cost the first schema migration in the repo, and that migration is worth
knowing about because it is the pattern for the next one. `GawiDatabase`'s KDoc
had already scoped it: a derived-table change bumps `DATABASE_VERSION`, extends
or drops the table, bumps `PROJECTION_VERSION`, and the next start replays the
log to fill the new column. The event log is never rewritten, so no payload
changed and nothing upcasts. Verified by upgrading a real v1 database on an
emulator: 57 events and both habits survived, `created_on` came back populated
with the dates those habits were actually made, and both versions moved to 2.

One invariant was rewritten to do it. `Event`'s KDoc said `tzOffsetMin` was "kept
for audit only — projection never reads it", and projection now reads it in
exactly one place. Paired with `occurredAt` it gives the calendar date the habit
was created on, in the offset it was written at. Both fields are stored at
command time and neither can change, so the derivation is a pure function of
immutable log data and every replay yields the same date — which is the property
architecture §5 protects. It is deliberately **not** the logical date: the cutoff
as it was then is not recorded anywhere, so this errs one day later at most, in
the direction that cannot manufacture a miss.

## 9. Phase 1.5 — the review, and what building it settled

Written 2026-08-29, after the build. PRD §5's Phase 1.5 asked for *"quarterly /
yearly review screens: adherence per habit and per tag across the period, trend
lines, best/worst streaks, 'focus shifted from X to Y' summaries"*, and its
nice-to-have export. The redesign canvas's page 11 drew it first — a past
quarter, the twelve-column year, and a decisions board — and the code is a
transcription of those boards.

### 9.1 Grow, don't fork

**One screen.** A separate Review destination would have redrawn the period
chips, the headline and the adherence list to put three facts under them. §1
already called retrospectives this module's second job and §7 chose calendar
periods so that the picker could be shared; this is that decision paying out.
The retrospective is the Insights overview one period back.

### 9.2 Stepping is an offset

A `◀ Q2 2026 ▶` row sits under the chips. The ViewModel holds *how many periods
back from today's*, clamped at zero, beside the period — never a stored date —
which is §8.5's rule for the grid's month, for the same reason: the window is
recomputed from `observeReadContext`'s today on every emission, so zero keeps
meaning "now" across a day rollover with nothing on the screen's side holding a
clock. `Period.window(today, back)` is the one piece of date arithmetic, and
"the period before" — which the focus sentence needs — is the same function
asked once more, not a second calculation that could drift from the first.

The month's title reuses the grid's `insights_month_title` rather than a second
identical template: one string, two steppers. The two stepper *rows* stay
separate on purpose — the grid hides its later arrow at zero and leaves a
footprint, this one disables it — and merging them would mean a policy
parameter for a two-line difference.

**Picking a different period resets the offset.** "Three quarters back" has no
meaning in years, and carrying the number across would land the user on 2023
without having asked for it.

**The later arrow is disabled at the current period, not removed.** The grid
hides its stepper at zero and leaves a footprint; here the label sits between
the two arrows and a vanishing one would shift it. Disabled at Material's alpha,
content description kept — and the clamp is in the ViewModel too, so a tap that
got through would still do nothing. `InsightsScreenTest` pins the disabled
state and `InsightsViewModelTest` pins the clamp.

**The earlier arrow has a floor too**, raised by the PR review: the first cut
walked back without end, each tap re-subscribing three flows to draw a period
older than every habit. It now disables once the period starts at or before the
oldest habit's creation — `HabitState.createdOn`, §8.9's field, already in the
mapper's hands — and with no habit at all. A habit whose creation date is unknown
(projected before that field existed) keeps the arrow live, because unknown is
not "nothing before here". The ViewModel guards on the state it has shown, the
same way it clamps the other direction.

### 9.3 Best, in the period, in the unit — and no worst

`BestRun.within(dates, schedule, window, today, weekStart)` in `:core:domain`,
beside `Streaks`: the longest consecutive run of finished units inside the
window, in the schedule's own unit. Not `Streaks.snapshot` — that answers "how
long is the run ending now", and a review of last quarter wants the longest run
*that quarter*, which the current streak says nothing about once it has broken.

Two clips, both deliberate. A run that began before the period counts only the
part inside it — a 40-day run that ended on the quarter's second day was not a
40-day quarter. And for a weekly habit only weeks the window holds **wholly**
are judged, the line `Rates.completionRate` already draws: three of a week's
days in the period and four out means a miss there would be the window's doing.
The current week is not excluded for being unfinished; if it has already hit
its target it is a hit week, as `Streaks` holds. Measured over the same
creation-clipped window as the rate (§8.9), so the two figures on a row describe
one span.

It rides on the schedule line — *"3× a week · best 9 weeks"* — because the unit
it is counted in is the schedule's, carried as `StreakUi.Days` or `.Weeks` so the
row cannot pick the wrong plural, and null rather than zero when the period held
no run: "best 0 days" under a row is the screen accusing the user.

`StreakUi` carries two states the row can never hold — `None` and `Broken` are
Today's — and is reused all the same, because its job here is the unit split and
a third streak type differing only by lacking two states would be the drift the
shared one exists to stop. `bestText` names the dead branch.

**A best run can stand beside a dash**, and the first device look showed exactly
that: a habit created and completed today reads *"Every day · best 1 day —"*.
Not a contradiction. A run counts today when today is done, as the streak on
Today does, while a rate counts only finished units (§4), so the two rules meet
on that row and both are right. The review that raised it proposed clipping the
run at yesterday to match the rate; that would have made the best run on
Insights read one less than the streak on Today for the same habit and the same
dates, which is the worse disagreement. Pinned by a `BestRunTest` case.

**The birth week of a weekly habit is judged partial**, because the window handed
to `BestRun` is already clipped to the habit's creation (§8.9) and the whole-week
rule is applied to the window as handed in. A habit created on a Wednesday that
hit its target that same week has that week discarded from both its rate and its
best run. `Rates.completionRate` has always done this, so the two figures on the
row agree, and it is recorded here as a known narrowing rather than fixed: the
fix belongs to both calculators at once and to §4's definition of a countable
week, not to the retrospective.

**No worst.** Every habit's worst run is zero. A column of zeros carries nothing,
so the PRD's "best/worst" is built as "best", and this is where that is decided.

The hit-week rule is `Streaks.hitWeeks`, now `internal` and called from
`BestRun` rather than copied into it — "which weeks count" is written once in
`:core:domain`, so the weekly best run on Insights and the weekly streak on Today
cannot come to disagree about the same dates. The walk itself is linear: a run
is walked from its head only, the date whose predecessor is absent.

### 9.4 The trend measures turning up

**Active days per month**, oldest first, for a quarter or a year — the
headline's own number, bucketed. Not a per-habit rate trend over the period:
that would be one line per habit or the averaged number §4 exists to refuse.
Active days needs no schedule, and its denominator — the calendar days each
month has had — is the one honest for a daily and a weekly habit at once. The
subtitle names it, as the rate card names its schedule, because a count out of
days is not a completion rate and must not be read as one.

**A month that has not begun is not a point** — not a zero, not a gap. §8.3's
rule for future days, in months: a year drawn with four zeros at its end would
read as a year already lost. The current month is a real point over the days it
has had so far — its own count over its own elapsed days, which on the 1st is
one day over one day and reads as such; this is a count of turning up, not
§8.7's rate, and it does not borrow that rule.

**One point is not a line, whatever the period.** The first cut keyed this on
Month and the review caught what that missed: a quarter viewed in its first month
and a year in January also have one begun month, and would have drawn a lone
dot. The rule is now the count of points — fewer than two and there is no trend —
which makes Month a case of it rather than the exception.

**The trend and the headline are one figure at two resolutions**, built from one
set of dates and clipped by neither, so they always sum. A future-dated
completion — a fast clock, an import — is in both or in neither; the first cut
clipped the trend at today and not the headline, and the two could differ by one.
The PR review found the same gap one resolution up: dropping months that have
not begun also dropped a future-dated completion's whole month. **A month that
holds a completion is a point even if it has not begun** — only an *empty*
un-begun month is nothing — and the selection is a filter rather than a cut, so
a month with data behind an empty one survives. The same review found the
current month's fill could exceed one (an unclipped count over the days elapsed)
and be clamped silently by the sparkline into a perfect month; the mapper bounds
it now, where the meaning is known.

**Twelve columns cannot carry twelve month names on a phone.** Under a year the
label is the month's initial, and J, M and A each name two months — §8.4's
weekday-letters problem, solved the same way: every column carries its own
spoken form, *"March, 15 active days"*, so the ambiguous letters are covered by
the thing a screen reader actually reads. The initial is a **resource**
(`insights_month_initial_*`), not the name's first character: "Juin" and
"Juillet" share one, "1月" and "11月" would share a digit, and §8.4 already
decided that an abbreviation is the translator's call. Under three columns the
full name is used, as on the rate card's five. Seen at 200 % font on a device
(running.md §4).

The value-over-label row under both sparklines is one composable,
`LabelledColumns`, because `Sparkline` centres its dots on the promise that the
row beneath it is `size` equal columns — a promise better held once than twice.

The line itself is the rate card's `Sparkline`, now a shared file taking bare
0–1 fractions: the two cards draw one series over equal label columns and a
second copy would have been a second set of mark specs to keep in step. Fixed
scale, gaps break the line, dots centred over their columns — §8.7's rules,
unchanged.

### 9.5 The focus rule

One sentence under the headline, body copy rather than a card, because it is a
remark about the two numbers above it and not a third surface. "Focus" is the
tag with the largest total. For a **complete** period — one stepped back to — it
is compared with the equal period before it, a second `observeTagEffort` read
over the previous window, the same query and no new kind of read. Different:
*"Focus shifted from health to career."* Same: *"Still mostly health."*

**The current period is not compared.** The PR review caught what the first cut
missed: at the current period the two sides are not equal — a partial figure
against a whole one — and on 1 July a single tagged completion was the whole of
"this quarter" and would have announced a quarter-scale shift. The trend card
already refused the analogous claim (one point is not a line); the sentence now
does too. The current period reads *"So far, mostly career."* — it names the
leader and claims nothing about movement. Chosen over silence, which would have
emptied the default view, and over a half-elapsed threshold, which would have
been a number nobody could defend.

Four rules, each pinned by `InsightsUiMapperTest`:

- **Untagged never wins.** It is the absence of a focus, and "shifted from
  career to Untagged" would say the user stopped caring when all they stopped
  doing was labelling. Only tagged totals compete.
- **Silence over a guess.** Either period without a tagged completion means no
  sentence. A habit tagged for the first time this quarter did not shift the
  focus from anywhere.
- **Ties resolve the way the bars sort** — largest, then name — so the sentence
  can never name a tag the list draws second.
- **Only a complete period is compared.** The current one hedges.

### 9.6 What it cost

No migration, no new query, no new module. One domain object with eleven tests,
one shared `Sparkline`, a `TrendCard`, four new fields on `Overview`, and the
fake repository learning to answer one window differently from another so the
focus sentence could be tested end to end. `check-citations` caught the docs
before the docs existed: every code comment naming this section failed lint
until it was written, which is the order the tool is there to enforce.
