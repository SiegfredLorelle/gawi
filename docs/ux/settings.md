# Settings: the three preferences, and the fourth that is not one

Companion to [the PRD](../prd.md) §5 and §7, and to
[the architecture](../architecture.md) §2, §3 and §5. The PRD specifies these
as capabilities and never as a screen — *"configurable: day boundary time, week
start day (default Monday), reminder time, timezone behavior"* is the whole of
it — so this document is where the screen those four words imply is decided.

**Status:** decided and built 2026-08-20, with `:feature:settings`. **Export and
import** landed the same day, as a labelled section below the three settings
(§6). **The 30-day nudge landed 2026-08-21** and turned the export row into a
`SettingRow` with a real stored value (§6). **The CSV of completions landed
2026-08-21** as a third row in that section, which completes PRD §5's data row
(§6).

Written after the screens, like [habits.md](habits.md) and unlike
[today-view.md](today-view.md). Little here was open: the fields are fixed by
`UserSettings`, and there is only one of them a user can get wrong. What was
genuinely decided is the missing fourth setting (§1), what the cutoff copy has
to admit (§2), and where the gear points (§4).

---

## 1. Three settings, not the PRD's four

`UserSettings` holds `dayCutoff`, `weekStart` and `reminderTime`. The PRD's
fourth, **timezone behaviour, is deliberately absent** — from the data type as
much as from this screen.

It is absent because it has exactly one value. The behaviour is "use the device
zone", which `DeviceClock` supplies per call, on every call. A control offering
one option is not a setting; it is a claim that something is configurable, and a
user who opens it looking for a fix for a travel problem would find nothing and
learn nothing. `UserSettings`' own KDoc has said this since it was written, and
this section is where it stops being only a code comment.

Revisit if a second timezone policy is ever wanted — "pin my habits to the zone
I created them in" is the plausible one, and it is a real feature with real
consequences for logical dates, not a preference.

## 2. Every row says what it changes, and the cutoff says what it does not

Each row carries a line of explanation under the value. Not a help icon, not a
first-run tour: all three of these change how the app counts a day or a week,
and a reader who has to go looking for that will not go.

The day cutoff's line is the one that matters, because what a reader would
assume is wrong. Moving the cutoff **does not re-file anything already logged**.
A completion stores the logical date it was written under (architecture §5) and
replay never re-buckets it, so changing this setting is prospective only. A
screen that let someone set the cutoff to 03:00 expecting last night's 01:00 tap
to move would be lying by omission.

Week start is **not the same rule**, and the copy says something different for
it on purpose. Nothing about a week is stored on an event: weekly bucketing is
computed at read time from the setting, which is why `TodayQueryTest` has a case
called *"changing the week start re-buckets a screen that is already open"*.
Both settings look alike on this screen and behave differently underneath, so
saying "prospective only" over both would be false about one of them.

The reminder line names both things the time does, because as of 2026-08-21
there are two: the mascot's `nearBoundary` mood
([today-view.md](today-view.md) §4) and the end-of-day notification
([reminder.md](reminder.md)). It used to admit the second was unbuilt, which was
the right copy while it was.

**And the row has a fourth state the other two cannot have: notifications
switched off.** The time is still set, still drawn and still drives the mascot,
so the screen looks entirely correct while the feature it is named for cannot
happen — which is exactly the kind of silent half-working the honest copy above
existed to avoid. So an error line appears under the row, with its own target
that leads to the permission or to system settings.

**One combination is refused**, and it is the first settings write on this screen
that can be: the reminder time may not equal the day cutoff. `reminderOn`
resolves that pair to the logical day's *start* rather than its end, so it is
meaningless rather than merely odd — and once the notification existed it meant
one posted at the top of every day, which also used up that day's one reminder.
Refused from **both** rows, since either can create the collision.
[reminder.md](reminder.md) §1 and §3 have the argument, including why
`SettingsMessage`'s KDoc was right that a picker cannot express an invalid time
and wrong that this made refusal impossible.

It is its **own** target and not a state on the row, because the row's tap
already means "change the time" and that stays worth doing while notifications
are off. [reminder.md](reminder.md) §3 has the rest, including why the copy names
no permission and why the escalation is decided in the request callback rather
than before it.

