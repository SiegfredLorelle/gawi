# The end-of-day reminder, and the wake it shares

Companion to [the PRD](../prd.md) §4, §5 and §6.1, and to
[the architecture](../architecture.md) §2, §4 and §7. The PRD specifies the
behaviour — *"end-of-day reminder notification if due habits remain incomplete
as the day boundary approaches… silent when everything is done. One reminder max
per day"* — and this document is where the mechanism and the permission are
decided.

**Status:** decided and built 2026-08-21, in `:core:data` (the decision), `:app`
(the workers and the notification) and `:feature:settings` (the permission).
The quick-complete buttons §4 specifies landed with 1.0.0's step 3, adding a
receiver to `:app`; they are the *"notification action"* half of PRD §6.1's
first criterion, which until then only the widget answered.

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
not a preference the user set, so not a `UserSettings` field at all.

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

**And the two directions use different policies.** `ReminderWorker` arms the
rollover with `KEEP`; `RolloverWorker` arms the reminder with `REPLACE`. The
invariant that makes the chain sound is that **at least one direction always
replaces**, so every interleaving makes forward progress.

Getting there took two wrong answers, and both are worth keeping because each
looks right on its own.

**`REPLACE` on both sides** lost a day. A reminder wake deferred past the cutoff —
device off overnight — runs late, correctly decides to stay silent, and then
*destroyed the overdue rollover work that was about to re-arm it*. Nothing was
left under the reminder's name and that whole day had none.

**`KEEP` on both sides** looked like the fix and lost a day in two other
orderings, because `KEEP` no-ops against `RUNNING` as well as `ENQUEUED` —
measured in `EnqueueRunnable`'s bytecode, where the branch after the `KEEP`
comparison tests both states. When both wakes are overdue at once:

- *rollover first:* `armReminder(KEEP)` no-ops against the still-enqueued overdue
  reminder; that reminder then runs, arms only the rollover, and leaves its own
  name empty.
- *concurrently:* each `KEEP` sees the other `RUNNING`, both no-op, and neither
  name has pending work afterwards — the chain is simply dead.

`ReminderScheduler.start()` repairs neither, because the process was started *for*
the workers, so its first emission lands while both are still pending and no-ops
too. Both found in review.

The residual of `REPLACE` on the rollover side is cancelling a reminder run in
flight. That is only reachable when the two wakes coincide, and a reminder run in
that position decides `Silent` anyway — a wake that late is inside the following
logical day, which §1 refuses.

**A prose claim here was also wrong, and the policies no longer depend on it.** An
earlier draft said the other name is *provably* not running, since the reminder
falls strictly inside a logical day and the cutoff ends it — and that a reminder
set equal to the cutoff would leave the two "a whole day apart". Day `D + 1`'s
start *is* day `D`'s boundary, so equal times put both wakes on one instant. §3
prevents that setting now and §1 refuses to act on a stored one, so it is
unreachable rather than merely unlikely.

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

**An edit re-arms only the wake that actually moved.** Replacing both would let a
reminder-time edit cancel a `RolloverWorker` mid-run and lose its streak sweep and
widget push, over a setting the rollover does not depend on. A `reminderTime` edit
moves the reminder alone. A **`dayCutoff` edit
moves both**, and that is the half worth stating: the cutoff is obviously the
rollover's own instant, and it is *also* an input to `reminderOn`, which uses it to
decide whether the reminder falls on today's calendar date or tomorrow's.

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

**The permission is not the on/off switch, and this reverses what this section
used to say.** It refused a `UserSettings` toggle on the ground that a second
source of truth for one behaviour is one more thing to keep in sync. The
canvas-fidelity pass overruled it, and the reason the refusal missed is that
these are not two sources of truth for one behaviour — they answer different
questions. A revoked permission is an **error**: the user asked for a reminder
and the system is preventing it, and the row has to say so. The switch is a
**choice**: the user does not want one, and there is nothing to report
([settings.md](settings.md) §2). Collapsing them made the only way to stop the
notification an act performed outside the app, in a screen this app does not
own, which reads as the app having no answer.

**Off is read at the wake, not at the arming, and not at the post.** The switch
is checked inside `evaluate()`, which returns `Silent` — the same shape the
threshold re-check above already has. The other two placements are both wrong
and each for a recorded reason. Not arming the work at all would break the
mutual chain §2 describes, where `ReminderWorker` arms the rollover: the
rollover wake would lose one of its two arming links, which is the failure §2
already lost a day to. Refusing at post time is too late, because `evaluate()`
stamps the journal *before* anything is posted, so a switched-off day would
consume its own reminder. Read at the wake, a silenced day leaves the journal
unstamped and both wakes armed, and flipping the switch back costs nothing.

