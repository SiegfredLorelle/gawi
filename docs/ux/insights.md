# Insights v1: history, trends and where the effort goes

Companion to [the PRD](../prd.md) §5 Phase 1 and §8's OQ-1, and to
[the architecture](../architecture.md) §2. The PRD asks for three things —
*"per-habit heatmap/calendar history, completion-rate trends"* and
*"tag-based effort distribution: share of completions per tag over a selected
period"* — and leaves every visual and definitional choice open. This document
is where those get decided.

**Status: a sketch, written 2026-08-23, before anything is built.** That is the
opposite of [habits.md](habits.md), [widget.md](widget.md),
[settings.md](settings.md) and [reminder.md](reminder.md), which were all
written *after* their screens and record what was decided by building. Read
everything here as provisional in a way those are not, and expect this file to
be rewritten rather than appended to once the screen exists. What it is for
right now is to stop three questions being answered by accident: which module
this lives in, what a cell in a heatmap is allowed to mean, and what happens to
the tag metric when OQ-1 lands.

`:feature:insights` still does not exist. `insights` **is** now in `scope-enum`
in `.commitlintrc.yaml`, added 2026-08-23 ahead of the module rather than with
it, because the `commit-msg` hook rejects a module's *first* commit if its scope
is missing — so the entry has to exist before the module does.

The module itself was deliberately **not** created empty, and that is worth
recording because it was tried. An Android library always has test sources
configured for its unit-test variant, so Gradle 9 fails
`:feature:insights:testDebugUnitTest` with "there are test sources present …
but the test task did not discover any tests" the moment the module is included
with nothing in it. The available workarounds are setting
`failOnNoDiscoveredTests = false`, which would mask a real misconfiguration
later, or inventing a placeholder test, which asserts nothing. Neither is worth
it: **the module gets created together with its first real file.**

What *is* built, as of 2026-08-23, is the two layers underneath — §6's owed tag
aggregate query, and §4's completion-rate denominator. Both were unblocked while
the screens were not, for the reason PRD §5 now records: they have no colour in
them.

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
2. **Completion-rate trends.** Needs §4's definition settled before it can be a
   number at all.
3. **Tag effort distribution.** Needs a new aggregate query and carries §5's
   problem.

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

  One limitation is the screen's to solve and is recorded rather than hidden:
  nothing in the projection stores when a habit was created — `HabitState` has no
  such field — so a window reaching back before the habit existed yields a rate
  that is arithmetically right and meaningless. The earliest completed date is
  the available proxy, and whether to clamp to it is a presentation decision.

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
- **The period picker.** "Over a selected period" (PRD §5) does not say which
  periods. Whether this is a fixed set (month / quarter / year) or a range, and
  whether it is shared with Phase 1.5's retrospectives, is undecided — sharing it
  is the reason to decide once rather than twice.
- **The colour scale**, and specifically whether intensity encodes anything at
  all. A binary done/not-done grid needs two colours; a count-per-day scale needs
  more and has nothing to count, because completions are idempotent per logical
  date (architecture §4). Two colours, then, which makes this easier than a
  heatmap usually is — and **no longer blocked, as of 2026-08-23**. It was: the
  app had no palette, `GawiTheme` was stock Material 3 by an explicit deferral to
  PRD §8's OQ-4, and choosing two colours before that landed would have meant
  choosing them twice — which is what inverted PRD §5's Phase 1 order. The
  designed scheme has now landed
  ([visual-identity.md](visual-identity.md) §7.2), so this is a live decision
  rather than a deferred one. What has not changed is the constraint on it:
  whatever is chosen must come from `MaterialTheme` colour roles rather than
  literals, so the palette reaches this grid the same way it reaches every other
  screen. `primary` is the obvious "done" — it is already what marks a completed
  cell in habit detail's `RetroStrip`, and reusing it means the two history
  surfaces agree — with the "not done" role picked so it does not read as
  disabled-because-broken. That is a decision for the screen, not for this
  paragraph.
- **Where it is reached from.** Habit detail for the per-habit grid is the
  obvious door; the tag distribution has no obvious one, and inventing a
  top-level destination is a navigation decision that belongs to `:app`.
- **Whether Momo appears here.** PRD §5 puts him in the Today view, the widget
  and the reminder. This screen is not on that list and should probably stay off
  it until OQ-4 is settled.
