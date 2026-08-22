# Habit management: the list, the editor and habit detail

Companion to [the PRD](../prd.md) §4 and §5, and to
[the architecture](../architecture.md) §2 and §3. The PRD's whole habit
specification is two bullets — *"create/edit/archive habits: name, icon/color,
schedule (daily or n-per-week), optional tag"* — so this document is where the
decisions behind those two bullets are written down.

**Status:** the list and the editor decided and built 2026-08-20, with
`:feature:habits`. **Habit detail** built 2026-08-21 — §7 has its decisions,
and it closes the last three of PRD §6's UX criteria. What remains open is in
§8.

Written after the screens rather than before them, unlike
[today-view.md](today-view.md). That is worth admitting: the Today view was
sketched first because it is the home screen and its shape constrains
everything after it, whereas this is a form and a list whose shape is mostly
dictated by `HabitMetadata`. What was genuinely open was the archive/delete
question (§4) and the weekly cap (§3), and both are recorded here.

## 1. Three screens

A **list** (`Habits`), an **editor** (`HabitEditor`) and **detail**
(`HabitDetail`). Reached from the Today view's app-bar action, and from a button
on its empty state that goes straight to the editor.

This section said "two screens, not three" until 2026-08-21, and the third it
was refusing was a separate *detail-and-edit* split of the form. That is still
refused — §2 is why one editor serves create and edit. Detail is not that third
screen: it is read-only, and it is where a habit is looked at rather than
changed.

There is deliberately **no add button on the Today view itself** beyond the
empty state's. Adding a habit is something you do a handful of times; ticking
one off is something you do daily, and PRD §6.1 wants that to take a single
tap. A floating action button over the rows would compete with the thing the
screen is for. From a populated Today view, adding a habit is two taps —
app bar, then the list's own button — and that is the right ratio.

`today_empty_body` has read *"Momo is waiting. Add a habit and it starts here"*
since the Today view was built, with nothing to tap. The empty state's button is
what makes that sentence true, and it is the shortest path from a fresh install
to a first habit — which is where any real use of this app has to start.

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
is enforcement. The field is drawn as an *error* only when editing, though: a
habit that had a name and no longer does is wrong, while an untouched create
form is not wrong yet, and greeting a first habit with a red field is not
validation. The disabled Save is what conveys the block in that case.

**Saving is guarded against a second tap.** `createHabit` is the only
non-idempotent command this module issues — archive and unarchive converge
under last-write-wins, and a completion collapses per logical date — so nothing
below deduplicates it, and two quick taps would leave two identical habits and
pop the back stack twice. The guard releases on rejection, because a save the
user can fix has to be a save they can retry.

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

The title block opens the habit; a trailing button archives. The row itself
does **not** toggle archived.

Archiving is the one of the two that feels destructive, so it gets its own
target and its own word. A row where tapping the name archived it would lose a
habit off the list with nothing to say it had happened — and since archiving is
idempotent, there would be no error either.

**Amended 2026-08-21:** the title block led to the *editor* until habit detail
was built. It now leads to detail, and detail carries an Edit action of its own.
The separation this section is about is unchanged — the destructive action still
has its own target and its own word — and what moved is only which screen the
safe tap opens. Detail is the better default: opening a habit is usually to look
at it, and the editor is one tap further on rather than unreachable.

Contrast the Today view, where §5 of [today-view.md](today-view.md)
deliberately makes the *whole row* the toggle: there, one tap is the point, and
the action is trivially reversible.

## 7. Habit detail

Built 2026-08-21. One screen, three jobs — and between them they close PRD §6.3
(notes), §6.4 (retroactive edits) and §6.6 (streaks on habit detail), which were
the last three of §6's six criteria still open.

### The streak is the subject, not a badge

The Today row draws a streak as a compact trailing badge; detail draws it large
and captioned. That follows [widget.md](widget.md) §2's argument for narrowing
§6.6 to two surfaces: the in-app ones are where a streak is *read deliberately*
rather than glanced at, and this is the one you open on purpose.

The rules themselves are shared, not restated. `StreakUi` and its mapper moved
to `:core:ui` when this was built, so [today-view.md](today-view.md) §5's "a
daily streak is a count, a weekly one is in weeks, and the two must never be
styled as the same number" is one decision with two renderings rather than two
copies that can drift. Detail distinguishes them three ways — the `w`, a caption
naming the unit, and a different colour role — so it survives a reader who
cannot tell the two colours apart.

### The retro strip is five cells, and one of them is shut

PRD §5 allows retroactive logging up to three days back. today-view.md §5 had
already decided how that limit is shown, and it is the reason the strip is five
cells rather than four:

> **Days outside the retro window are drawn shut**, not tapped and refused. With
> today at Tue 19, Sat 16 is the oldest open day and Fri 15 renders struck
> through and dashed. The command rule should be readable before it is hit.

`Fri 15` is `today − 4`, one day past what `Commands.addCompletion` will accept.
A four-cell strip would show only legal writes and say nothing about the edge,
so the strip reaches one day further back than it can write to. That cell is
inert: no tap, no long-press, and `disabled()` in its semantics. A cell that
answered a gesture with a snackbar would be exactly the tapped-and-refused
behaviour §5 argues against.

It renders **struck through and dimmed** rather than struck through and dashed.
A true dashed border needs a drawn stroke rather than a `BorderStroke`, and the
strike-through plus a quieter outline and glyph already carry it. Recorded
because it is a deliberate departure from §5's wording.

A shut day still shows whether it was completed. It is refused, not hidden.