## 3. Pick, then confirm

Both time rows and the week-start row open a dialog holding the half-made
choice, and hand it back only on confirm. Cancel always means nothing changed.

The alternative — writing on every tick of a picker — was rejected for a reason
specific to this screen: the store is the single source of truth for what is
drawn, so a write is what redraws the row. Writing continuously would mean the
day boundary passing through every value between 21:00 and 03:00 on the way
there, and the day cutoff is not an inert number. It is joined into the live
query that decides which rows Today is showing.

This is also why nothing half-picked lives in the ViewModel. The mid-edit value
belongs to the dialog and dies with it, which is the same shape `ScheduleUi`
takes in the habit editor — and it is simpler here, because there is no invalid
value to hold. Every point on the clock is a legal cutoff and every day is a
legal week start, so unlike `Schedule.Weekly` there is no domain type waiting to
throw on an out-of-range value.

The system file picker is the Data section's version of this dialog (§6): it
belongs to the platform, and backing out of it returns a null `Uri`, which does
nothing and says nothing.

## 4. The gear moves to what it looks like

Today's app bar had one action, *Manage habits*, drawn as a gear. That was
harmless while it was the only way off the screen. It stops being harmless the
moment a settings destination exists beside it, because the gear is the one
symbol a reader will read as settings.

So the gear now opens settings and manage-habits takes a list glyph. Both are
glyphs with no text — there is no icon pack in this project (the colour scheme
is designed now, but the icon vocabulary is still open:
[visual-identity.md](visual-identity.md) §7.3) — which
means the content description is the *only* thing distinguishing them, to a
screen reader and to a test alike. `settingsButton_isNamedAndLeadsToSettings`
and `todaysAppBarLeadsToSettings` both exist to catch the two being crossed, and
both were mutation-checked.

## 5. Formatting is decided in the mapper, and it is not localised yet

Day names are string resources rather than `DayOfWeek.getDisplayName`. The
tests then assert against the same `R.string` the screen renders, the copy is
translatable in the one place every other string in this app is, and what the
picker reads does not depend on which machine's JVM locale data rendered it.

Times are formatted by a pure function taking the device's 12-or-24-hour flag,
which the Route reads from the platform and passes down. That keeps the decision
in the mapper — where the other decisions are — while leaving both conventions
renderable in a test with no device to set the flag on.

**Two limitations, recorded rather than discovered later.** The formatter uses
`Locale.ROOT`, so the meridiem is always the English `AM`/`PM`. It is
deterministic, which is what stops the test and the screen disagreeing on a
machine set to something else, and this app has no `values-xx` anywhere — so the
day names beside it are English too. The moment a second locale is added, this
is one of the two things that has to change, and the other is every string file.

And the 12-or-24-hour flag is read when the screen composes, not observed. This
is worth stating precisely because the obvious fix does not work: `Configuration`
carries no 12/24-hour field, so keying a `remember` on `LocalConfiguration`
looks like a refresh and is a no-op — flipping the system clock format
broadcasts `ACTION_TIME_CHANGED` and never recreates the activity. Observing it
properly means a `ContentObserver` on `Settings.System.TIME_12_24` or a receiver
for that broadcast. Deferred: the payoff is a screen that re-renders while the
user is changing an Android setting they reached by leaving this app, and the
next thing that recomposes catches up anyway.

## 6. Export and import, and the rows that are not settings

PRD §5 gives this app one recovery path and architecture §6 explains why there
is only one: Auto Backup is off, so nothing else copies the log anywhere, and
the log cannot be rebuilt from the derived tables. That makes the export and
import rows the most consequential thing on the screen and, oddly, the least
setting-like.

This section was written when there were two of them. There are three now, and
the third is deliberately *not* a recovery path — everything below down to "The
CSV of completions" is about the first two, and that subsection is where the
distinction is drawn. Read the two together: the argument here is what makes the
CSV's copy load-bearing rather than decorative.

**They are a section with a heading; the three settings above are not.** The
habit list already made this choice for its archived group — the obvious group
goes unlabelled and only the one that needs saying gets a name. Labelling both
would mean inventing a word for "the three settings", and every candidate
("General", "Preferences") says less than the rows do on their own. No divider
either: a rule and a heading are two devices doing one job, and there is no
`HorizontalDivider` anywhere in this app to be consistent with.

