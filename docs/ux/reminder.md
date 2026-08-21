# The end-of-day reminder, and the wake it shares

Companion to [the PRD](../prd.md) §4, §5 and §6.1, and to
[the architecture](../architecture.md) §2, §4 and §7. The PRD specifies the
behaviour — *"end-of-day reminder notification if due habits remain incomplete
as the day boundary approaches… silent when everything is done. One reminder max
per day"* — and this document is where the mechanism and the permission are
decided.

**Status:** decided and built 2026-08-21, in `:core:data` (the decision), `:app`
(the workers and the notification) and `:feature:settings` (the permission).
This is the second half of PRD §6.1's first criterion, the widget having been
the first.

It ships **two** scheduled wakes, and only one of them is a notification. The
other is the day-rollover refresh that [widget.md](widget.md) §4 and §6 leave
open and nominate this step for. They are together because they are the same
mechanism — a wake at a wall-clock instant that no event can trigger — and
apart in every other respect.

What was genuinely decided: where the decision lives (§1), how the two wakes
stay armed (§2), where the permission is asked for (§3), and what the
notification does and does not do (§4).

---

## 1. `:core:data` decides, `:app` schedules and posts

Architecture §2 gives `:app` *"WorkManager scheduling for the end-of-day
reminder"*, and that is where the workers are. But the *decision* — is anything
still outstanding, and has today already been reminded about — is not scheduling.
It went to `:core:data`, as `ReminderCheck`, and the split is worth stating
because the obvious alternative is worse in a specific way.

Everything the decision needs already exists there:

- **`Mascot.isOutstanding`**, whose KDoc says outright that a second copy of the
  now-or-never rule is how the Today view's chip and this notification would come
  to disagree. The daily case is trivial; the weekly one is that a habit is only
  outstanding once the week has too few days left to finish it, which is not
  something to re-derive in `:app`.
- **`reminderOn`**, which has named this as its third caller since it was
  written. With an 03:00 cutoff and a 21:00 reminder, 01:30 belongs to the
  previous logical day — the case a same-date comparison gets backwards.
- **`observeToday()`**, which returns the rows, the logical date they were
  queried for, the wall clock, and the two thresholds, in one read. Reading the
  clock again in `:app` would be a second, independently-resolved "today" that
  could disagree with the rows it was describing.

So `:app` holds no clock, no cutoff and no schedule rule. It holds a
`CoroutineWorker`, a channel and a `PendingIntent`, and it names `MainActivity`,
which is the one thing `:core:data` cannot know.

`ReminderCheck` is a **public class with an `internal` constructor**. This
module's habit is an internal implementation behind a public interface —
`OfflineFirstHabitRepository` behind `HabitRepository` — which is right when
there is a seam worth faking. There is not one here: nothing wants to substitute
a different reminder rule, and an interface with one implementation and one
caller is ceremony. The visibility split says the true thing instead, and it is
what keeps `ReminderJournal` internal.

### The journal, and the asymmetry that is the point of it

`ReminderJournal` stores one value: the logical date last reminded about. It is
PRD §6.1.5's *"one reminder max per day"*, and it is shaped after
`ExportJournal` — same preferences file, its own key, `IOException`-only
`catch` — because it is the same kind of thing: a record of what the app *did*,
not a preference the user set, so not a fourth `UserSettings` field.

**Its failures resolve the opposite way round from `ExportJournal`'s, and two
classes in one file resolving failures in opposite directions is exactly what a
later reader unifies.** That one is arranged so every failure resolves *towards
nudging*: a wrong warning costs an export nobody needed, a wrong silence costs
the warning the PRD asked for. Here the costs are reversed — an extra
notification is the failure a user actually notices, and "one max per day" is a
headline criterion, while a reminder missed once is a nudge that arrives
tomorrow. So an unreadable file **suppresses**, and a failed write after posting
is absorbed.

One thing was taken straight from `ExportJournal`, including the correction a
reviewer made to it: **a stamp dated well in the future reads as no stamp at
all.** A device whose clock ran a month ahead when a reminder was posted, and
was correct afterwards, would otherwise be silenced for a month — invisibly,
which is the one failure worse than a duplicate. A stamp one day ahead still
suppresses, because a clock nudge across local midnight is jitter and must not
re-arm a reminder just posted. Epoch days are compared rather than `LocalDate`s,
which is also what stops a nonsensical stored value reaching
`LocalDate.ofEpochDay` and throwing out of a function whose whole job is to be
un-throwable.

