# The widget: today's habits on the home screen

Companion to [the PRD](../prd.md) §4, §6.1 and OQ-5, and to
[the architecture](../architecture.md) §2, §4 and §8. The PRD specifies a
capability — *"Android home-screen widget: today's habits, tap to complete
without opening the app"* — and leaves what it draws as an open question. This
document is where both are decided.

**Status:** decided and built 2026-08-21, as `:widget`. The **end-of-day
reminder is deliberately a separate step**; §4 and §6 say what that step
inherits.

Written after the screen, like [habits.md](habits.md) and
[settings.md](settings.md). What was genuinely decided: what the widget shows
(§2, which settles OQ-5), what a tap does (§3), why the render is a snapshot and
the tap is not (§4), and what the module does to the app's manifest (§5).

---

## 1. Why this is the step that closes PRD §6.1

PRD §6.1's first success criterion is *"logging < 5 seconds: widget or
notification action, one tap"*. Both halves of that "or" were unbuilt, so the
criterion had nothing behind it at all. The widget is the half that needs no
permission, so it is the half that goes first.

It is also the app's first surface that is not a screen. Everything before this
was reached through `MainActivity`; a widget is drawn by the launcher, from a
process that may not be running, and it cannot observe the database. That is
what §4 is about.

## 2. Minimal: a name and a checkbox. This settles OQ-5

**PRD OQ-5** asks: *"Should the widget show streaks or stay minimal (just
checkboxes)?"* — and **§6.6 answers the other way**, listing *"streak
visibility everywhere it motivates: Today view, widget, habit detail"*. The two
have contradicted each other since they were written.

**Decided: minimal.** One row per habit, its name, and a checkbox. No streak, no
week count, no mascot.

Why, in the order the reasons mattered:

1. **A streak is the number most likely to be wrong on a widget.** A streak
   reaches zero with no new event — nobody does anything, the day turns, the run
   is gone. So it is the one value whose staleness is not bounded by user
   inaction, on the one surface with no live query (§4). A wrong checkbox is a
   redraw behind; a wrong streak is a demotivating lie.
2. **Width is rows.** A streak column costs horizontal space on the smallest
   widget, which is the size that serves the "one tap" claim best.
3. **§6.6's "everywhere" is about the surfaces that motivate**, and the two
   in-app ones are where a streak is read deliberately rather than glanced at.

So §6.6 is **narrowed** rather than contradicted: streaks live in the Today view
and (when it is built) habit detail. `WidgetRow` has no streak field, which is
what stops this decision being undone by an accident of what was in scope.

Revisit when Momo has real art (PRD OQ-4) — a mascot on the widget is a
different proposition from a number, and it is the thing most likely to make a
larger widget size worth designing.

## 3. A tap toggles, exactly as the screen does

Tapping a row completes the habit. Tapping a completed row undoes it.

The alternative considered was complete-only, with a second tap opening the app.
Rejected: it gives the widget a rule the screen does not have. `TodayScreen`
toggles, so a user who has learned the app already knows what a tap does here,
and a mis-tap on a home screen — which is a place people tap by accident — is
recoverable where it happened. `undoCompletion` already tombstones every live
add for the cell, so undo is meaningful even after a merge, and completions are
idempotent per logical date, so a double tap cannot double-count.

**What a tap does not do:** open the app, show a message, or report a rejection.
There is nowhere to put one. §4 says what that costs and what is done about it.

## 4. The render is a snapshot; the tap re-reads. This is the load-bearing part

Glance widgets **cannot observe Room** (architecture §4). Room's
`InvalidationTracker` is the only data-change notifier in this app, and a widget
is not a subscriber. Two consequences, and they pull in opposite directions.

**Keeping it current is a push.** `ProjectionListener` is called after the
projection transaction commits, and `:widget` binds the implementation that
tells Glance to redraw. So an in-app tap updates the home screen. The interface
is declared in `:core:data` and implemented in `:widget` because the literal
reading of architecture §4 — "the repository triggers a `GlanceAppWidget`
update" — would put Glance in `:core:data` and invert the module rule
(`widget → core`, §2). That paragraph has been corrected.

**But not everything that changes the widget is a commit.** A day rollover is
not an event. Nothing is written at the cutoff, so no listener can fire for one,
and a widget left on a launcher overnight will show yesterday's ticks. Two
things follow:

- **Bounded, not fixed:** the provider's `updatePeriodMillis` is 30 minutes —
  also the floor the framework clamps to — so staleness has a ceiling without
  any new dependency. An exact refresh at the boundary wants a scheduled wake,
  which means WorkManager, which arrives with the reminder. **That step should
  add a boundary refresh**; it is the cheapest thing it can do for this one.
- **The tap does not trust the render.** This is the part that matters for
  correctness rather than appearance. A rendered row carries only a habit id;
  the logical date and the completion state both come from a read taken at tap
  time. Had the tap used the drawn date, a stale widget would write a
  completion to *yesterday* — and the 3-day retroactive window (architecture
  §5) **accepts** that rather than rejecting it, so it would be a silent wrong
  answer rather than a refusal. Re-reading means a stale render can mislead
  the eye and never the log.

That is a deliberate difference from `TodayScreen`, where the snapshot is live
through its `Flow` and the date handed to a tap is current by construction —
the rule `HabitRepository.observeToday`'s KDoc states. The widget is the one
caller that cannot rely on it.

**Not `collectAsState`.** Glance's session is started and torn down by the
framework around update requests, so collecting the flow would keep the widget
current only while a session happened to be alive. Explicit push plus a snapshot
read is deterministic; a collected flow is current-if-lucky.

**Failures resolve towards saying so.** Both reads behind the widget can throw —
`SQLiteException` is a `RuntimeException` unrelated to `IOException`, and the
settings store refuses to guess a cutoff. A failed read draws *"Can't read your
habits"*, deliberately distinct from *"No habits yet"*: on a widget those two
look identical if the failure is silent, and drawing an empty list for a broken
database is the same failure-towards-silence the export nudge took three review
rounds to stamp out. A failed *write* is absorbed and the widget re-renders,
because correcting itself is the only report it can make.

## 5. What the module does to the manifest, measured twice

Read from the **merged** manifest (`packaged_manifests`, `--rerun-tasks`), not
from the library's documentation, and compared against the same build with
`:widget` removed.

**Glance requires WorkManager, and this is the load-bearing fact of the whole
step.** `androidx.glance:glance` runs its composition session in
`SessionWorker`, a `CoroutineWorker`, reached from `GlanceAppWidget`'s own
constructor through `SessionManagerImpl`. Every version through 1.3.0-alpha02
declares the dependency, so there is no version to upgrade to. WorkManager's
manifest then contributes four permissions to an app that had none of its own:
`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` and
**`ACCESS_NETWORK_STATE`**.

**The resolution, decided rather than discovered:**

- **`ACCESS_NETWORK_STATE` is removed**, with `tools:node="remove"` in `:app`'s
  manifest. WorkManager asks for it in order to evaluate a *network constraint*
  on a work request, and nothing in this app has one. Keeping it would falsify
  PRD §5 and §7's *"no network permission at MVP (verifiable privacy claim)"* —
  a headline promise — for a capability no code here uses.
- **The other three are kept, deliberately.** WorkManager genuinely wakes the
  device and reschedules after boot; a manifest that hid that would be lying
  about what the app does, which is the opposite of the point. None of the three
  can move data off the device.
- **`INTERNET` is absent**, so the process cannot open a socket at all. That is
  the property §1 is really claiming, and it is unchanged.
- `ManifestPermissionTest` asserts the **exact** set, so anything arriving
  through any library's manifest fails a test rather than being noticed later.
  When the reminder takes WorkManager on purpose, that test is the decision
  point: if a `WorkRequest` ever takes a network constraint, the removal line is
  the first thing that has to go.

**Two corrections that fell out of measuring it:**

- `AndroidManifest.xml`'s comment said *"Deliberately no `<uses-permission>`"*.
  That was **already literally false on `main`** — androidx.core contributes a
  signature-level permission for a non-exported dynamic receiver, and it
  predates the widget. Corrected to say what is true.
- *"One exported component"*, which three security reviews leaned on, was also
  already false: `PreviewActivity` (Compose tooling) and
  `ProfileInstallReceiver` (guarded by `android.permission.DUMP`) were both
  exported before this branch.

### The measurement that was wrong, and why it is worth writing down

The first version of this section said the WorkManager dependency was
*vestigial* — that no class in either Glance artifact referenced `androidx.work`
— and excluded it. That was **false**, and it reached a build comment, this
document and an architecture paragraph before anything caught it.