**One row composable, and a nullable value.** A `SettingRow`'s middle line is
`titleMedium` in the primary colour and it means *this is what the setting is set
to*; three rows teach a reader that before they reach the Data section. "Export a copy"
is not a value, and pre-staging the nudge by writing "Last exported: never" there
before anything stored it would have been copy the store cannot back — the one
thing §2 argues against.

The nudge then landed and the convergence went one step further than this section
predicted. It said the export row would *become* a `SettingRow` and that
`ActionRow` would keep the import row; in the event `ActionRow` was deleted and
`SettingRow` took both call sites, with `value: String?`. **The argument above is
what makes that safe rather than what it overrides**: a null draws no middle line
at all, not an empty one, so the import row still puts no action verb where the
three settings above it put a state — and it never will, because nothing an
import leaves behind is a thing to report. What changed is only that keeping two
composables would have meant maintaining one `Column` twice to express one
difference. It also retires half of §7's duplication bullet.

**The nudge is words, not a colour, and it replaces the help line rather than
joining it.** The value line says how long it has been; the help line underneath
says why that matters, once it has been thirty days or once there are events and
no export at all. An alarm-coloured caption is not the "gentle" PRD §5 asks for,
two paragraphs of caption under one row is worse than one, and the precedence has
to put a running export above both — a row writing a file right now has no
business saying there is no file.

**A log with no events in it says nothing at all.** `lastExportedAt` being absent
means two different things and the log decides which: on a fresh install there is
nothing to lose, and a nudge there is a warning about losing nothing; on a log
that holds something it is exactly the case the nudge exists for, and it is
overdue immediately rather than thirty days later. That second signal is why the
stamp is not a `UserSettings` field — see §7.

**There is no "not now".** A nudge that can be dismissed for thirty days is a
nudge that says nothing, and there is no second surface to dismiss it from. The
row that shows it is the row that fixes it.

This used to carry a second reason — that a dismiss action would have needed a
seventh `SettingsActions` property, one past detekt's constructor threshold. That
was never true of this declaration and is now visibly untrue: the class has seven
properties today. §7 has the measurement. The product reason above is the whole
of it and always was.

**The system picker is this section's "pick, then confirm" (§3), so there is no
dialog.** Cancelling it returns a null `Uri` and nothing happens and nothing is
said, which is exactly the rule the other three rows follow. A dialog *before*
the picker would ask someone to confirm a file they have not chosen; a dialog
*after* it could only repeat the name they just tapped. Merge is also
idempotent — importing the same file twice changes nothing the second time — so
the usual "this cannot be undone" justification is half absent.

Be honest about the other half: importing the **wrong** file adds habits the app
can only archive, never delete (`habits.md` §4). That is the case a confirmation
would guard and precisely the case it cannot, because at confirm time nothing
has read the file. What would change this is a dry run in `:core:data` — parse
and count without committing — after which *"128 new, 12 already here — import?"*
is a genuinely different offer and earns a dialog. Recorded rather than built.

**The file name uses the wall-clock date, not the logical one.** The day cutoff
decides which day a completion belongs to (architecture §5); it has no business
deciding what a file is called, and someone exporting at 00:30 under an 03:00
cutoff would otherwise find yesterday's name on today's backup. ISO order so a
folder of them sorts chronologically.

**The import picker's type filter is generous and is never the check.** An
export round-tripped through a cloud drive or a messaging app frequently comes
back typed `application/octet-stream`, and some providers report any `.json` as
`text/plain`, so all three are offered. A filter that hides someone's own backup
from them is a worse failure than one that also shows a few text files. What
makes a file an export is decided by reading it.

**The busy state is a field on the state, where the dialog state is not**, and
the difference is who owns the work. A half-picked time belongs to the dialog
and dies with it — that is the point of §3. An export belongs to
`viewModelScope`: it keeps running across a recomposition, and it is the
coroutine finishing rather than a gesture that ends it, which screen-local state
has no way to hear. This does not weaken §3's "the store is the only source of
truth": that rule is about *committed settings*, and there is no preference
called "an export is running". Both rows disable while either runs, because
exporting midway through an import reads a half-merged log.