### The threshold is re-checked, and that is not belt-and-braces

`evaluate()` refuses to post if the wake arrives before the reminder threshold.
The case it exists for is the *late* one: a wake deferred by Doze, or by a
powered-off device, can arrive **after the day cutoff**, inside the next logical
day, where every habit is legitimately incomplete. Without the check it would
post *"5 of 5 left today"* at 00:30 and — worse — stamp the journal for the new
day, so the real reminder that evening would be suppressed by the one that fired
by mistake.

The tolerance is asymmetric: late is refused outright, early is allowed a
minute. The costs are not comparable. A wake a second early that is refused
means no reminder *at all* that day, because the next armed wake is tomorrow's;
a wake a minute early means a nudge a minute early, which nobody perceives.

**A reminder set equal to the day cutoff is refused outright**, which is the
other half of the same check. `reminderOn` resolves that pair to the logical
day's *start* rather than its end — its KDoc has always said so, and said that a
settings screen was where the combination should be prevented. Nothing prevented
it, and while the setting only drove Momo's face the cost was invisible: a mascot
that looked worried all day reads as a mood, not a bug. With a notification
behind the same threshold it became a *"N of N left today"* at the top of every
logical day which *also* consumed that day's one reminder, so the evening was
silent too — the worst of both. `:feature:settings` refuses the combination now
(§3); this refuses to act on a value an older build already stored.

`reminderOn`'s threshold is the one used, so it is the same instant the mascot's
`nearBoundary` turns on at. `nearBoundary`'s *upper* bound is deliberately not
repeated — it exists there to protect a caller holding a stale date, and the
snapshot's date is derived from the snapshot's clock in the same read, so here it
would be dead code.

---

## 2. Two wakes, and they arm each other

Both are one-time unique work with an initial delay, not periodic work. A 24-hour
period drifts, cannot follow a settings edit, and cannot express "the next
cutoff" across a DST shift.

| Wake | Fires at | Does |
|---|---|---|
| `gawi.reminder.end-of-day` | the reminder time | posts the notification, or stays silent |
| `gawi.reminder.day-rollover` | the day cutoff | `refreshStreaks()`, then pushes `ProjectionListener` |

**`ReminderWorker` arms the rollover; `RolloverWorker` arms the reminder.**
Neither re-enqueues its own name, and that is not a stylistic choice.
`enqueueUniqueWork` with `REPLACE` cancels whatever is already under that name
**including a run in progress** — so a worker that re-armed itself would cancel
itself every time, leaving the tail of its own `doWork` running inside a
cancelled coroutine and its completion recorded as `CANCELLED`. Correct-looking
and racy.

**And the workers arm with `KEEP`, not `REPLACE`.** This is the second half of
the design, and the first draft got it wrong in a way that lost a whole day's
reminder. `KEEP` means *"make sure the other wake exists"*: pending work is left
alone, and completed work is not pending, so the next occurrence is enqueued
normally. With `REPLACE` there was a real hole — a reminder wake deferred past
the cutoff (device off overnight) runs late, correctly decides to stay silent,
and then **destroyed the overdue rollover work that was about to re-arm it**. So
nothing was left under the reminder's name, and that whole day had no reminder;
the chain only resumed at the *next* cutoff. Found by `/code-review`.

`KEEP` also disposes of a race the first draft's prose called impossible. It
claimed the other name is *provably* not running, since the reminder falls
strictly inside a logical day and the cutoff ends it — and that a reminder set
equal to the cutoff would leave the two "a whole day apart". **That was simply
wrong**: day `D + 1`'s start *is* day `D`'s boundary, so equal times put both
wakes on the same instant, and under `REPLACE` each worker would have cancelled
the other mid-run. §3 now prevents that setting and §1 refuses to act on a stored
one, but `KEEP` is what makes the coincidence harmless rather than merely
unlikely.

