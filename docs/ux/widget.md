# The widget: today's habits on the home screen

Companion to [the PRD](../prd.md) §4, §6.1 and OQ-5, and to
[the architecture](../architecture.md) §2, §4 and §8. The PRD specifies a
capability — *"Android home-screen widget: today's habits, tap to complete
without opening the app"* — and leaves what it draws as an open question. This
document is where both are decided.

**Status:** decided and built 2026-08-21, as `:widget`. The **end-of-day
reminder was deliberately a separate step** and shipped the same day; what §4 and
§6 handed it — a scheduled wake at the day boundary — is built, and
[reminder.md](reminder.md) §2 is where it now lives.

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
and habit detail, built 2026-08-21. `WidgetRow` has no streak field, which is
what stops this decision being undone by an accident of what was in scope.

~~Revisit when Momo has real art (PRD OQ-4)~~ — revisited 2026-08-25, when
she had it. **The minimal widget stands, and Momo joins it only when the host
gives it room**: at two cells tall (170 dp and up, `WidgetUiState.kt`) her
resting frame sits above the rows, 72 dp; one cell tall is exactly the widget
this section describes. A mascot on the widget turned out to be the thing that
made a larger *size* worth drawing without making a second *provider* worth
building ([momo.md](momo.md) §4, visual-identity §7.4). No streak either way.

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

- **Shortened, not bounded.** The provider asks to be updated every 30 minutes,
  which is also the floor the framework clamps to — but `updatePeriodMillis` is
  a *minimum interval between requested updates*, not a deadline. The framework
  batches these, defers them under Doze and App Standby, and does not wake the
  device to deliver one. So it shortens the stale window and closes nothing;
  there is no ceiling here to quote, and an earlier draft of this bullet
  claimed one. An exact refresh at the cutoff wants a scheduled wake, which
  means a WorkManager **worker** — WorkManager itself is already a dependency,
  because Glance requires it (§5). **The reminder step built that worker**, as
  `RolloverWorker` (reminder.md §2): it wakes at the cutoff, sweeps the streaks
  and pushes `ProjectionListener`, so a widget on a launcher follows the rollover
  without being tapped. The periodic update is still what covers a device that
  denied the wake, so this shortens the stale window a great deal and still does
  not bound it.
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

**What re-reading costs, stated plainly: across a rollover the tap's visible
semantics invert.** A session-less widget can draw yesterday's ticks until a
periodic update gets through, which is not a bounded wait. Tap a row it shows
as **ticked** and the fresh read says `completedToday == false` for the new day
— so the tap *adds* a completion where the user meant to undo one, and the box
they tapped stays checked. The log is right and today really is done; the eye
was misled. Calling this "safe" without that sentence, which an earlier draft
of `docs/running.md` did, is only half true.

The alternative considered was passing the drawn state as a second action
parameter and refusing to act when it disagrees with the fresh read. Declined:
it gives the widget a rule the screen does not have, and it turns a tap into a
no-op, so a user chasing a one-tap completion taps twice. Recorded rather than
built, because the trade could reasonably go the other way once there is real
usage to judge it by.

**`collectAsState` *and* the push, which reverses what this section first
said.** The original decision here was a one-shot read plus the explicit push,
argued as "a collected flow is current-if-lucky". That was backwards, and
`/code-review` caught it. Measured against `glance-appwidget-1.1.1` bytecode:
`AppWidgetSession` collects `runGlance` — the thing that invokes the widget's
`provideGlance` — with `collectAsState`, **once per session**. An `update`
arriving while a session is already alive therefore never re-enters
`provideGlance`; it re-reads only the state definition, which this widget does
not use.

So a one-shot read froze the content for the session's lifetime. The concrete
failure: complete a habit in the app and the widget redraws, complete a second
five seconds later and that push lands on the live session, nothing re-reads,
and the home screen shows the first ticked and the second not — until the next
periodic update gets through, which is best-effort rather than a deadline. Two
taps on the widget in quick succession do the same, which reads as a dead
widget.