### The CSV of completions, and the sentence it needs

**A third row under a heading that argues against it.** Everything above turns
on these rows being the only recovery path there is. The CSV is not one — it is
a view of the completions projection for a spreadsheet, holding no events and no
habit configuration, so nothing can be rebuilt from it. That leaves two ways to
carry the distinction, and **the copy carries it**: `settings_export_csv_help`
says the file is for a spreadsheet, holds no habits or settings, and cannot be
imported back, and points at the row above for the thing that can.

The alternative was splitting the section in two, with the CSV under a heading
of its own. Rejected for the reason the section is named in the first place: it
would mean a one-row group and a word to invent for it ("Spreadsheet"?
"Reports"?), and every candidate says less than the sentence does. A structural
distinction also would not have helped the person this is for — someone who
taps the wrong row learns what it did from the copy either way.

**It gets no value line and no nudge, and those are the same decision twice.**
`ExportJournal` records the JSON export only. A CSV cannot restore anything, so
letting one reset the 30-day warning would silence it for a month on the
strength of a file that would be no use — the exact failure §7's rule about
resolving towards nudging exists to prevent. That is enforced where it cannot be
forgotten rather than remembered: the CSV archive is **not given the journal at
all**, which `CompletionCsvArchiveWiringTest` asserts against the constructor,
and `csvHelp` takes no `ExportRecency`, so the row has no way to show a nudge
even by accident.

**A UTF-8 byte order mark is written, and the import path strips one.** The two
directions disagree about the same three bytes and that is deliberate. Excel
reads a mark-less UTF-8 CSV as the platform's legacy encoding, so a habit named
in anything but ASCII comes out as mojibake in the tool most likely to open the
file; import, meanwhile, is fed by editors that add a mark uninvited, where
refusing the file would be the worse failure. Each path accommodates the reader
it actually has.

**Habit names are free text, and a spreadsheet evaluates a cell starting `=`,
`+`, `-` or `@` as a formula.** This is the one part of the file that is a
security property rather than a formatting choice, and it is reachable by typing
a habit name. Such a field is written with a leading apostrophe, which Excel and
LibreOffice both read as "this cell is text" and neither displays. **The check is
made on the trimmed value**, so a sigil hiding behind a leading space, TAB or CR
is caught too — a spreadsheet skips leading whitespace before deciding what a
cell is. Whitespace on its own is not a sigil and is only quoted, never
apostrophed: ` read` comes out as `" read"` and keeps its space. The sigil set is
therefore exactly `=`, `+`, `-` and `@`. **Not stripped** — a habit honestly
named `-5kg` must survive,
and an export whose justification is the user owning their data cannot quietly
edit it. Be exact about the cost: the bytes do change, and a text editor shows
the apostrophe.

**Three columns, `habit,logical_date,note`.** What a spreadsheet is for is
pivoting by habit and counting by month; a habit id and a schedule would be
columns nobody pivots on and are in the JSON already. `logical_date` rather than
"date" because which day a completion belongs to is decided by the day cutoff
(architecture §5), and calling it a date would quietly claim otherwise.

**The join is a `LEFT JOIN` and archived habits are included.** A completion can
arrive before its `HabitCreated` under a merge, and the projection then keeps the
cell while dropping the habit row — so an inner join would lose a day the user
really logged, and its name falls back to the habit id. Archived habits' history
is still history.

**Two limits of the three-column choice, recorded rather than fixed.** Both were
found by review after the row was built, and neither is a bug in the sense that
the file is wrong — they are consequences of the shape agreed above.

*Two habits with the same name are one series in a pivot.* The `habit` column is
a name, and nothing distinguishes an archived "Read" from a new "Read" created
after it — which is a normal thing to do at a fresh start, and archived habits'
completions are deliberately included. A `habit_id` column would settle it and
was rejected above on the grounds that nobody pivots on an id; that argument is
about pivoting and is not an answer to this. What makes it tolerable for now is
that the ambiguity is visible — two identical labels in the same file — rather
than silent in the data. Revisit if duplicate names turn out to be common in real
use; the fix is a fourth column, not a rename.