So the chain alternates — 21:00 arms midnight, midnight arms 21:00 — and if
either link is ever lost, `ReminderScheduler.start()` re-arms both on the next
process start. The chain has a repair path that does not depend on itself.

### The settings collector, which closes a second gap for free

`ReminderScheduler` collects `SettingsSource.observe()` on an application-scoped
coroutine, deduped on `(dayCutoff, reminderTime)` — the two fields that move a
wake. The week start moves neither, so reacting to it would re-enqueue both works
for nothing.

This exists because **a settings edit is not an event**. Changing the reminder
time moves the threshold and changing the day cutoff moves the logical date, and
neither writes anything to the log, so nothing can push either change at
WorkManager. It also closes [widget.md](widget.md) §6's *"a settings edit is not
an event either"* item with no mechanism of its own: a cutoff edit re-arms the
rollover wake along with the reminder.

The first emission uses `ExistingWorkPolicy.KEEP` and every later one uses
`REPLACE`. The first is "these are the settings", which is not an edit and must
not disturb work already scheduled — or, worse, running. Every later one is a
real edit, where replacing the pending wake is the entire point.

### The rollover wake is the one that happens because nothing happened

Every other redraw in this app follows a commit: a write moves the derived
tables and `ProjectionListener` pushes that at Glance. A day rollover commits
nothing — it is a wall-clock instant, not an event — so no push can fire for it,
and a widget with no live session goes on showing yesterday's ticks
(architecture §4). `RolloverWorker` is the third caller of `ProjectionListener`
and the first of a different kind, and its two calls are ordered: sweep the
streaks, *then* push. Pushing first would redraw the old streaks and leave the
new ones unpushed until something else committed.

`refreshStreaks()`' own KDoc already said *"the only way a streak reaches zero
without a new event, so a day-rollover worker will want this"*. This is that
worker.

### What is deliberately not used

- **No constraints on either request, and never a network one.** `:app`'s
  manifest removes `ACCESS_NETWORK_STATE` with `tools:node="remove"` because
  WorkManager wants it only to evaluate network constraints and nothing here has
  one. A constraint added to either request would force that line out and
  falsify PRD §5's *"no network permission at MVP"* — a headline claim — for a
  wake that needs no network. `ManifestPermissionTest` is the tripwire.
- **No exact alarms.** Architecture §7 rules out `SCHEDULE_EXACT_ALARM` by name:
  a "habits left today" nudge does not need exact delivery, and the permission
  attracts Play-policy scrutiny. Do not "upgrade" this.
- **Not expedited.** `setExpedited` pulls foreground-service behaviour into a
  background nudge.
- **No boot receiver.** `RECEIVE_BOOT_COMPLETED` is already granted and
  WorkManager reschedules its own persisted work.
- **Not `hilt-work`.** `@HiltWorker` wants a `HiltWorkerFactory` installed
  through a `Configuration.Provider` on the `Application`, and that configuration
  governs every worker in the process — **including Glance's own
  `SessionWorker`**, which is how the widget renders at all. Taking over
  WorkManager's initialisation to inject two classes would put the widget's
  rendering path behind a change made for the reminder's convenience. An
  `@EntryPoint` resolved off the application is the pattern `:widget` already
  established for framework-constructed objects, adds no dependency, and leaves
  the default `androidx.startup` initialisation exactly as the widget found it.

---

## 3. The permission, asked for from the settings row

`POST_NOTIFICATIONS` is the app's **first runtime permission** and the only
permission it declares by hand. Four of the five in the merged manifest arrive
with WorkManager and androidx.core; this one is a decision, and
`ManifestPermissionTest` now asserts that split separately so a hand-added
permission fails a test whose name says what happened.

**It is requested from the settings screen's reminder row, not at first launch.**
The row already existed and already admitted the notification was unbuilt. A
cold prompt on first run arrives before the user has any habits, or any reason to
want a reminder, and is the version most likely to be refused permanently.

**The permission is the on/off switch.** There is no fourth `UserSettings`
field. Notifications allowed means the reminder runs; revoked in system settings
means it does not, and the row says so. The PRD asks for a configurable time and
not for a toggle, and a second source of truth for one behaviour is one more
thing to keep in sync.

### Three details that are each a small trap