**The two mechanisms are both needed and cover different cases.** Collecting
`observeToday()` inside the content keeps a *live* session tracking Room,
through the same `InvalidationTracker` every screen uses. `ProjectionListener`
is what starts a session at all when none is alive — the common case, since
sessions are short-lived. Neither alone is sufficient, and the earlier text
asserting one of them was is the kind of claim this project keeps having to
correct: it described what the library was assumed to do rather than what it
does.

**And they do not cover each other everywhere.** `catch` terminates a flow, so a
read that throws would end collection for the life of the session — and the push
cannot repair that, because `update` on a live session never re-enters
`provideGlance`. A screen recovers when its `WhileSubscribed` window lapses and
re-subscribes; nothing does that for a widget. That is why the read carries a
bounded retry (three attempts, 150ms apart) *above* the `catch`, so a transient
failure never reaches the terminal state. A persistent one still lands on
*"Can't read your habits"* and stays until the session ends, which is correct —
there is nothing else to show.

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
  The reminder schedules work of its own as of 2026-08-21 — a worker, not a new
  dependency — and it changed this set by exactly one permission,
  `POST_NOTIFICATIONS`, which is the app's own. Pinning WorkManager to 2.11.2 in
  the same step changed it by nothing, measured separately and *before* the
  permission went in, so that the assertion could be seen failing. If a
  `WorkRequest` ever carries a network constraint, the removal line is still the
  first thing that has to go.

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