*Excel in a `;`-locale opens the file in one column.* Excel takes its delimiter
from the OS list separator rather than from the file, so the same installs the
byte order mark exists to serve are the ones that cannot split the rows. Writing
an `sep=,` first line would fix it for Excel and LibreOffice and break every
strict reader, `pandas` included, so the comma stays and the workaround is the
import dialog. `CompletionCsv`'s KDoc has the reasoning and docs/running.md §4
has the check.

**The success message carries no count.** A number here would govern a noun
("1 completions") and so need a `<plurals>`, whose id cannot travel through
`SettingsMessage` — that resolves through `getString`. The rule this screen
already follows is to write copy that needs no quantity resource. The one thing
a count says that copy cannot is that the file came out empty, and that case has
its own sentence.

**Export speaks on success where a settings write is silent.** §7 notes that a
successful settings change says nothing, because the row redrawing is the
feedback. There is no row to redraw here, and the file went somewhere the app is
never told about, so silence would be indistinguishable from nothing happening.

**There is no `<plurals>`, and the suppression in `strings.xml` is honest.** The
import result is two independent counts in one sentence and a quantity resource
selects on one number. Composing two translated fragments to get around that is
worse than one sentence, so the copy is written to need neither — "1 added, 1
already here" and "128 added, 12 already here" are both grammatical — and the
cases where a count is zero get their own string rather than reading "0 added".

## 7. Still open

- ~~**The CSV of completions is not built.**~~ **Built 2026-08-21**, and this
  bullet's own warning is what shaped it. The JSON is the event log and the only
  thing an import can rebuild from; the CSV is a view of the completions
  projection for a spreadsheet, and it is not a recovery path. §6 has the
  decisions. Two of them exist purely to keep that true where a comment could
  not: the CSV archive is not given `ExportJournal`, so it cannot stamp the
  last-export time, and `csvHelp` takes no `ExportRecency`, so the row cannot
  show the nudge. Both are asserted rather than asked for.

  One sizing assumption in the plan for it was wrong and worth recording.
  `SettingsActions` was expected to need a nested holder for a seventh action,
  because its own KDoc said six was the last that fits under detekt's
  constructor threshold of seven. The threshold is seven, but
  `LongParameterList` sets `ignoreDataClasses` true by default, so the rule
  never applied to that declaration at all — measured, and the KDoc is corrected
  in place. What did bite was `TooManyFunctions`, which caps a *file* at eleven:
  the mapper was at ten, so the Data section's functions moved to
  `SettingsDataMapper.kt`.
- ~~**The 30-day nudge is not built.**~~ **Built 2026-08-21**, and one thing
  this bullet said about it turned out to be wrong in a way worth recording.
  `lastExportedAt` is **not** a fourth `UserSettings` field. Two reasons, both
  written up on `ExportJournal`. `OfflineFirstHabitRepository` dedupes the Today
  query on the `(settings, logical date)` pair, so a `UserSettings` field that
  changed on every export would make that dedupe miss and restart the streak
  sweep under an open screen — the churn `DataStoreSettingsSource`'s
  `distinctUntilChanged` exists to prevent. And the nudge needs a second signal
  `UserSettings` cannot carry at all, whether the log holds anything, so a flow
  of its own was needed either way and folding the stamp into it cost nothing.
  It shares the preferences *file*, which is safe because `update` assigns only
  its own three keys, and two tests pin that in both directions. The three
  preferences are what the user set; when an export last happened is a record of
  what the app did — the same distinction §6 draws about the two rows.
- **`ContentResolverEventArchive` has no behavioural test, and that is now a
  gap rather than a constraint.** The CSV archive got one this round
  (`CompletionCsvArchiveTest`), using `ShadowContentResolver` — which the
  previous reasoning had written off as a shadow "nothing in this project uses".
  True about the repo, wrong about the difficulty: `robolectric` was already on
  `:core:data`'s test classpath and the shadow implements the exact two-argument
  `openOutputStream` both archives call. The JSON path deserves the same test,
  and **the order matters**: with it in place, extracting the shared
  `NonCancellable`/`"wt"`/encode-before-open rules into one writer stops being a
  refactor of untested code. Three things stay device-only either way — the
  `"wt"` mode, `NonCancellable`, and the null-stream guard, none of which the
  shadow can express.