**The state is read with `NotificationManagerCompat.areNotificationsEnabled()`,
not `checkSelfPermission`.** The permission only exists on API 33+, while
switching notifications off in system settings works on every version — so the
permission answers the narrower question and would report "allowed" on API 29 for
an app the user had silenced. `areNotificationsEnabled` is the honest reading of
*"will the reminder be seen"*, which is what the row claims. `ReminderNotifier`
checks **both**, and the second one is there because Android Lint's
`MissingPermission` will not accept the first in its place.

**It is re-read on resume, not once.** The fix for a blocked notification is in
system settings, which means leaving the app and coming back — so a value read
once would show the stale answer for exactly as long as the user was looking to
see whether it had worked.

**The affordance must not dead-end, and getting this right needed a correction.**
`RequestPermission` returns *instantly and silently* once the user has refused
for good, so a row reading "tap to allow" would do literally nothing when tapped.
The escalation to the system's own notification page is decided **in the request
callback and not before the launch**, because
`shouldShowRequestPermissionRationale` is `false` in two unrelated states —
before the permission has ever been requested, *and* after it has been refused
for good. An earlier version of this used it before the launch, which would have
sent every first-time user straight into system settings instead of showing them
the dialog: the single commonest path through the row. By the time the callback
runs, the "never asked" reading is impossible and `false` means one thing.

The copy does not name a permission. Below API 33 there is nothing to grant — the
user switched notifications off and system settings is where they go back on — so
"allow the permission" would be wrong on some versions and jargon on all of them.
It says what is not happening and offers to fix it, which is true everywhere.

### The one settings combination that is refused

The reminder time may not equal the day cutoff, from either row. It is the first
**refusable** settings write in the app, and `SettingsMessage`'s KDoc used to
argue that no such thing could exist: *"a fixed picker cannot express an invalid
time"*. That was sound and incomplete — a picker cannot express an invalid time,
but it can express a valid time that is invalid *against another setting*, which
is a validation the store cannot do because it sees one field at a time.

Guarded from both rows, because either can create the collision and a screen that
refused it from one side while allowing it from the other would be worse than one
that did neither.

The notice is its **own** target below the row rather than a state on it.
`SettingRow`'s rule is that the whole row is the target, and the row's tap
already means "change the time" — which stays worth doing while notifications are
off, because the same setting decides when Momo starts looking worried
([today-view.md](today-view.md) §4). Folding two actions into one row would make
a tap ambiguous; making the row *do* this instead would take the time picker away
over a permission.

---

## 4. What the notification says, and what it does not do

One notification, a fixed id so a duplicate replaces rather than stacks, and a
`PendingIntent` that opens the app. `FLAG_IMMUTABLE`, because nothing fills
anything in on it.

The copy is Momo's, matching the Today view rather than inventing a second
register for the shade, and the count reuses `today_remaining`'s exact phrasing —
*"2 of 5 left today"* — so the shade and the app agree about the number instead
of describing it two ways.

`IMPORTANCE_DEFAULT`, which makes a sound. A habit nudge that arrives silently is
one the user finds the next morning, which is the whole point missed;
`IMPORTANCE_HIGH` would be an interruption for something that is not urgent. Both
are the user's to change in the channel's own settings.

**No action buttons, and this is a PRD decision rather than a shortcut.**
Quick-complete is PRD §4's explicit stretch goal, *"allowed to slip to Phase 1;
documented so it isn't lost"*. §6.1.1's *"logging < 5 seconds"* is already
satisfied by the widget, so a button here is a second path to a solved problem —
and it would carry OQ-2 with it, which is still unanswered: Android caps three
action buttons, and what to show when more than three habits remain is a real
design question. Deep-linking is out for a smaller reason: `:app` owns the
navigation graph and Today is already the start destination, so a route would be
a second way to express the same landing place, free to disagree with the graph.

---

## 5. What was measured rather than reasoned about

Three things, recorded because the last step's review rounds were all prose
overclaiming rather than misbehaving code.

**WorkManager was pinned before the permission was added, on purpose.** `:widget`
takes Glance on `implementation`, so WorkManager reached `:app`'s runtime
classpath and never its compile one — a worker there does not build without an
explicit declaration, which meant choosing a version where Glance's transitive
**2.7.1** (2021) had been the silent default. 2.11.2 went in first, alone, and
changed the requested permission set by **nothing**. Then `POST_NOTIFICATIONS`
went in and `ManifestPermissionTest` **failed**, naming exactly one addition.