```text
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

## 6. The streak widget, and the two decisions it did not get to make

**Built 2026-08-29**, as docs/ux/visual-identity.md §7.4's third surface. Its own
`GlanceAppWidget`, receiver, provider xml and manifest entry — the per-widget cost
that section prices — and almost nothing else new, because `observeToday()`
already served it. §7.4 had priced "a read `observeToday()` does not currently
serve"; `TodayHabit` carries a `StreakSnapshot` and its habit's `Schedule`, which
are both halves of `StreakUi.toUi`, and `TodaySnapshot` carries the logical date.

**It shows every habit, not one number, and that was the argument.** A single
headline streak has to choose a habit, and the only ordering available compares a
count of days against a count of weeks. `StreakUi` is a sealed type specifically
so those two cannot be styled as the same number — so ranking by the integer
contradicts it, and ranking by elapsed days is arithmetically fine and still
misleading, since a weekly habit hit once a week for three weeks has not done more
than a daily habit hit eight days running. There is no ordering here that is both
simple and true, so the widget does not rank: query order, the same rule the Today
widget follows, for the same reason. Both directions were drawn at true size before
the choice.

**It dates its number, and that is not decoration.** §7.1 requires it: a streak
reaches zero with *no new event*, so it is the one value whose staleness is not
bounded by the user doing nothing — on the one surface with no live query. The
line carries a **date, not a clock time**. The number only changes at the day
cutoff, so a fresh-looking `07:14` on a stale render claims a precision the value
does not have, and would anyway be a fact about the last render rather than about
the streak.

That requirement is also what fixed the row count. At 180×110dp with the module's
8dp padding there are 94dp to spend; a 16sp row is about 20dp, so four rows fit
only if the "as of" line goes. It does not go — the rows scroll instead, in a
`LazyColumn` above a pinned footer, and three are visible. A widget you scroll has
weakened its own case for being a widget, and that is the honest cost of the
direction rather than a defect to hide.

**Read-only, deliberately.** No tap target. A widget that wrote would have to
re-read the log first for the reason §4 spends itself on, and nothing here needs
to write, so nothing here takes that on. Adding a tap later means adding an
`ActionCallback` with the same re-read — not passing the drawn date.

**Two costs §7.4 did not price**, both found in the building:

- **`GlanceProjectionListener` names one widget.** A provider left out of it
  still renders, and just stops following in-app writes for the life of a
  session — indistinguishable from a widget nobody placed. It now refreshes
  every declared provider, and `ProjectionRefreshTest` reads the receivers out of
  the merged manifest so a third provider cannot be forgotten in one place while
  being added in two others.
- **`previewLayout` needs a layout, and this module ships none** — its
  `initialLayout` is Glance's own. A Glance tree has no RemoteViews until a
  session composes one, so the preview is a hand-built approximation, in the
  system face rather than Outfit (the picker inflates it; there is no bitmap
  escape there). It is the one place in this module that still reproduces the
  palette by hand, because XML cannot read Kotlin — pinned to `:core:ui` by
  `StreakPreviewColorsTest`, exactly the way `:app`'s window background is.

## 7. The Momo widget, and the Today widget's large body

**Both built 2026-08-29**, closing docs/ux/visual-identity.md §7.4's set of four
surfaces. Both were drawn on the design canvas's page 3 before any of the set was
built; the caption decision below was drawn again on page 9 ("Widget set — round
three") and chosen there, the way §6's direction was.

**Today large is a third body of the same provider, not a fourth provider.**
At 170dp of height *and* 220dp of width the Today widget draws the canvas's
header — Momo on a `primaryContainer` pill, and beside her the mood line over
the **woven day band** — above the same rows it always drew. Tall but narrower
than 220 keeps the face-above-rows body that shipped 2026-08-25; under 170 stays
§2's minimal widget at any width. The width gate is arithmetic rather than
taste: at the provider's 180dp, 164dp of usable width less the 66dp pill and 10dp
of gap leaves 88dp for the copy, and the regenerating line is 47 characters, so
no mood line fits that in two lines of caption type. 220 sits between the
three-cell minimum and the 250dp four-cell placement the canvas drew, and it is
the streak widget's `FULL_MIN_WIDTH` for the same kind of reason.
`LARGE_MIN_WIDTH` in `WidgetUiState.kt` carries the numbers and `WidgetBodyTest`
pins both gates at both edges.

**The band is the rows' own flags, and nothing else.** One segment per habit in
the rows' order, `primary` when today's cell is ticked and `outlineVariant` when
it is not, drawn as flat `Box`es on day/night providers rather than as a bitmap
— a background colour is the one thing `RemoteViews` draws in every scheme
without a raster. Nothing is counted, sorted or capped, so the band cannot say
something the checkboxes beneath it do not; with many habits the segments thin
rather than fold, which at thirty is a texture and still true. The pair of fills
is held to WCAG 1.4.11's 3:1 in `GawiColorSchemeTest` and `WidgetPaletteTest`
(light 3.78, dark 6.34), the way the history grid's two cell fills are, because
the pair is the information. `WidgetMomoTest` asserts the segments by provider
identity in order, so swapping the two colours is a red test rather than a
launcher surprise.

**One reading, and in this body it is the mood line's.** In the face-above-rows
body Momo carries the mood sentence because nothing else says it; in the large
body the sentence is drawn, so she is decorative and the band is decorative, and
TalkBack reads the line once and then the rows (momo.md §5). Her height on the
pill is a second constant, `MomoBitmap.PILL_HEIGHT_DP`, for the reason the first
one is a constant: the bitmap's cost must not follow a host's idea of "large".

**The Momo widget is the fourth provider, and the cheapest to keep honest.**
Two by two, 110dp square, her ground the tank colour — `primaryContainer`, flat,
because a `RemoteViews` background is one colour and flat was decided anyway —
and under the resting frame **one word**: *thriving*, *pottering*, *worried*,
*regrowing*. Mood only: no rows, no number, so nothing here can rot and nothing
here needs §6's "as of" line or §4's re-reading tap. The habitat's weeds and
bubbles stay Today's own (momo.md §4); the canvas did not draw them here and
nothing about them crosses the widget edge.

**The caption was the open call, and page 9 settled it.** Three were drawn at
true size for all four moods in both schemes: one word, the full `widget_mood_*`
sentence, and no caption. The sentence reuses strings but its longest line clips
on the second line at 94dp of usable width, and Momo has to shrink to 52dp to
make the room; no caption leaves a greyscale viewer with only an expression to
read. One word costs four new strings and a fifth mood vocabulary to keep in
step with the sentences, and that was judged the cheaper cost. TalkBack does not
read the word: the face carries the full sentence once, and the word is
decorative — so the widget never says "Momo is pottering about. pottering." With
no habits the roles swap, the way the Today widget's do: the no-habits copy is
drawn and read, and the face is decorative. A failed read draws the failure copy
and no face, because nothing was read.

**What the palette gained, and what the edge did not.** Three roles joined
`GawiRole` in `:core:ui`: `PrimaryContainer` (Momo's ground, the pill),
`OnPrimaryContainer` (the one ink drawn on it — light 10.10:1, dark 6.37:1) and
`OutlineVariant` (an outstanding segment). `WidgetPalette` names them
`momoGround`, `momoCaption`, `bandWoven` and `bandOutstanding`; the first two are
grounds and darken at night, which `WidgetPaletteTest` now distinguishes from the
inks that lighten. The canvas's page 3 drew the caption in `onSurface`, which
predates the role list; `onPrimaryContainer` is the role that pairs with that
ground and it is what ships. The `:core:ui` edge still carries four things
(architecture §2) — the role list grew, the list of things did not.

**The preview cannot show her face.** The Momo widget's API 31 picker preview
is its ground and its word in the system face, because there is no drawable of
Momo in `:widget` — she is `drawMomo` rasterised at render time — and a
hand-drawn copy for the picker would be the fifth copy of the geometry that
`MomoBitmap` exists to avoid. The description names her instead. Its two
colours join the same hand-copied list `WidgetPreviewColorsTest` pins
(`StreakPreviewColorsTest` until this day).

**Every place a provider has to be named was named**: the manifest,
`refreshedWidgets()` (a provider missing there freezes for the life of a session,
§6), `ProjectionRefreshTest`, `WidgetHostBinding`, and a `res/xml-v31` variant
that repeats every base attribute. `MomoWidgetHostTest` binds it to a real host
and reads the 2×2 back off `AppWidgetProviderInfo`; `WidgetHostTest` now also
tells the Today provider it is 250×200dp and waits for the mood line, which is
the one string only the large body emits. Neither has been placed on a launcher
yet — docs/running.md §4 has the boxes.

## 8. Still open

- ~~**A boundary refresh** (§4).~~ **Built 2026-08-21** with the reminder, as
  `RolloverWorker` (reminder.md §2). A widget on a launcher now follows the day
  rollover without being tapped. What has not changed is that this is still
  best-effort rather than a deadline — a wake WorkManager defers is a redraw that
  arrives late, and the provider's periodic update remains the only other thing
  shortening the window. There is still no ceiling to state here, only a much
  better likelihood.
- ~~**A settings edit is not an event either.**~~ **Built the same day, and by
  the same mechanism**, which is why the two were listed together. Changing the
  **day cutoff** changes the logical date and therefore every `completedToday`
  without writing anything to the log, so no `ProjectionListener` push can fire
  for it (found by `/code-review`). `ReminderScheduler` collects
  `SettingsSource.observe()` and re-arms both wakes when the cutoff or the
  reminder time moves, so a cutoff edit re-schedules the boundary refresh along
  with it — reminder.md §2. The gap that remains is the interval *between* the
  edit and the next wake, which is the same best-effort caveat as above.
- **`glance-appwidget-testing` was declined, and then taken when its own
  condition came true.** PR review first suggested it for pinning what the
  widget draws, and it was not taken: `Message` resolves its copy through
  `LocalContext.current.getString(...)`, so the Glance unit harness would need
  Robolectric or resource plumbing before it could assert a string at all, and
  it brings ten transitive artifacts. The thing worth pinning then — *which*
  state draws *which* copy — became a pure `WidgetContent.body()` tested with
  plain JUnit, in the shape `TodayUiMapper` and
  `TodayMessage(@StringRes val text: Int)` already set. That bullet ended
  "revisit only if the *rendering* itself ever needs pinning", and it did: the
  widget drew black text on a dark background for a whole phase, because
  Glance's default text colour is not theme-aware while the container's
  background is. Nothing asserting on `body()` could see that. So the harness
  and Robolectric are both in `widget/build.gradle.kts` now, and
  `WidgetTextColourTest` renders with them — still the only test in the module
  that renders rather than decides. **This bullet said "considered and declined"
  for longer than it was true**; a condition a document sets for itself is worth
  re-reading when it is met.
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
- **What the reminder had left to answer, and what it answered.** *Resolved
  2026-08-21 — see reminder.md §3 and §5.* `POST_NOTIFICATIONS` went in as the
  app's first runtime permission, requested from the settings reminder row; no
  `WorkRequest` took a network constraint, so the `tools:node="remove"` line
  stayed. The rest of this bullet is the record of getting there. An earlier draft
  bullet said taking WorkManager would "reintroduce the four permissions"; that
  was left over from believing it had been excluded, and it is wrong twice.
  WorkManager is **already here**, so `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and
  `FOREGROUND_SERVICE` are already in the merged manifest, and
  `ACCESS_NETWORK_STATE` is already removed. What is genuinely open for that
  step is `POST_NOTIFICATIONS` — the app's first runtime permission — and one
  specific risk: a `WorkRequest` that takes a **network constraint** would make
  WorkManager need `ACCESS_NETWORK_STATE` again and force the
  `tools:node="remove"` line out. `ManifestPermissionTest` is the tripwire for
  exactly that.