**It is a fifth `UserSettings` field, and the argument §8 of settings.md makes
against adding fields does not reach it.** That argument is about the Today
query's `(settings, logical date)` dedupe, and what it refuses is a field that
changes *on every export* — one that would make the dedupe miss and restart the
streak sweep under an open screen. A boolean a user flips by hand re-emits that
query once, when they flip it, which is what it is for.

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
for good. Using it before the launch would send every first-time user straight
into system settings instead of showing them the dialog: the single commonest
path through the row. By the time the callback
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

The small icon is a **vector in this app's own resources**, not a framework
drawable. It started as `android.R.drawable.ic_popup_reminder` and that was wrong:
since API 21 a small icon is drawn from its *alpha channel only* and tinted by the
system, so a legacy full-colour bitmap renders as a silhouette at best and a filled
blob at worst — and a platform `android.R.drawable` is not a stable appearance
contract across API levels or OEM skins. Raised in PR review. Since 2026-08-25 the
vector is **the launcher mark's silhouette**, which is what holds at 24 dp
([momo.md](momo.md) §4). Since visual-identity §7.1 that mark is one gill
cluster rather than a face, so step 4 redraws this file as three dots and drops
the `evenOdd` eye cut-outs — there is no longer a face to carry through one
colour, which makes it a simpler file than the one it replaces.

`IMPORTANCE_DEFAULT`, which makes a sound. A habit nudge that arrives silently is
one the user finds the next morning, which is the whole point missed;
`IMPORTANCE_HIGH` would be an interruption for something that is not urgent. Both
are the user's to change in the channel's own settings.

**Up to three action buttons, one per outstanding habit, and none at four or
more.** Quick-complete was PRD §4's explicit stretch goal, *"allowed to slip to
Phase 1; documented so it isn't lost"*, and it carried OQ-2 with it: Android caps
three action buttons, and what to show when more than three habits remain is a
real design question. PRD §8 answered it and PRD §5's step 3 built it. Each
button writes a completion; at four or more there are none and the tap opens
Today.

**The button is labelled with the habit's name and nothing else.** The position
is the verb — an action row under a reminder is not read as a list — and a
*"Done: "* prefix would only make a long name truncate sooner. What it *speaks*
cannot be the bare name, though: read alone, *"Read"* is an instruction rather
than a habit.

**A notification action has no content description, and that is an API fact
rather than an oversight.** `NotificationCompat.Action.Builder` offers none —
the title is both what is drawn and what a screen reader announces — so the
split the Today row makes with `onClick(label = …)` cannot be made the same way
here. It is made inside the title instead: the name carries a `TtsSpan` whose
text is *"Complete Read"*, which is a `ParcelableSpan` and so survives the trip
to the shade. `ReminderNotifierTest` pins that the span is set; whether the
platform keeps it on an action title is a device check
([running.md](../running.md) §4), and the fallback if it does not is the bare
name.

**The button carries the date; it must never resolve one when tapped.** The
reminder fires before the day cutoff and the notification survives the night,
so a user who taps it over breakfast would write a completion against *today*
for a habit they owed *yesterday*. The `PendingIntent` therefore carries the
logical date the notification was posted for, which is the same guard the Today
row already keeps — *"the date travels with the row, so a tap writes to the day
it was drawn for rather than to one resolved a moment later"*. It is the one
part of this that is a correctness rule rather than a design choice.

**Which is why `ToggleHabitAction` cannot be reused, though it looks like the
same tap.** The widget resolves the date at tap time on purpose, because a
Glance session is short and the *drawn* date is the value most likely to be
stale. A notification is the opposite case: it outlives its own day, so the
drawn date is the trustworthy one and *now* is what has gone stale. The two
surfaces need opposite rules, and the widget's own KDoc argues for the one it
has.

**And the button completes; it never toggles.** `ToggleHabitAction` undoes a
completion when the row is already done, which on a widget is a mis-tap the
user can fix where it happened. Here the row was drawn hours ago: a habit ticked
in the app since then would be *un*-ticked by a button still labelled with its
name, in a surface the user is not looking at. So each button is an idempotent
completion for its carried date — pressing it twice writes what pressing it once
did, and nothing removes a completion from the shade.

**A tap re-posts the notification rather than dismissing it.** The count in the
body and the buttons under it are the same fact twice, so completing one habit
out of three has to move both or the shade starts disagreeing with itself while
the user is still looking at it. The fixed id already makes a re-post replace
rather than stack, which is what makes this cheap; when the last outstanding
habit goes, the notification goes with it, because there is nothing left for it
to say.