That failure is the reason the first result is worth anything. A check that has
only ever returned "clean" has not been shown able to return "dirty", which is
precisely the mistake §5 of [widget.md](widget.md) records about a broken grep.
Both versions' AARs were also read directly: 2.7.1 and 2.11.2 declare the same
four permissions, so the `tools:node="remove"` line is not vestigial.

**WorkManager is not initialised under Robolectric.** `WorkManager.getInstance`
throws `IllegalStateException` there — its `androidx.startup` provider does not
run. This has two consequences worth stating together. `ReminderScheduler`'s
`Throwable`-absorbing guard is therefore load-bearing rather than defensive: it
is why `AppNavigationTest` and `AppSmokeTest` still pass with the scheduler wired
into `Application.onCreate`. And that same guard means a *completely broken*
scheduler would pass those tests in silence — the failure shape
`ProjectionListenerTest` exists to rule out for the widget. `work-testing` and
`WorkManagerTestInitHelper` are what close it, in `ReminderSchedulerTest`.

**One comment claimed a mutation reddened one test; it reddens three.** Stubbing
out the threshold guard was written up as failing only the deferred-wake test.
Run, it fails that one and two others — the early-direction cases. The comment
was corrected to what the run said. Same lesson as the two above, in the smallest
possible form: run the control, in the same pass.

---

### What the review round found, which was code this time

The three above were caught before review. `/code-review` then found five things,
and the shape of them is worth recording because it is **not** the shape of the
last step's rounds: 11a's findings were all prose overclaiming with correct code
behind it, and three of these were real defects.

- **A reminder equal to the day cutoff** posted "N of N left today" at the top of
  every logical day and consumed that day's reminder (§1, §3). Latent in the
  mascot since step 4a; the notification is what made it bite.
- **`REPLACE` in the workers lost a whole day's reminder** after a wake deferred
  past the cutoff (§2). The subtlety is that the late worker destroyed the
  *other* pending work that would have repaired it.
- **`untilNextCutoff` could arm a wake in the past**, once a year, for one hour:
  a cutoff inside a DST fall-back's repeated hour makes "today" regress, which
  `logicalDate`'s KDoc documents and this had not accounted for.
- Two smaller ones: an unguarded `startActivity` for a system settings action
  that need not resolve, and a KDoc claim about strictness that was false.

Two of my own KDoc paragraphs asserted the exact properties that were broken —
"provably not running" and "always strictly after `now`". Both read as reasoning
and neither had a test. The three that were real defects now do, each
mutation-checked against the code before the fix.

## 6. Still open

- **A muted channel is not detected.** The settings row reads
  `areNotificationsEnabled()`, which does not see notifications-on-but-this-
  channel-set-to-None. Checking it needs the channel id, which belongs to `:app`,
  and coupling `:feature:settings` to it for one edge case was declined. The row
  would say the reminder will arrive, and it would not.
- **Quick-complete actions** (PRD §4, OQ-2). Deferred to Phase 1 on the PRD's own
  terms; §4 above has the reasoning.
- **The wake can drift, and nothing measures how far.** Delivery is inside
  WorkManager's flex window, which architecture §7 calls *deliberately inexact* —
  there is no ceiling to state here, only a likelihood, and the threshold check
  in §1 is what stops drift becoming a *wrong* reminder rather than a late one.
  A user who never opens the app relies entirely on the mutual chain in §2.
- **No test proves a notification reaches the shade.** `ReminderCheckTest` pins
  every decision and `ReminderSchedulerTest` pins the scheduling, but the post
  itself is only exercised by hand — [running.md](../running.md) §4 has the
  checks. This is the same gap the widget has for *"a write in the app moves the
  widget"*, and for the same reason: the framework is the part not under test.
- **The reminder does not survive a cleared app.** `pm clear` or an uninstall
  removes the WorkManager database along with everything else; the next launch
  re-arms both wakes. Worth knowing when a manual check appears to fail for no
  reason — and `make itest` does exactly this (running.md §4).