- **The last-export *age* is still counted at emission, so it can go stale on an
  open screen.** A screen left open across midnight shows yesterday's count until
  it re-subscribes, which `WhileSubscribed(5_000)` makes a five-second staleness
  on any real return to the screen. Cheap to fix with a clock tick, and not worth
  one.

  This bullet used to carry a second half that is now fixed, and the way it was
  wrong is worth keeping. It said the log was counted per emission so importing
  into a previously empty log left the row silent "until the flow restarts", and
  judged that not worth fixing alongside the midnight case. **The two are not
  alike.** Midnight needs a screen left open across a day boundary; the import
  case is two taps on first run — fresh install, open Settings, import a backup
  from another phone, and the row that should now say "Never exported" says
  nothing. A reviewer pointed out that `docs/running.md` §4 could not even catch
  it, because its import check runs *after* an export and so starts with a log
  that already has events. The count is a `Flow` off `EventDao` now, so Room's
  invalidation moves the row on any append and the `COUNT(*)` stops re-running on
  unrelated preferences writes. Lesson: "stale until it re-subscribes" was a true
  sentence that made a first-run defect sound like a rounding error.
- **Every failure resolves towards nudging rather than towards silence**, and
  that rule is what `ExportJournal` is arranged around. This value only ever
  decides whether to warn someone they may have no backup, so a wrong warning
  costs an export nobody needed and a wrong silence costs the warning PRD §5
  asked for. Four places apply it, and each one had to be decided separately:

  An **unreadable preferences file** substitutes empty preferences and carries
  on, so the log is still counted and a device with events on it is still
  nudged. Not a blanket catch — anything that is not a read failure is a bug and
  propagates, the same split the settings store makes over the same file. It is
  then guarded a second time in the ViewModel, narrowly, so that no failure in
  that flow can reach `Unavailable`: that branch is correct for settings you
  cannot read and would be absurd here, taking the only recovery path on the
  device off the screen over its own caption.

  A **stamp dated well after today** reads as no stamp at all rather than being
  clamped to nought. A device whose clock was ahead when the export happened and
  correct afterwards leaves a stamp that can never count upwards, so clamping
  would pin the row to "Last exported today" for the life of the install and
  kill the nudge silently. **One day of tolerance**, because the comparison is on
  local dates: without it a backwards clock correction of a few *minutes* across
  midnight — export at 00:03, NTP pulls back to 23:57 — reads as a future stamp
  and flips the row to "Never exported" moments after a successful export. The
  safe direction, but it looks like a bug on the one row whose job is to be
  believed. A day ahead is jitter; two days is a wrong clock.

  This bullet also claimed a whole day of skew was needed and that jitter could
  not trigger it. Neither was true, and the test that pinned it passed only
  because the fake clock defaults to UTC, where the two instants share a date —
  at `+08:00`, the zone this app is actually developed in, it failed. **That is
  the fifth time on this feature**, and the third time in the same bullet, that a
  sentence explaining why something is safe was written before it was. The test
  now sets the zone explicitly, and dropping that line makes it pass trivially,
  which is the check worth keeping.

  A **failed write of the stamp** does not fail the export. `edit` reads before
  it writes, so it throws on the same file the read path degrades over, and
  letting it out would report a complete document with copy that tells the user
  to delete a good backup rather than trust it. The residual is self-healing:
  the old stamp stands, so the row keeps nudging, and the next export tries
  again.

  A **log that cannot be counted** reads as having something to lose. This is the
  one that got away twice, and the reason is a type hierarchy: Room throws
  `SQLiteException`, which is a `RuntimeException` and has no relationship to
  `IOException`, so a corrupt, locked or full database walked straight past the
  preferences guard — which was the only guard, because the count ran inside the
  same `map` — and was answered a layer up with "nothing to lose". The nudge was
  therefore silenced by the failure that makes it most urgent. The two reads have
  separate guards now, because they fail for separate reasons. Answering "there is
  something here" costs an export nobody needed, and on a broken database that
  export fails loudly, which tells the user more than silence would.

  **A garbage stored value** is refused rather than narrowed. `recencyOf` maps
  the day count to an `Int` for the quantity resource, and a stored
  `Long.MIN_VALUE` dates the stamp to the year -292275055 — 106,752,011,854 days,
  which narrows to -622,170,546, a negative age that reads as not-overdue.
  Measured rather than reasoned about. Bounding the count at `Int.MAX_VALUE`
  where the future-stamp check already lives is what makes that narrowing safe by
  construction instead of by luck.

  **This bullet twice claimed a safety the code did not have.** First the
  preferences fallback was a fixed "nothing to lose" while the doc said the
  opposite; then, after that was fixed, the sentence "a device with events on it
  therefore still gets nudged" was left standing while the count could still
  throw past the guard. Both were caught by reviewers, and both were written in
  the same sitting as the code they described. That is four occurrences in this
  project of the same failure mode, and the tell has been identical every time: a
  sentence explaining *why* something is safe, written before it was.