The harness was broken, not the reasoning: `grep` on this machine is `ugrep`,
which **skips binary files by default**, so grepping `*.class` returned zero
matches for everything. The check ran with **no positive control**. Adding one —
grep for `androidx/glance`, which must appear in Glance's own classes — reported
0 out of 262, which is impossible, and that is what exposed it. With `grep -a`
the real answer is five classes in `glance` core.

The lesson is the one this project keeps relearning, in its sharpest form yet: a
measurement that only ever returns "clean" has not been shown to be capable of
returning "dirty". **Run the control in the same pass, always.**

### The defect that followed, which no JVM test could see

Excluding `androidx.work` compiled cleanly and then failed at runtime:

```
java.lang.NoClassDefFoundError: Failed resolution of: Landroidx/work/CoroutineWorker;
  at androidx.glance.appwidget.GlanceAppWidget.<init>(GlanceAppWidget.kt:57)
  at com.gawi.widget.TodayWidget.<init>
  at com.gawi.widget.GlanceProjectionListener$onProjectionChanged$2
```

`NoClassDefFoundError` is an `Error`, and `GlanceProjectionListener` caught
`Exception` — so it escaped, propagated out of `appendLocked`'s
`NonCancellable` region, and **failed a habit creation that had already been
committed to the log**. On screen the editor simply sat there as though Save
were dead. Same class as the recorded "an `OutOfMemoryError` slips past every
guard in this app".

The guard now catches `Throwable` and rethrows cancellation. That is not
overreach: this listener runs strictly *after* the commit, so there is nothing
left for it to fail, and the only cost of swallowing is a redraw that the update
period and the next write both recover.

**`WriteJourneyTest` found this on its first real run**, and nothing else could
have: every JVM test injects a fake listener, so the real Glance constructor is
never called anywhere else. The instrumented source set paid for itself before
it had a second test.

**Components added.** Exactly one exported and unguarded:
`com.gawi.widget.TodayWidgetReceiver`, and it has to be — an `AppWidgetProvider`
the launcher cannot bind is not a widget. It carries no data in its intents and
every action it accepts is an `AppWidgetManager` broadcast.
`GlanceRemoteViewsService` and `RemoteViewsCompatService` are exported but
guarded by `BIND_REMOTEVIEWS`, which only the system holds. Glance's two
trampoline activities and three receivers, and WorkManager's own components, are
all either `exported="false"` or guarded by `DUMP` / `BIND_JOB_SERVICE`.

## 6. Still open

- **A boundary refresh** (§4). The reminder step brings WorkManager and should
  schedule one at the cutoff. Until then, staleness is bounded by 30 minutes.
- **"A write in the app moves the widget" has no automated test, and one was
  attempted.** The pieces are each covered — `ProjectionListenerTest` proves the
  repository makes the call (mutation-checked), and `WidgetHostTest` proves
  Glance renders for a real host — but nothing proves Glance *acted on the
  push*. A test binding a widget host inside `WriteJourneyTest`, writing through
  the UI and waiting for the widget to follow, was written and **removed**: the
  widget rendered no text at all for the full timeout there, while the same
  binding renders immediately in `WidgetHostTest`, which has no Compose test
  rule. The likely cause is the rule and an `AppWidgetHost` not co-existing, and
  it was not worth more time than that against a check `docs/running.md` already
  covers by hand. Worth retrying from a plain `ActivityScenario` driven by UI
  Automator rather than the Compose rule.
- **The reminder's permissions are a bigger question than they looked.** Taking
  WorkManager deliberately reintroduces the four permissions above, including
  `ACCESS_NETWORK_STATE`, *before* `POST_NOTIFICATIONS` is even considered. So
  that step opens against PRD §5 and not only against the manifest comment.
- **Size variants.** The provider declares no API 31 attributes
  (`targetCellWidth/Height`, `description`, `previewLayout`): minSdk is 29, lint
  reports each as `UnusedAttribute`, and `warningsAsErrors` makes that a failed
  build. Adding them means a `res/xml-v31` variant to keep in step — worth it
  once they buy something.
- **A habit's colour and icon are not drawn.** `HabitPalette` and
  `parseHabitColor` are `:core:ui`, which a Glance tree cannot consume (the
  theme is Compose UI). Drawing them means Glance-side colour parsing, which is
  a duplicate of a rule that already exists — deferred rather than duplicated.
- **The widget is not in the launcher automatically.** Pinning one needs the
  user, or `requestPinAppWidget` from the app. There is no in-app "add the
  widget" affordance and PRD §4 does not ask for one.