**Not a calendar.** PRD Phase 1's Insights v1 is where a per-habit
heatmap/calendar history goes. This is the writable window and the one day past
its edge, which is a different thing with a different job.

**An archived habit's cells are all shut** — added in review. `Commands` rejects
every completion write on an archived habit, so a live cell there could only
answer a tap with a refusal, which is the thing §5 is arguing against and which
the paragraph above claims this screen does not do. The archived section's rows
open detail with the same tap as any other, so it is the documented path rather
than a corner. Archiving is undone from the list row (§6); detail stays
read-only until it is, and its header already says "Archived".

**A day carrying a note is marked** — also added in review. The note reached the
cell but only the sheet read it, so an annotated day looked exactly like a bare
one and nothing advertised the long-press. A `•` beneath the tick, and "has a
note" appended to the cell's spoken label. The second half is the sharper one:
without it a note is discoverable only by long-pressing every completed day in
turn.

### Every past-day write confirms, in both directions

PRD §5 fixes the copy — *"You're logging for a previous day — make sure this is
accurate. Be true to yourself."* — and §6.4 wants retroactive edits to "carry
deliberate friction but stay possible".

The prompt appears for an **undo as well as a completion**. §5 says "editing a
past day", and un-ticking a day you did do rewrites the record as much as
ticking one you did not. Today's cell writes straight through, which is §6.4's
other half: "same-day undo is frictionless".

It is **friction and nothing else**. Architecture §5 is explicit that the 3-day
window is a *command* validation and that the confirmation is UI, with nothing
enforcing it. Two consequences the implementation has to honour: dismissing
leaves the log untouched rather than deferring a write, and the domain still
refuses a day out of range whatever the screen believed. `RetroWindowExceeded`
therefore has real copy despite the strip never offering an illegal tap — the
day can roll over between the strip being drawn and a tap landing on it.

### The note is behind a long-press, on a completed day only

PRD §5 puts an optional note on a completion, reached by "long-press / detail
view"; §6.3 requires notes never add friction to the base flow. Nothing on the
way to logging a day asks about a note.

Offered on a **completed** day only: a note hangs off a completion, and
architecture §4 has notes die with the add they belong to, so there is nothing
to annotate on an empty day and `updateNote` would reject it with
`CompletionNotFound`.

Not offered on the **shut** day either, even though `updateNote` has no
retro-window check and the domain would accept it. Annotating is not claiming to
have done something, so the window does not apply — but shut has to mean inert,
and a cell that refuses a tap while answering a long-press does not read as
closed. That is a UI choice, not a rule, and it is the one place this screen is
stricter than the domain.

**Clear is a button, not a disabled Save** — today-view.md §5, and the reason is
architecture §4: an empty note is a real write that clears the note and wins
last-write-wins like any other. Removing a note has to be something you can ask
for rather than something you discover by emptying a field. It reports the same
write Save does with an empty field, because it *is* that write; what §5 asks
for is the affordance.

It is a `ModalBottomSheet`, the first in the app — every other overlay here is
an `AlertDialog`. Its content is a separate stateless composable so the tests
can assert what the buttons report without driving a sheet's animation.

### Creating a habit opens it

`createHabit` mints a `HabitId` and returns it "so the caller can navigate to
it", per its own KDoc. Until detail existed there was nowhere to go and the id
went unused; saving a new habit now lands on it.

The editor is popped inclusively on the way. Without that, Back from a new
habit's detail lands in the form that made it — a filled-in create form that
would append a second identical habit if saved again.

## 8. Still open

- **A per-habit heatmap or calendar history** is PRD Phase 1's Insights v1, not
  this. §7's strip is the writable window and the day past its edge; a month
  view is a different screen with a different job, and building one here would
  pull Phase 1 scope into the MVP.
- **Nothing on this screen is reachable from the Today view.** Detail is opened
  from the habit list or by creating a habit. PRD §5 names a long-press on a
  Today row as one route to the note, and that route does not exist — the list
  is the only door. **The evidence that was going to settle this is not coming**
  — the 30-day trial was waived on 2026-08-23 (PRD §5), and "how often is a note
  actually wanted" was exactly the kind of thing it would have answered. So this
  gets decided on design grounds when Insights v1 next opens this screen up
  (PRD §5, Phase 1), or it stays as it is.
- **Multi-tag (PRD OQ-1).** One tag is baked into `HabitMetadata.tag` and into
  the wire format, so multi-tag is an event-payload schema bump with an
  upcast-on-read, not a UI change.
- **No habit-count limit** is specified anywhere, and none is enforced. The list
  is a `LazyColumn`, so this is fine until some other part of the app cares.
- **A habit whose stored icon or colour is not in `HabitPalette` opens the
  editor with nothing selected in that picker.** Deliberately left: the form
  carries the loaded value, so saving preserves it and only the picker looks
  unset. Reachable today only for habits the old debug seeder wrote. The fix is
  to append the current value to the offered list when absent — worth doing if
  import ever makes off-palette values common, and safe to add now that the
  colour label lookup degrades rather than throwing.
- **A `Saved` event can be lost** if the Route's collector is cancelled between
  `receive()` and the callback, leaving the habit saved but the editor open with
  Save latched. Narrow enough to accept: the same window exists for a rejection
  snackbar, which nobody would call a bug. The fix is to make "saved" a state
  flag rather than a one-shot event, which is a redesign, not a patch.
- **The in-progress form does not survive process death** — the ViewModel holds
  a plain `MutableStateFlow` with no `SavedStateHandle`. Draft persistence is a
  feature rather than a fix.
