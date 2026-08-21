# Architecture: Gawi (Android)

Engineering companion to [the PRD](prd.md). The PRD says *what* and *why*;
this document fixes *how*. It records decisions, not aspirations — if a
decision changes, change it here first.

**Status:** agreed baseline before scaffolding (2026-08-16).

---

## 1. Principles

1. **Offline-first, verifiably.** The MVP APK declares no network permission.
   Every feature must work with the radio off, forever.
2. **The event log is the source of truth.** All user data is an append-only
   log of events in local SQLite. Everything the UI shows is derived from it
   and can be thrown away and rebuilt.
3. **Sync-ready from day one.** Future sync (LAN in Phase 2, E2E cloud in
   Phase 3) is defined as: union of two event logs, dedupe by event UUID,
   tombstones handle deletes. Nothing in the MVP schema may make that require
   a data migration.
4. **The domain is pure Kotlin.** `:core:domain` has zero Android imports.
   Dates, streaks, event replay — the correctness-critical logic — runs and is
   tested on the plain JVM.
5. **Events are immutable.** The log is never rewritten. Fixes and undos are
   new events; schema evolution is handled by upcasting on read (§3).
6. **Commands are validated; events are not.** Business rules (the 3-day
   retro window, "habit exists", etc.) guard the *command* path — a user
   trying to do something now. The apply/replay path accepts any well-formed
   event unconditionally. Replay must never be able to fail on data that was
   once valid; sync and import depend on this.

## 2. Module structure

Now-in-Android convention: Gradle version catalog
(`gradle/libs.versions.toml`), convention plugins in `build-logic/`, and
`core`/`feature` modules. Right-sized for a solo app:

| Module | Contents |
|---|---|
| `:app` | MainActivity, navigation graph, Hilt app wiring, WorkManager scheduling for the end-of-day reminder |
| `:core:domain` | Pure Kotlin/JVM: event types, projection logic, logical-date rules, streak computation, UUIDv7 generator, event and export JSON codecs |
| `:core:data` | Repositories, event store, Room database + DAOs, DataStore settings and the last-export stamp, export/import plumbing and the CSV of completions |
| `:core:ui` | Theme, shared composables |
| `:feature:today` | Today view (app home screen, Momo's habitat) |
| `:feature:habits` | Create/edit/archive habit, habit detail |
| `:feature:settings` | Day boundary, week start, reminder time, export and import, the 30-day export nudge |
| `:widget` | Glance home-screen widget |

Dependency rule: `feature → core`, `widget → core`, `app → everything`,
`core:data → core:domain`, and `:core:domain` depends on nothing but the
Kotlin stdlib and kotlinx-serialization.

This table fixes the **target shape**, not the creation order. Scaffolding
starts with `:app`, `:core:domain`, and `:core:data`; each remaining module is
created when its first screen is built. What is non-negotiable from day one is
the dependency rule — in particular that domain logic never lands in a module
where it can import Android.

Built so far: `:app`, `:core:domain`, `:core:data`, `:core:ui`,
`:feature:today`, `:feature:habits` and `:feature:settings`. `:widget` does not
exist yet. `app/src/debug/` is gone: the debug-only activity that set the day
cutoff and the reminder time over `adb` was deleted when `:feature:settings`
landed, as this paragraph used to promise, and there is no debug source set
anywhere in the project now.

`:feature:settings` holds the three preferences the data layer stores — day
boundary, week start and reminder time — and, below them in a labelled section
of their own, **export and import**. They are a section rather than two more
setting rows because they are not settings: they have no stored value, and
between them they are the only disaster-recovery path there is, `allowBackup`
being off (§6) and the event log reconstructible from nothing. The export row
does now carry one stored value — how long ago it last wrote a file — which is
§6's compensating control and the whole of PRD §5's nudge;
`docs/ux/settings.md` §6 has the copy decisions. **The CSV of completions is
built too**, as of 2026-08-21, which completes PRD §5's data row — a third row
in the same section, deliberately not a recovery path, and the one row there
whose help line has to say so.

The CSV lives in `:core:data` as this table's row for that module says, and its
being there rather than in `:core:domain` is worth one sentence: unlike the
export codec it needs no kotlinx-serialization, so putting it here leaves the
boundary guard below intact. It also never touches the last-export stamp, which
is enforced by the class that writes it not being given `ExportJournal` at all
rather than by a comment asking for restraint.

**The export codec is in `:core:domain`, not `:core:data`**, which is the one
place this table's `:core:data` row would once have said otherwise. An export
embeds each payload as nested JSON rather than as an escaped string — the "open
formats" half of the PRD's data-ownership promise, so that `jq` can walk the
file — and doing that means parsing `EncodedPayload.json`, i.e. knowing that the
domain's opaque payload string is JSON at all. That knowledge belongs in the
package that already has it. The consequence is worth stating because it is
load-bearing: `:core:data` has no kotlinx-serialization dependency, in main or
in test, so that dependency appearing there later is a signal this boundary has
been crossed.

**`:app` owns navigation, and no other module depends on a navigation
library.** A feature module exposes Route composables taking plain lambdas, so
a screen reports what happened to it and `:app` decides where that goes. Two
things follow, and both are worth keeping: a feature's tests never need a
`NavController`, and a screen cannot navigate somewhere its own module has no
business knowing about. Concretely, feature modules take
`androidx.hilt:hilt-lifecycle-viewmodel-compose` for `hiltViewModel()` and not
`hilt-navigation-compose`, whose pom would put navigation on their classpath.

Routes are **type-safe** — `@Serializable` classes in
`app/src/main/kotlin/com/gawi/app/navigation/`, not string templates — so an
argument that changes shape is a compile error rather than a null at runtime.
Habit ids cross that boundary as `String`, because `HabitId` rejects a
non-canonical UUIDv7 by throwing and a route argument is exactly where an
unexpected value arrives; it is validated inside the screen's ViewModel.

## 3. Event model

One `events` table, append-only:

| Column | Type | Notes |
|---|---|---|
| `id` | TEXT, PK | UUIDv7 (time-ordered; hand-rolled in `:core:domain`, ~20 lines + tests, no dependency) |
| `type` | TEXT | Event type discriminator |
| `schema_version` | INTEGER | Version of this event type's payload shape |
| `occurred_at` | INTEGER | Epoch millis (instant) |
| `tz_offset_min` | INTEGER | Device UTC offset at write time, for audit |
| `payload` | TEXT | JSON via kotlinx.serialization |

Event types at MVP:

- `HabitCreated` — name, icon/color, schedule (`daily` or `weekly(n)`),
  optional tag
- `HabitUpdated` — changed habit metadata
- `HabitArchived`
- `HabitUnarchived`
- `CompletionAdded` — habit id, `logical_date`, optional note
- `CompletionTombstoned` — references the `CompletionAdded` event id
- `CompletionNoteUpdated` — references the `CompletionAdded` event id, new
  note text (the PRD's long-press/detail flow attaches notes after the tap).
  Empty text is a valid write and clears the note; it participates in LWW
  like any other note write (§4, note resolution).

**Conflict resolution (LWW):** where two events contend (habit metadata, a
completion's note), last-write-wins by `occurred_at`, ties broken by event id
— UUIDv7 gives a deterministic total order for free. `HabitUpdated` is
**whole-record** LWW, not per-field merge; per-field is CRDT territory this
app does not need. Clock skew between one person's devices is an accepted
risk.

**Settings are not events.** Day boundary, week start, reminder time, and
timezone behavior are device-local preferences in DataStore. They never sync
and never enter the log.

**Schema evolution:** payloads carry `schema_version`; readers upcast old
versions to the current shape at deserialization time. The log is never
migrated in place — replaying a years-old log through current code must always
work (PRD §7 "migrations replay-safe").

**An export carries two version numbers and they mean different things.** The
envelope has a `format_version`, which says how to find the events; each event
carries the `schema_version` above, which describes one payload. A newer
envelope full of v1 payloads is an ordinary file, and so is the reverse. A
reader must establish the envelope version *before* it decodes anything else,
so that a file from a newer build is refused as unsupported rather than as
corrupt — the log has one writer and an unknown shape there is corruption, but
a file is picked by a user and may legitimately come from the future. Payload
bytes travel verbatim through both directions of an export, because decoding
and re-encoding would upcast to the current version and drop unknown keys,
which is the log migrated in place.

## 4. Projections (derived state)

Strategy: **transactional projections.**

- Appending an event and updating the derived Room tables (`habits`,
  `completions`, per-habit streak state) happen in **one Room transaction**.
- `rebuildProjections()` drops all derived state and replays the full log.
  It is:
  - the migration story — a Room schema change on derived tables is just
    "bump version, rebuild";
  - the sync story — Phase 2 inserts foreign events, then rebuilds;
  - the test oracle — a standing invariant test asserts that incremental
    updates and a full rebuild produce identical state.
- The UI reads derived tables through ordinary Room `Flow` queries; nothing
  above `:core:data` knows events exist.

**Completion idempotency:** a habit is completed **at most once per logical
date**. Multiple live `CompletionAdded` events for the same
`(habit_id, logical_date)` — from two unsynced devices, or add→undo→add on one
— project to a single completion. The projection never rejects an event, so
sync merges converge by construction.

**Undo semantics:** undo tombstones **all** live `CompletionAdded` events for
that `(habit_id, logical_date)` known locally at undo time. This keeps undo
meaningful after a merge (a duplicate arriving from another device cannot
silently resurrect an undone completion for events the undo already saw) and
makes add→undo→add work naturally, since each re-add is a fresh event.

**Note resolution:** duplicate `CompletionAdded` events collapsing into one
completion (above) raises "whose note does the cell show?" The rule:

- A note — whether inline on `CompletionAdded` or via `CompletionNoteUpdated`
  — is **live iff the `CompletionAdded` it belongs to is live** (not
  tombstoned). Notes die with undo; re-adding a completion never resurrects
  an old note.
- The displayed note for a `(habit_id, logical_date)` cell is **LWW across
  all live note writes** in that cell, using the existing
  `occurred_at`-then-id ordering (§3). A clear (empty text) is a note write
  and wins like any other — so clearing beats an older non-empty note.

This is a pure function of the event set, so duplicate merges pick one note
deterministically and the incremental-≡-rebuild invariant holds.

**Widget refresh:** Glance widgets do not observe Room. A widget tap goes
through the same command path as the app, so open screens update via their
`Flow` queries — but the reverse direction must be explicit: the repository
triggers a `GlanceAppWidget` update after the projection transaction commits.
That is the single place responsible for keeping the widget current.

At this app's data volume (~2k events/year) a full rebuild is milliseconds,
so `rebuildProjections()` is cheap enough to reach for whenever in doubt.

## 5. Logical dates & streaks

The correctness core of the app. All of it lives in `:core:domain`.

- `logical_date = f(instant, day-boundary cutoff, timezone)` — e.g. with a
  03:00 cutoff, 01:30 on the 16th belongs to the 15th.
- **Stored at log time.** The completion event carries the `logical_date` the
  user saw when they tapped. Changing the **day-boundary** setting later applies
  **prospectively only**; past events never re-bucket.
- **Week start is not the same rule**, and the difference is user-visible. No
  event stores a week, so week bucketing is derived from `logical_date` at read
  time — changing the week start therefore re-counts weeks that have already
  happened, including the one currently on screen. `TodayQueryTest`'s *"changing
  the week start re-buckets a screen that is already open"* pins it, and
  docs/ux/settings.md §2 is why the two settings carry different copy.
- Week bucketing uses the configurable week start (default Monday). Weekly
  habits are `n` completions anywhere in the week — not tied to specific days.
- Streaks are computed from completions: **day-streaks** for daily habits,
  **week-streaks** (consecutive weeks hitting n/n) for weekly habits. A missed
  day/week resets; grace mechanics deferred (PRD OQ-3).
- The **3-day retroactive window** is a *command* rule (§1.6): the domain
  rejects an attempt to log a completion whose `logical_date` is more than
  3 days before today. It does **not** apply to the event apply/replay path —
  sync and import insert months-old events, and replay must accept them
  unconditionally. (The honesty-prompt confirmation is UI; the window is a
  command validation; events themselves are never rejected.)

## 6. Backup & data safety

**Android Auto Backup is disabled**: `android:allowBackup="false"` plus
disabled `dataExtractionRules`. With the default (`true`), the OS backs app
data up to the user's Google account — no network permission needed by the
app — which would quietly falsify the PRD's "verifiable privacy claim."

Documented cost: disabling it also disables OS device-to-device transfer, so
until Phase 2 sync ships, **export/import is the only migration and disaster-
recovery path**. Compensating control: a gentle in-app nudge when no export
has been made for 30 days (local check only, surfaced in-app — never a
notification). **Built 2026-08-21**, as a value line and a help line on the
export row itself rather than as a banner or a second surface.

**Only the JSON export counts as a backup.** The CSV of completions, built
2026-08-21, writes no events and cannot be imported, so it never stamps the
last-export time and never settles the nudge above — a spreadsheet is not a copy
of the log. `ExportJournal` is not reachable from that path at all, which is how
this is kept true rather than remembered.

**What records the export is not a setting**, and the boundary is worth stating
because the obvious place is wrong. The stamp lives in the settings preferences
file under its own key, read and written by `ExportJournal`, and deliberately
*not* as a fourth `UserSettings` field: that type is compared to decide whether
`observeToday()` has to re-run, so a field changing on every export would
restart the streak sweep under an open screen. The nudge also needs to know
whether the log holds anything at all, which is not a preference in any reading,
so one flow carries both. Settings are still not events (§3) and this is still
not a setting.

## 7. Tech choices

| Concern | Choice |
|---|---|
| Language / UI | Kotlin 2.x, Jetpack Compose (current BOM) |
| SDK | minSdk **29**, targetSdk latest stable, JDK 17 |
| DI | Hilt |
| Persistence | Room over SQLite; DataStore for preferences |
| Serialization | kotlinx.serialization (event payloads, JSON export) |
| Time | java.time (minSdk 29 ⇒ no desugaring) |
| Navigation | Compose Navigation, type-safe `@Serializable` routes, single activity |
| Widget | Jetpack Glance |
| Reminder | WorkManager + notification via PendingIntents |
| IDs | UUIDv7, hand-rolled in `:core:domain` |

**Reminder timing is deliberately inexact.** The end-of-day reminder fires
within WorkManager's flex window (~15 min); the `SCHEDULE_EXACT_ALARM`
permission (Android 12+, Play-policy scrutiny) is **deliberately avoided** —
a "habits left today" nudge does not need exact delivery. The scheduled time
just needs enough margin before the day boundary to absorb the flex window.
Do not "upgrade" this to exact alarms.

## 8. Testing strategy

- `:core:domain`: exhaustive JVM unit tests — logical-date edge cases (cutoff
  boundaries, DST, timezone changes), day- and week-streak computation,
  retro-window enforcement, UUIDv7 monotonicity, and the
  incremental-≡-rebuild projection invariant.
- `:core:data`: Room DAO tests on the JVM (Robolectric / in-memory database).
- Feature modules: **Compose UI tests on the JVM**, under Robolectric, in the
  module's own `test` source set — not `androidTest`. They render a stateless
  screen composable, assert what it draws and what a tap reports, and so cover
  the gap between a mapper test (what the state says) and a ViewModel test
  (which state is emitted). Being unit tests, they need no device and change
  nothing below. Aim them at wiring and at rules a reader could delete by
  accident; the logic itself is already covered above.
- `:app`: one **navigation test** that launches the real `MainActivity` under
  Robolectric with `HiltTestApplication`. It is the only test of the production
  Hilt graph — a missing binding is a runtime failure, so nothing below it can
  catch one — and it asserts that each route leads where it says. Driving the
  real activity is forced rather than chosen: feature screens and ViewModels are
  `internal` to their modules, and `hiltViewModel()` needs an
  `@AndroidEntryPoint` host.
  **Journeys that write are deliberately not here.** Room's `InvalidationTracker`
  does not deliver in this setup, so a screen never re-reads after a write;
  measured by calling the repository directly, which succeeds while the screen
  stays stale. Such a test passes only when a `WhileSubscribed` window happens to
  lapse between two assertions, which is a pass that proves nothing. Replacing
  the database would fix it and `@TestInstallIn` cannot reach it, because the
  modules binding it are `internal` to `:core:data`. Until that changes, the
  command path stays covered by `:core:data`'s tests and by `docs/running.md` §4
  on a device.
- Widget, notifications, and OEM battery behavior: **physical device only**
  (PRD §7). No emulator in CI.
- CI runs unit tests only; instrumented tests are a manual, on-device
  activity. The JVM Compose tests are unit tests and are inside that gate —
  this line changes only if cross-app journeys (widget on a launcher, the
  notification shade) are ever put in CI, which needs Gradle Managed Devices
  and is a decision, not a detail.
- Golden-image / screenshot testing is **deliberately not adopted yet**: Momo's
  art is placeholder copy (PRD OQ-4), so goldens would pin something designed
  to change. Revisit when the four moods have real art.

## 9. Repo integration (template contract)

The template's Makefile contract maps to Gradle as:

| Target | Wiring |
|---|---|
| `make setup` | `./gradlew help` warm-up (wrapper fetches everything) + git hooks |
| `make fmt` | Spotless (ktlint) apply |
| `make lint` | Spotless check + detekt + Android Lint |
| `make test` | `./gradlew test` (module-generic: JVM modules' `test` plus Android modules' unit tests; a new module can never be silently skipped) |
| `make run` | `./gradlew :app:installDebug` + `adb shell am start` (see below) |

Deviations and notes:

- **`make run` is an addition to the template's contract**, not a rename of it.
  Nothing in CI calls it, so the cross-repo sameness the Makefile header protects
  is untouched; what it buys is that §8's manual on-device activity has a
  one-command entry point instead of living in people's shell history. The
  procedure it serves is [docs/running.md](running.md).
- `ci.yml` gains JDK 17 setup and Gradle caching. This is a **conscious
  deviation** from the template's "never edit the workflow" rule: that rule
  prevents stack drift across many repos, and this repo has exactly one stack
  forever. Without caching, every CI run pays a 5–10 minute cold Gradle build.
- The wiring gets documented in `docs/stacks/kotlin-android.md` in the
  template's own style.
- Secrets: nothing at MVP (no network). When release signing arrives, the
  keystore and its passwords stay out of git; signing config paths go in
  `.env.example` with placeholders.
