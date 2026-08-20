# Habit management: the list and the editor

Companion to [the PRD](../prd.md) §4 and §5, and to
[the architecture](../architecture.md) §2 and §3. The PRD's whole habit
specification is two bullets — *"create/edit/archive habits: name, icon/color,
schedule (daily or n-per-week), optional tag"* — so this document is where the
decisions behind those two bullets are written down.

**Status:** decided and built 2026-08-20, with `:feature:habits`. Habit
**detail** is deferred; see §7.

Written after the screens rather than before them, unlike
[today-view.md](today-view.md). That is worth admitting: the Today view was
sketched first because it is the home screen and its shape constrains
everything after it, whereas this is a form and a list whose shape is mostly
dictated by `HabitMetadata`. What was genuinely open was the archive/delete
question (§4) and the weekly cap (§3), and both are recorded here.

## 1. Two screens, not three

A **list** (`Habits`) and an **editor** (`HabitEditor`). Reached from the Today
view's app-bar action, and from a button on its empty state that goes straight
to the editor.

There is deliberately **no add button on the Today view itself** beyond the
empty state's. Adding a habit is something you do a handful of times; ticking
one off is something you do daily, and PRD §6.1 wants that to take a single
tap. A floating action button over the rows would compete with the thing the
screen is for. From a populated Today view, adding a habit is two taps —
app bar, then the list's own button — and that is the right ratio.

`today_empty_body` has read *"Momo is waiting. Add a habit and it starts here"*
since the Today view was built, with nothing to tap. The empty state's button is
what makes that sentence true, and it is the shortest path from a fresh install
to a first habit — which is what PRD §5's 30-day trial cannot start without.

## 2. One editor for create and for edit

`HabitUpdated` is a **whole-record last-write-wins register**
(architecture §3), not a per-field merge. An edit therefore has to submit every
field, which is exactly what a create submits. So it is one screen, one state
type, and one `Form`; `editing` changes the title and nothing else.

This is not a saving of effort, it is a correctness property. Two screens would
invite an edit form that submitted only what changed, and the fields it left out
would be silently overwritten with defaults.

## 3. The form, and the one thing it can get wrong

Fields are fixed by `HabitMetadata(name, icon, color, schedule, tag)`.

- **Name** — free text. A blank name is `CommandError.BlankName`, and it is the
  **only** thing the domain rejects about metadata. Submitted **untrimmed**,
  matching `Commands.createHabit`, which tests `isBlank()` on whatever it is
  handed; trimming here would let a name of only spaces pass the form and be
  rejected by the domain.
- **Icon and colour** — picked from a fixed palette in `:core:ui`
  (`HabitPalette`), never typed. Two reasons: nobody wants to type `#7E57C2`
  into a phone, and picking from a list is what makes every stored colour valid
  *by construction*. That is what leaves a blank name as the only reachable
  validation error. `parseHabitColor` still exists and is still needed — the
  palette is what the editor offers, not a guarantee about what is in the log,
  which may hold anything an import or the old debug seeder wrote.
  Icons are **emoji**, because `HabitMetadata.icon` is a `String` that has to
  survive an export and an import, and a drawable resource id would not.
- **Schedule** — daily, or weekly with a target. **The target is capped at
  1..7, and the cap is load-bearing.** `Schedule.Weekly` validates with
  `require`, so an out-of-range target **throws** rather than returning a
  rejection: an unbounded stepper would crash on the save button instead of
  showing an error. The cap is seven because completions are idempotent per
  logical date (architecture §4), so an eighth can never be earned. It is
  enforced in three places — the stepper's bounds, `ScheduleUi.toDomain()`'s
  `coerceIn`, and `Schedule.Weekly` itself — and the middle one is a backstop,
  not a duplicate.
- **Tag** — one free-text tag, per PRD §4 and OQ-1's proposal. Blank means *no
  tag*, translated to null in one place (`Form.toMetadata`).

`canSave` and the ViewModel's own blank check are both present on purpose. A
disabled button and an enforced rule are different things, and only one of them
is enforcement.

## 4. Archive is the delete story

**There is no `HabitDeleted` event, and this document is the record that its
absence is a decision.** The PRD and architecture §3 both say create, edit,
archive, unarchive and never mention deletion.

Archiving is right for an append-only log. A habit's completions are history,
and history is the thing this app exists to keep; deleting a habit would either
orphan its completions or destroy them. Archived habits leave the Today view
(`observeToday` filters `archived = 0`) and stop counting toward the mascot's
mood, which is everything "delete" was wanted for.

Adding a real delete later is not a small change: a new payload, a wire DTO, a
codec entry, a `Projector` branch, both of `ProjectionWriter`'s exhaustive
`when`s, a `PROJECTION_VERSION` bump, and a decision about whether it tombstones
the habit's completions. Do not add it casually.

**Archiving is idempotent.** Archiving an archived habit is accepted and
converges under last-write-wins — there is no "already archived" error. That is
why the list must offer the *way back* on an archived row rather than the same
button again: otherwise it is a dead control with no error to show for itself.

## 5. Archived habits are shown, not hidden behind a toggle

Both sections are always drawn, with a heading over the archived one.

A show-archived toggle was considered and rejected. The habit you have put away
is precisely the one you need to be able to find in order to bring it back, and
a toggle defaulting to off hides the only reason this screen shows archived
habits at all. A heading says where something went and holds no state that can
fall out of step with the list above it.

Revisit if the archived section ever gets long enough to bury the active one.
With a solo user's habit count that is not close.

## 6. Row actions are separated on purpose

The title block opens the editor; a trailing button archives. The row itself
does **not** toggle archived.

Archiving is the one of the two that feels destructive, so it gets its own
target and its own word. A row where tapping the name archived it would lose a
habit off the list with nothing to say it had happened — and since archiving is
idempotent, there would be no error either.

Contrast the Today view, where §5 of [today-view.md](today-view.md)
deliberately makes the *whole row* the toggle: there, one tap is the point, and
the action is trivially reversible.

## 7. Still open

- **Habit detail is deferred.** Architecture §2 scopes this module as
  "create/edit/archive habit, habit detail", and the detail screen is the part
  not built. PRD §6.6 wants streak visibility in "Today view, widget, habit
  detail", so the third of those is outstanding. The reads it needs already
  exist and are still callerless: `observeHabit` and `observeCompletedDates`.
- **`createHabit` mints and returns the `HabitId`** so a caller "can navigate to
  it", per its own KDoc. With detail deferred, the editor only pops back and
  that returned id goes unused. It is the natural first user of a detail screen.
- **The retro strip and the honesty prompt** (PRD §5, three-day retro window)
  belong to habit detail too, and none of that UX is specified anywhere yet.
- **Multi-tag (PRD OQ-1).** One tag is baked into `HabitMetadata.tag` and into
  the wire format, so multi-tag is an event-payload schema bump with an
  upcast-on-read, not a UI change.
- **No habit-count limit** is specified anywhere, and none is enforced. The list
  is a `LazyColumn`, so this is fine until some other part of the app cares.