- ~~**Size variants.** The provider declares no API 31 attributes.~~ **Half
  closed 2026-08-29.** The streak widget carries them, in a `res/xml-v31`
  variant beside its base file (§6): `targetCellWidth/Height` so a fresh
  placement is three cells by two rather than a guess off `minWidth`,
  `description` so the picker says what the widget is, and `previewLayout`. The
  Today widget still declares none, because nothing has yet asked it to — the
  reasoning below stands for it: minSdk is 29, lint reports each attribute as
  `UnusedAttribute`, and `warningsAsErrors` makes that a failed build. What the
  streak widget's variant proved is the cost: the `-v31` file **replaces** the
  base one rather than merging with it, so every attribute has to be repeated and
  the two kept in step by hand.

  **The other half, stated because "half closed" does not say which half.** On
  API 29 and 30 the streak widget's picker entry has no preview *and* no
  description: `previewLayout` is API 31, and neither provider declares
  `previewImage`, which is API 11 and so would be legal in the base file. The
  entry falls back to the launcher icon and its label alone. Deliberate for now
  rather than overlooked — a preview image is a drawn asset, so it belongs on the
  design canvas before it belongs in `res/` — but it means the platform range
  where the picker says least is exactly the one with no preview at all.
- **A habit's colour and icon are not drawn.** `HabitPalette` and
  `parseHabitColor` are `:core:ui`, which a Glance tree cannot consume (the
  theme is Compose UI). Drawing them means Glance-side colour parsing, which is
  a duplicate of a rule that already exists — deferred rather than duplicated.