**The re-post counts against the carried date too, and does not go through
`evaluate()`.** Both follow from the paragraph above. Recounting against *today*
after the cutoff would contradict the buttons it is posted beside, so the body
counts the same day the buttons write to — and past midnight it is no longer
describing "today". The copy survives that rather than changing: knowing it is
stale needs `today`, and resolving `today` in the tap path is the one thing this
section rules out. Going through `evaluate()` would be worse than wrong: that is
the function that stamps `ReminderJournal` (§1), so a quick-complete tap after
the cutoff would mark the new day as already reminded and silence that evening's
real reminder — §1's late-wake case, reached from a new direction. The re-post
posts directly under the fixed id.

**The button carries the candidates; the log says which are left.** Each
`PendingIntent` holds the whole outstanding list as it stood at post time, and a
tap asks one question of the log before re-posting: of those habits, which
already have a completion on the carried date. Nothing re-derives what is
*outstanding* — that needs the schedule, and `HabitRepository.observeToday` only
answers for the *current* logical date, so it would mean a second way to decide
outstanding beside `Mascot.isOutstanding`, for a date nothing else asks about.
Asking which are already done is a fact rather than a judgement, and
`observeCompletionDatesByHabit` answers it for all of them in one query with the
carried date at both ends. The tap still resolves no clock: the date goes in, it
is never worked out.

**That is also what makes two taps in flight safe.** A second button's
`PendingIntent` was filled in when the notification was posted, so its list
predates the first tap's write — which is why *ordering the two is not enough on
its own*, and the obvious fix is the wrong one. Reading the day back is what
settles it; a lock around the tap is what guarantees the read happens after the
write it should see. The same read closes a smaller thing for free: a habit
completed in the app since the post no longer keeps a button in the shade.

**A refused write leaves the shade exactly as it is.** The carried date is the
one thing that can put a tap outside architecture §5's three-day retroactive
window — a notification that survived a long enough gap — and that window is a
command rule, so the domain refuses it. Dropping the button anyway would report
a completion that was never written, which is the same class of silent wrong
answer the carried date exists to prevent, arrived at from the far end. So the
notification is left alone: the button is still there, and it still owes
something.

**So it does not consult the off switch either, and that needs saying rather
than inheriting.** §3 rules out a post-time switch check for one reason — that
`evaluate()` stamps the journal first, so a silenced day would consume its own
reminder. A re-post stamps nothing, so that reason does not reach it and the
answer has to be argued here. It is this: the switch gates the end-of-day
*nudge*, and a re-post is not one. It is the acknowledgement of a tap the user
made a moment ago on a notification in front of them, and the window is real —
being nudged is exactly what sends somebody to the setting, so the switch can
go off between the post and the tap. Suppressing the re-post there would leave
the count contradicting the button they just pressed, which is the state the
paragraph above exists to prevent. **The permission is still honoured**, and for
free: `ReminderNotifier.post` and `repost` share one body whose first statement
is the check, and the notification's id is private to that class, so a re-post
goes through the same door.

**A re-post must not re-alert, and the flag that stops it belongs to the
re-post alone.** The channel is `IMPORTANCE_DEFAULT`, which makes a sound, so
without `setOnlyAlertOnce` every `notify()` on the fixed id would buzz again.
One tap becoming a second alert is wrong on its own, and it is the version of
the paragraph above that would feel indefensible: a user who has just switched
the reminder off would be *sounded at* for completing a habit.

Setting it on every post would be the wrong fix, though, and it is the obvious
one. The flag suppresses the sound whenever a notification with that id is
*already showing* — and `setAutoCancel` clears this one only on a tap, so a
reminder ignored overnight is still there when the next evening's is posted.
A blanket `setOnlyAlertOnce` would therefore silence tomorrow's real reminder
for anyone who left today's unread. So it is a parameter: the first post
alerts, a re-post replaces its content silently, and
`ReminderNotifierTest` asserts both directions.

**Nothing is ranked, and that is why the cap is where it is.** With four
outstanding there is no non-arbitrary way to choose three, and any rule that did
— most at risk, longest streak, alphabetical — would be the app making a
decision about somebody's day without saying so. Falling back to no buttons is
not a degradation; it is the honest answer, and the tap still reaches the screen
that shows all of them.

Deep-linking is out for a smaller reason: `:app` owns the navigation graph and
Today is already the start destination, so a route would be a second way to
express the same landing place, free to disagree with the graph.

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

