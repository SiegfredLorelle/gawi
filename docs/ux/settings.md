# Settings: the three preferences, and the fourth that is not one

Companion to [the PRD](../prd.md) §5 and §7, and to
[the architecture](../architecture.md) §2, §3 and §5. The PRD specifies these
as capabilities and never as a screen — *"configurable: day boundary time, week
start day (default Monday), reminder time, timezone behavior"* is the whole of
it — so this document is where the screen those four words imply is decided.

**Status:** decided and built 2026-08-20, with `:feature:settings`. **Export**
is the part of architecture §2's row for this module that is not built; see §6.

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

The reminder line admits what is not built. The time already feeds the mascot's
`nearBoundary` mood (docs/ux/today-view.md §4), so the setting does something
today — but the end-of-day notification it is named for does not exist yet, and
copy that implied otherwise would be promising a notification that never comes.

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

## 4. The gear moves to what it looks like

Today's app bar had one action, *Manage habits*, drawn as a gear. That was
harmless while it was the only way off the screen. It stops being harmless the
moment a settings destination exists beside it, because the gear is the one
symbol a reader will read as settings.

So the gear now opens settings and manage-habits takes a list glyph. Both are
glyphs with no text — there is no icon pack in this project (PRD OQ-4 is
undesigned, and stock Material is deliberate until Momo has real art) — which
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

## 6. Still open

- **Export is not built**, and it is the rest of this module's job per
  architecture §2. It should not wait: it is the only disaster-recovery path
  there is, `allowBackup` is deliberately off (architecture §6), and the event
  log cannot be reconstructed from anything else. Harmless while the database
  holds scratch data; unrecoverable eighteen days into a streak. PRD §5 also
  wants a gentle in-app nudge when no export has been made for 30 days, which
  is the one piece of this that plausibly belongs on *this* screen rather than
  beside it.
- **No confirmation that a write landed.** A successful change is silent: the
  row redrawing from the store is the feedback, and a snackbar on every tap
  would be noise. The failure path does speak. Worth revisiting only if the
  redraw ever stops being immediate.
- **The reminder time has no notification behind it.** §2's copy is honest
  about that, but honest copy is a stopgap for a setting that is half-wired.
  It resolves with the WorkManager reminder (PRD §5, architecture §7).
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