- **`BIND_APPWIDGET` on the receiver, offered by `/security-review` and not
  taken.** The review found no vulnerability on this branch, and enumerated
  what a third-party app can actually drive against the exported receiver by
  reading Glance's own `onReceive`: a forced redraw, and `onDeleted` clearing
  Glance's per-widget scratch preferences. **No habit data is readable and
  nothing reaches the event log** — the attacker-supplied widget ids are
  useless, because `AppWidgetManager` enforces ids against the calling package,
  and the lambda-trigger path resolves a key this widget never registers (it
  uses `actionRunCallback`, so it has zero lambdas). Adding
  `android:permission="android.permission.BIND_APPWIDGET"` would block those
  pokes, since the framework sender holds it. Recorded rather than applied: the
  review classified it as hardening rather than a fix, and putting a permission
  on a provider risks a launcher-compatibility regression that one emulator
  cannot rule out. Cheap to revisit — it is one attribute.
- **The widget is not in the launcher automatically.** Pinning one needs the
  user, or `requestPinAppWidget` from the app. There is no in-app "add the
  widget" affordance and PRD §4 does not ask for one.
- ~~**Whether this widget can ever draw the app's own typeface.**~~ **Answered
  on 2026-08-24: not as a font — and on 2026-08-25: yes, as pixels.** The route
  that failed was not Glance's typed API, which has never offered anything but
  four generic family names, but a hand-written layout inside
  `AndroidRemoteViews` — `RemoteViews` are inflated against our package's
  resources, so `android:fontFamily="@font/…"` looked like it should resolve.
  Measured on a launcher, it does not: the attribute is honoured for a built-in
  family name and a bundled font resource is dropped **silently**, in both
  spellings a font resource can take. That measurement stands. What did not
  stand was the sentence that followed it here — "bitmap text is the only escape
  and is not worth it for a checkbox list" — which was reversed the next day:
  `BitmapText.kt` lays each name out in Outfit with `StaticLayout`, draws it
  white, and a Glance `Image` carries it with `ColorFilter.tint`, taking the ink
  from `WidgetPalette` since 2026-08-28. docs/ux/visual-identity.md §2 has the
  numbers, the controls, and what the bitmap route costs once built. Two of
  those costs belong to this module's design and are recorded here as well:
  - **Font scale follows at the next render, not immediately.** Glance
    recomposes on a locale change and not on a configuration change, so a scale
    change lands with the next write, rollover or 30-minute update — the same
    latency §4 accepts for a day rollover.
  - **On API 29–30 every colour the widget draws is resolved in our process**,
    at translation time, because below 31 Glance hands a launcher a literal
    rather than a colour resource for an image tint or a compound button. This
    bullet used to reason from that to "the whole widget goes stale together —
    no new failure shape", and **measured on API 29 and 30 on 2026-08-28 that
    was wrong** — because it was not true of *every* colour, and the one
    exception was the one that mattered. `GlanceTheme`'s `widgetBackground` is
    resource-backed, so it became `setViewBackgroundColorResource` and the
    **host** re-resolved it the instant night mode changed, while the name and
    the glyph kept the values baked at the last render. One side of the contrast
    pair moved and the other two did not, so a toggle left the widget illegible
    rather than stale: the name fell to 1.31:1 against its own ground and the
    checkbox to 1.60:1. A second defect fell out of the same measurement, which
    no toggle was needed to see — the glyph was below the floor in dark mode
    *even freshly rendered*, at 2.91:1 checked and 1.60:1 unchecked, because it
    was taking the platform accent against a background this module chose.
  - **Fixed on 2026-08-28**, by giving all three colours one kind of provider —
    a day/night pair from `WidgetPalette` (docs/ux/visual-identity.md §7.4), so
    they take the same translation path and cannot disagree. Below 31 all three
    now resolve here at the same instant, from the same configuration, so **a
    toggle leaves the widget stale together and readable**, which is the shape
    this bullet predicted before it was measured; it repairs at the next write,
    rollover or 30-minute update, exactly as §4's latency says. Measured after
    the fix on emulators of both levels and identical on each: the name at
    16.59:1 in light and 14.82:1 in dark, the glyph at 5.56:1 checked and
    5.18:1 unchecked in light, 10.44:1 and 5.31:1 in dark, and unchanged across
    a toggle in either direction. One cost, recorded rather than hidden: the
    light-mode checked glyph falls from 18.43:1 to 5.56:1, because it used to be
    pure black by accident and is now the brand teal by choice. **Above API 31
    nothing regressed** — the host is handed a day/night pair for each colour, so
    the whole widget still follows a toggle within about two seconds with no
    render at all, measured on API 37 on 2026-08-28.