- **A caught read completes the flow, so a recovered status is not picked up
  until the screen re-subscribes.** `Flow.catch` emits and then ends, which
  `combine` treats as a flow that will never speak again — the same property
  `DataStoreSettingsSource.observe()` and `TodayViewModel`'s `catch` already
  have, and carried here rather than fixed for consistency with them. It now
  applies to each half of the status separately: a failed count freezes
  `hasEvents` while the stamp keeps updating, and the reverse. Bounded by
  `WhileSubscribed(5_000)`, and every frozen value is the nudging one, so it
  fails on the safe side. A bounded `retryWhen` is the fix for all of them at
  once.
- **The stamp is written after the output stream closes, and no JVM test covers
  that ordering.** It has to mean "a file landed" rather than "a write was
  attempted", or the nudge goes quiet for thirty days over a document that was
  truncated and never filled. Testing it means substituting a `ContentResolver`,
  which needs a Robolectric shadow that nothing in this project uses yet, and
  `EventLogArchive` is split out from the `Uri` side precisely so the decisions
  are testable without one. Checked on a device instead (docs/running.md §4).
- **Leaving the screen the instant you tap Save can still leave an empty file,
  and the export is tied to the screen's lifetime.** Two of the three ways this
  used to go wrong are fixed and the third is not, so it is worth separating
  them.

  Fixed: the log is read and encoded *before* the document is opened, because
  opening truncates it. A read that throws — SQLite, a full disk — therefore no
  longer empties the file, and no longer replaces a backup the user picked to
  overwrite with nothing. Fixed too: the open, write and close run under
  `NonCancellable`, so a write that has *begun* finishes even if the destination
  is popped underneath it.

  Not fixed: the picker creates the document when the user taps Save, and the
  export only starts when the result reaches the ViewModel. If the destination
  is already leaving at that moment, `viewModelScope` is cancelled and the
  coroutine never runs its body at all — so SAF's freshly created, zero-length
  document is what remains, under a name that reads like a backup.
  `NonCancellable` cannot help here: it protects a region once entered, and this
  never enters one. **Reproduced on a device** by tapping Save and pressing Back
  immediately (two of three attempts). At a realistic log size the window is a
  few milliseconds, which is why a person is unlikely to hit it and an impatient
  one still can.

  Nor is process death survived: a force-stop or a low-memory kill mid-write
  leaves whatever the provider had. That much is bounded rather than dangerous —
  truncated JSON does not parse and `event_count` would not match what follows
  it, so the import path refuses such a file as damaged rather than restoring
  part of it. The same is true of the zero-length file above.

  Closing the rest means running the export on an application-scoped coroutine
  or WorkManager so it does not belong to the screen. That is a real decision
  rather than a line of code: work that outlives the screen it was started from
  can never report its result, and this screen's whole argument for staying
  silent on success is that the row redrawing is the feedback.

  This bullet used to claim the write was already non-cancellable. It was not;
  the doc was describing an intention the code never carried, which a PR
  reviewer caught as a self-contradiction. Recorded because the failure mode —
  a doc that reads like a decision and is actually a wish — is one this project
  has now hit twice, and the second time it was caught by running the thing
  rather than by reading it.