### What the second review round found

Three reviewers on the PR. **Four more valid findings, and one of them was a hole
in a fix made during the round before it** — the switch from `REPLACE` to `KEEP`
closed the bug it aimed at and opened two orderings that lose a day's reminder,
because `KEEP` no-ops against `RUNNING` as well as `ENQUEUED`. §2 has the resolution
and the invariant that replaced the reasoning.

The other three: the workers' arming is not unconditional (cancellation skips it,
and a KDoc said otherwise); `ReminderCheck` was the one caller of
`Mascot.isOutstanding` trusting the query to filter archived, which `TodayUiMapper`
explicitly declines to do; and a settings edit replaced both wakes when only one
had moved. Plus the notification icon in §4, and two manual checks in
`docs/running.md` §4 that would have passed without testing anything.

**Two findings were rejected**, recorded so they are not re-litigated: a
markdownlint MD046 report (markdownlint is not in this repo's toolchain, and the
blocks in question are already fenced), and a suggestion to bump `androidxCore` to
1.19.0 (that entry is deliberately pinned to the version which already resolves, so
that declaring it changes no resolution — the catalog comment says so).

**And two of the new tests were caught being vacuous before they were trusted**,
which is the habit that matters more than any single finding here. The
two-worker test passed under the bug twice — first because the reminder work never
finished, then because the assertion ran while it was still `RUNNING` — and the
archived-filter test passed with the filter deleted, because `observeToday`'s SQL
makes the guard unreachable through the real repository. Both were rewritten until
the mutation reddened them.

### What the third review round found, and what it missed

**Two findings, both prose, both mine.** One was a self-contradiction: architecture
§7 said "nothing bounds how long after that it runs" and, four lines later, that a
margin could "absorb the delay". The second was a sentence *added in round 2* while
fixing a different overclaim — that a widget lag without forced Doze "means the
rollover wake is not being armed at all", which is one diagnosis for a symptom with
at least three causes. `docs/running.md` now gives the diagnostic instead of the
conclusion.

Also valid and sharper than either: the once-per-day device check claimed to prove
the journal survives a process ending, and never ended the process. The single
thing it added over the JVM test was the single thing it omitted.

**What the round missed, found while fixing it:** §2 above and architecture §7 both
still documented `KEEP` on *both* sides of the chain — the policy round 2's own fix
replaced. Round 2 updated the KDocs and the §5 summary and left the two sections a
reader would actually consult, so the summary pointed at a resolution §2 did not
contain. Three reviewers read that diff and none flagged it, which is worth
recording plainly: **review catches claims that are wrong, not claims that have
quietly gone out of date.** A policy change has to be swept for by name.

### What the first review round found, which was code this time

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
- **The wake can drift, and nothing measures how far.** Delivery is inside
  eligibility rather than delivery — architecture §7 calls it *deliberately
  inexact*, and there is no flex interval to quote because these are one-time
  requests with an initial delay, not periodic work —
  there is no ceiling to state here, only a likelihood, and the threshold check
  in §1 is what stops drift becoming a *wrong* reminder rather than a late one.
  A user who never opens the app relies entirely on the mutual chain in §2.
- **No test proves a notification reaches the shade.** `ReminderCheckTest` pins
  every decision, `ReminderSchedulerTest` the scheduling, and
  `ReminderNotifierTest` what this app asks the platform for — the buttons, the
  cap, the carried extras and the alert flag. What none of them reaches is the
  platform's own half: that the row appears, that the small icon holds at 24 dp,
  that a `TtsSpan` on an action title survives. [running.md](../running.md) §4
  has those by hand. This is the same gap the widget has for *"a write in the app
  moves the widget"*, and for the same reason: the framework is the part not
  under test.
- **Nor does any test pin which wakes an edit re-arms**, which is the one
  property `replaceWhatMoved` exists for. No test calls its `start()` — the
  only caller is `GawiApplication` — `replaceWhatMoved` is private, and
  every case drives `armReminder`/`armRollover` directly with an explicit
  policy, so the closest one asserts that the reminder's own delay moved and
  never that the rollover was left alone. A change making a reminder-time edit
  replace both would reintroduce the defect §5 records, with the suite green.
  The only coverage is the manual cutoff check in
  [running.md](../running.md) §4, which exercises the direction that re-arms
  both rather than the one that must not.
- **The reminder does not survive a cleared app.** `pm clear` or an uninstall
  removes the WorkManager database along with everything else; the next launch
  re-arms both wakes. Worth knowing when a manual check appears to fail for no
  reason — and `make itest` does exactly this (running.md §4).