- **The Data section is inside the `Settings` branch**, so a non-IO read
  failure takes the recovery path off the screen along with the settings.
  `Unavailable` is a bug-shaped state that IO cannot produce — `observe()`
  absorbs `IOException` into defaults — so this is tolerable rather than
  invisible, and `unavailable_takesTheDataSectionWithIt` pins it as a decision.
  If it ever becomes reachable in practice, the fix is to draw the section as a
  sibling of the `when` rather than inside it.
- **An import cannot be previewed.** See §6: a dry run in `:core:data` is what
  would make a confirmation dialog worth having.
- **No confirmation that a write landed.** A successful change is silent: the
  row redrawing from the store is the feedback, and a snackbar on every tap
  would be noise. The failure path does speak. Worth revisiting only if the
  redraw ever stops being immediate.
- **A muted channel is not detected.** *Resolved the bigger half of this
  2026-08-21:* the reminder time now has a notification behind it
  ([reminder.md](reminder.md)), and the row admits it when notifications are off.
  What is left is narrower and still real — `areNotificationsEnabled()` does not
  see notifications-on-but-this-channel-set-to-None, so the row would promise a
  reminder that never arrives. Checking it needs the channel id, which belongs to
  `:app`, and coupling this module to it for one edge case was declined. Recorded
  here beside the 12/24-hour gap, which is recorded the same way for the same
  kind of reason.
- **An unreadable preferences file shows the defaults, not an error.**
  `SettingsSource.observe()` absorbs `IOException` into `emptyPreferences()`
  deliberately — a query bound to a guessed cutoff shows the wrong day's rows,
  a dead flow shows none — so this screen would draw midnight, Monday and 21:00
  over a file it could not read. Nothing is silently overwritten by that:
  `DataStore.edit` reads before it writes and throws on the same file, so a
  write fails loudly. But the screen cannot currently tell the user that what
  they are looking at is a guess. Fixing it means `observe()` distinguishing
  "defaulted because absent" from "defaulted because unreadable", which is a
  `:core:data` change and a wider one than it looks.
- **No timezone setting**, per §1. Recorded here so it reads as a decision
  rather than an omission.
- **`GlyphButton` wants a home in `:core:ui`, and this screen made that worse.**
  Five composables now wrap an `IconButton` around a `Text` glyph named by a
  `contentDescription`, and two of them —
  `feature/settings/.../SettingsScreen.kt` and
  `feature/habits/.../HabitListScreen.kt` — are byte-for-byte identical
  including the KDoc. The other three are `ManageHabitsButton` and
  `SettingsButton` in `feature/today/.../TodayScreen.kt` and an un-extracted
  copy in `HabitEditorScreen.kt`; `HabitEditorPickers.kt`'s `StepperButton` is
  the same shape plus an `enabled`. **This module added two of the five**, so
  the duplication is partly this screen's own doing. Architecture §2 names
  `:core:ui` as the home for shared composables and `Notice` is the precedent,
  so the destination is not in question. What is: three of the five live in
  files the settings change never touched, so extracting properly pulls
  `:feature:habits` into a diff that has no other business there. Next
  cleanup rather than this one — and §4's argument about the glyph carrying no
  meaning on its own is written in three places now, which is usually the signal
  that the component wants extracting.

  **Still five as of 2026-08-21.** Habit detail draws two glyph buttons and added
  no sixth: `HabitListScreen.kt`'s copy became `internal` and detail calls it.
  The count is unchanged, the pressure is not — that copy now has two callers,
  so it is the one with the strongest claim on `:core:ui` when this is done.

  **`SectionHeader` is the sixth**, added by the Data section: a bare `Text` at
  `titleSmall`/`onSurfaceVariant` with the same padding as
  `HabitListScreen.kt`'s archived heading. Not identical — that one is a
  `LazyColumn` item and takes no modifier — so it is a second occurrence rather
  than a copy, which is exactly the threshold this bullet exists to track.
  Whenever the glyph button moves to `:core:ui`, this should move with it.

  **The row duplication is gone**, which is the one thing on this backlog the
  nudge actually removed: `ActionRow` and `SettingRow` were the same `Column`
  differing in a middle line and a pair of booleans, and there is now one of
  them with five call sites (§6). The glyph button is untouched and still five.
