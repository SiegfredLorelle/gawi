# PRD: Gawi (working title) — habit tracker with Momo the axolotl

**Version:** 0.5 (draft)
**Author:** Siegfred Lorelle Mina
**Status:** Baseline for iteration
**Last updated:** 2026-08-23

---

## 1. Problem Statement & Purpose

I do a set of recurring activities (leetcode, Anki language practice, exercise, reading, journaling, courses) that serve my long-term growth. I want to:

1. **Stay consistent day to day** — effortless logging, streaks, and (later) a mascot that makes me not want to break the chain.
2. **Understand my trajectory over long horizons** — at the end of a quarter or a year, the accumulated data should answer: *where did my time and focus actually go, and is it aligned with my long-term goals?*

Existing habit trackers are cloud-first, account-required, and lock data behind their servers. This app is offline-first, data stays on my device, and I own it completely.

## 2. Goals & Non-Goals

### Goals
1. Track recurring activities — daily or N-times-per-week — with near-zero logging friction (< 5 seconds, one tap).
2. Motivate continuity: streaks at MVP; **mascot with emotional states (Duolingo-style) is committed** for Phase 1.
3. Enable long-horizon retrospectives (quarterly/yearly): tags on habits power "where is my effort going" insights.
4. Offline-first: fully functional with no network, no account. Data stored locally on device (SQLite).
5. Data ownership: full export at any time in open formats (JSON/CSV).
6. **[Committed, Phase 2] Device-to-device LAN sync** (LocalSend/Syncthing-style pairing). Data model is sync-ready from day one.
7. **[Future] Optional E2E-encrypted cloud sync** as a paid convenience — never a requirement. Primary monetization candidate.

### Non-Goals (for now)
- iOS, web, desktop (Android first; others later)
- Social features, sharing, leaderboards
- Full gamification systems (XP, currencies, shops)
- Running timers / time tracking
- Heavy goal-management features (tags are the lightweight substitute)

## 3. Target User

Me (v1), on Android. Later: privacy-conscious self-improvers who want motivating habit tracking without a subscription-gated cloud account.

## 3.5 Identity (working — held loosely until pre-launch)

- **App name (leading candidate): "Gawi"** — Tagalog for **"habit / customary behavior."** Four letters, easy to say, and searches show no existing habit app by this name. Shortlist alternates: **"Hinabi"** (Tagalog "woven" — preserves the weaving metaphor), **"Araw"** (Tagalog "day/sun"). **Rejected: "Habi"** — user research found heavy collisions, including habi.app, an existing habit tracker (direct category conflict = trademark/SEO risk) plus multiple voice-chat apps. **Parked: "Lotl"** — no habit-app conflict, but the axolotl space is saturated with "lotl" wordplay (Steam game LOTL, merch brands). Re-verify final name (Play Store, domain, trademark) before launch.
- **Core metaphor (kept regardless of name): weaving.** Each completed day weaves one more thread; streaks are fabric woven over weeks and months.
- **Also considered, passed:** "Aholote" (informal Spanish spelling of *ajolote* = axolotl) as app or mascot name — spelling ambiguity hurts word-of-mouth, reads as "misspelled axolotl" to Spanish speakers, and a species name weakens the mascot's character (kept: Momo). Possible future lore angle: Xolotl, the Aztec god the axolotl is named after.
- **Mascot: Momo, an axolotl.** Chosen for the **regeneration** theme: when a streak breaks, Momo doesn't guilt-trip — axolotls regrow. Broken streaks are framed as "pick the thread back up," a deliberate contrast to guilt-based mascots (e.g., Duolingo's owl).
- **In-app concept: Momo's habitat.** The Today view doubles as Momo's small habitat/tank; its vibe (and Momo's mood) reflects streak health. This preserves the "Habitat" idea inside the app even with the shorter name.
- **Emotional states (draft):** thriving, content, worried, regenerating (post-broken-streak, hopeful — replaces "sad").
- **Naming rejected/avoided:** owls, foxes, cats, flame iconography (crowded/owned by others); "Momo" as the app name (collides with existing major apps).

## 4. Core Concepts / Data Model (conceptual)

- **Habit** — a recurring activity. Fields: name, icon/color, schedule, **optional tag** (e.g., career, health, language), archived flag.
  - Schedules: `daily` (expected every day) or `weekly(n)` (n times per week, **not tied to specific days** — 3/3 on any days counts).
- **Completion (event)** — binary checkmark: habit ID, timestamp, logical date (respecting day boundary), optional free-text note. Append-only; deletions are tombstones.
- **Tag** — a simple label on a habit. One tag per habit at MVP (revisit multi-tag later). Powers effort-distribution insights.
- **Day boundary** — configurable cutoff time; defaults to midnight, device timezone.
- **Week** — calendar week; **default start Monday, configurable** (same settings pattern as day boundary).

### Storage principle (sync-ready from day one)
All data is an **append-only event log** (habit created/edited/archived, completion added, completion tombstoned) in local SQLite, with UUIDv7 event IDs. Current state is derived from the log. Future sync = union of event logs, dedupe by UUID, last-write-wins on habit metadata. Hard requirement at MVP so sync never needs a data migration.

## 5. Feature Requirements by Phase

### Phase 0 — MVP (Android, personal daily driver)

**Complete as of 2026-08-22.** Every bullet below is built and every §6 criterion
is met; the one bullet that did not ship — notification quick-complete actions —
was formally moved to Phase 1 on its own terms, which the bullet explicitly
allowed.

**The 30-day trial was waived on 2026-08-23** and Phase 1 was started instead —
the success criterion at the end of this section records what that costs, and §8
and §9 record what it cost the two open questions and the two risks that were
parked on it. What remains is therefore not construction and no longer a trial:
it is `docs/running.md` §4's device checks, still owed, specifically its widget
and accessibility blocks. Those were argued to be worth running **before** Phase 1
rather than after, because Phase 1's first two entries add screens and its fourth
replaces the three-face placeholder on both the Today view and the widget — the
surfaces those checks exist to verify. **They were skipped anyway on 2026-08-23,
and re-triggered rather than dropped.** The argument survived the decision and
changed sides: Phase 1 opens with a whole-app restyle (§8, OQ-4 widened), so a
TalkBack pass, a 200% font-scale pass and a widget legibility check run now would
measure a theme that is about to be replaced. Their trigger was the restyle
landing — **and it has fired.** The restyle is in, so these are due rather than
deferred, and `docs/running.md` §4 carries the list plus a new block for what
only a device can judge about the scheme itself. Two of them were run with it:
the 200% font-scale pass, and a mechanical check that every colour swatch
announces the hue it actually draws. TalkBack focus order, the widget on a
launcher and Accessibility Scanner are still owed. `docs/running.md` §3 covers getting
the app onto a phone.

**Habits**
- Create/edit/archive habits: name, icon/color, schedule (daily or n-per-week), optional tag.
- Weekly habits show per-week progress ("2/3 this week"), week start per settings.

**Logging**
- Today view (app home screen): all habits, one tap to complete, tap again to undo (undo = tombstone event; same-day undo has no friction).
- Optional note per completion (long-press / detail view — never blocks the one-tap flow). (**Built 2026-08-21** — long-press a completed day on habit detail, which is reached from the habit list or by saving a new habit. The Today-row long-press is *not* built. [docs/ux/habits.md](ux/habits.md) §7.)
- **Retroactive logging: up to 3 days back only.** Editing a past day triggers a confirmation with an honesty prompt ("You're logging for a previous day — make sure this is accurate. Be true to yourself."). (**Built 2026-08-21** — [docs/ux/habits.md](ux/habits.md) §7. The prompt is UI friction only; architecture §5 keeps the window a command validation, so the domain refuses an out-of-range day whatever the screen believed.)
- Android **home-screen widget**: today's habits, tap to complete without opening the app. **Built 2026-08-21** — a tap toggles, so it undoes too; [docs/ux/widget.md](ux/widget.md) records the decisions and what a widget cannot keep current on its own.
- **End-of-day reminder notification** if due habits remain incomplete as the day boundary approaches (configurable time, e.g., 21:00). Silent when everything is done. One reminder max per day. (**Built 2026-08-21** — [docs/ux/reminder.md](ux/reminder.md).)
- **Notification quick-complete actions** (complete a habit directly from the reminder): MVP **stretch goal**, and **moved to Phase 1 on 2026-08-21**, which this bullet explicitly allowed. The reminder shipped as open-the-app only. The deciding reason was not complexity: §6.1's one-tap criterion was already met by the widget, so an action button is a second path to a solved problem, and it would carry OQ-2 — unanswered — along with it. Still committed. [docs/ux/reminder.md](ux/reminder.md) §4.

**Motivation**
- Daily habits: day-streak counter. Weekly habits: **week-streak** (consecutive weeks hitting n/n). (**Visible on both its surfaces as of 2026-08-21** — see §6.6.)
- Simple emotive indicator on Today view (happy/neutral/worried) as the mascot placeholder.
- Missing a day/week resets the streak. Grace mechanics: revisit after real usage (OQ-3).

**Data & settings**
- SQLite local storage, no account; **no network permission at MVP** (verifiable privacy claim).
- **Android Auto Backup disabled** (`allowBackup=false`) — otherwise the OS ships app data to the user's Google account with no network permission needed, silently breaking the privacy claim. Trade-off: no OS device-to-device transfer, so export/import is the only migration/recovery path until sync ships.
- Full JSON export + CSV of completions; import from export (doubles as manual device migration until sync ships).
- Gentle in-app nudge when no export has been made for 30 days (local check, shown in-app only — never a notification). Compensates for backup being disabled.
- Configurable: day boundary time, week start day (default Monday), reminder time, timezone behavior (default: device timezone).
- **A fourth preference that is not on this list landed 2026-08-26**: the app's theme, as System / Light / Dark ([docs/ux/settings.md](ux/settings.md) §7). Not scope creep so much as the last thing OQ-4 left owed — the two schemes were designed and built on 2026-08-23 and the device could choose between them, but the user could not. Timezone behaviour, the fourth item this line *does* list, remains deliberately unbuilt for the reason that document's §1 gives: it has exactly one value.

**MVP success criteria:** I use it every day for 30 consecutive days without reverting to my old method. **Waived on 2026-08-23 — not met, not failed, not run.** Kept rather than deleted because three other things in this document were leaning on it: §9's first risk named it as its only mitigation, and OQ-1 and OQ-3 were both explicitly parked on what it would reveal. Deleting the criterion would have left those three pointing at nothing, which is the failure mode this repo keeps finding. It is also **not rescheduled**: 30 days on a Phase 1 build measures a different app, so this criterion cannot be picked up later — it can only be replaced by a new one.

### Phase 1 — Mascot, quick actions & insights (committed)

**Readiness order, assessed 2026-08-22; the gate in front of it removed
2026-08-23.** This used to open "nothing here starts before the 30-day trial".
The trial was waived, so what is left is simply the order to take Phase 1 in,
cheapest-unblocked first:

1. **Insights v1 heatmap** — the cleanest. `HabitRepository.observeCompletedDates`
   already serves an arbitrary date range, and
   [docs/ux/habits.md](ux/habits.md) §8 deliberately parked the month view here
   rather than growing habit detail's writable strip into one. No open question
   blocks it.
2. **Tag-based effort distribution** — a pure read too, but it needs a new
   aggregate query rather than an existing one. **Build it single-tag**, because
   one tag is what the wire format holds today — but OQ-1 was settled the other
   way on 2026-08-23 (multi-tag is committed, unscheduled), so build it knowing
   the *metric* moves when the schema does: once a completion can carry more
   than one tag, "share of completions per tag" stops summing to 100% and has to
   choose between fractional and full attribution. That choice belongs to the
   schema bump, not to this screen. What this screen owes it is not to be shaped
   as though one tag were permanent.
3. **Notification quick-complete actions** — blocked on **OQ-2** (what to do when
   more than three habits remain, Android's action-button cap). Not urgent: §6.1
   is already satisfied by the widget.
4. **Momo** — blocked on **OQ-4** (art style), which is a design decision before
   it is an engineering one. `Mood` already computes all four states; what is
   missing is art, not logic ([docs/ux/today-view.md](ux/today-view.md) §4).

Note that 4 partly unblocks OQ-3's second half — see §8.

**The order was inverted on 2026-08-23, before any of it was built.** What is
above is a readiness assessment and it still reads correctly; what it does not
weigh is that items 1 and 2 *draw* things. The app was on stock Material 3 — no
bespoke `ColorScheme`, no typography — and that was not an oversight but this
same OQ-4 deferral, written into `core/ui`'s own source: `GawiTheme`'s KDoc said
it stayed stock "because Momo's palette is PRD OQ-4 and undesigned", and
`HabitPalette` repeated it. So the palette was not a fifth workstream that could
be scheduled against Insights; **it was item 4, and items 1 and 2 were downstream
of it.** (The colour half landed on 2026-08-23 and the app is no longer stock —
see §8's OQ-4. This paragraph is kept in its original tense-of-decision because
it records *why* the order was inverted, which outlives the state it describes.) Building the heatmap first means choosing its colour scale — the one
undecided piece of it most tightly bound to a palette — against a theme that is
about to be replaced.

The order actually being taken is therefore: **OQ-4's brief first, then
Insights.** The brief is split where its lead times split. Its palette,
typography and habit hues are what Insights needs and go first, unblocking
`Theme.kt`, the widget and the two feature modules together; Momo's own art and
the launcher icon are a longer job and run behind them, so a Rive state machine
never holds the phase up.

**What actually landed on 2026-08-23 was two thirds of that first half.** The
colour scheme and the eight retuned hues are in the code and verified on a
device. **Typography is not** — §5 of
[visual-identity.md](ux/visual-identity.md) commits to bundling a variable font
and names the ten roles the app draws, but the typeface waits on an experiment
(can a Glance widget be handed a bundled font at all), because the app and the
widget sit side by side on a home screen and a face that cannot reach the widget
is a different choice from one that can. So the app is no longer generic in
colour and still is in type, which is the largest remaining gap between it and
the design canvas. The widget also still draws on Glance's defaults; §7.4 of that
document scopes its palette and the three further surfaces. The cost is honest and worth stating: engineering now
waits on a design decision that has been open since this document was written,
and the split is what bounds that wait rather than removing it.

Everything below that is not colour — the completion-rate denominator and the
tag aggregate query — is unblocked either way, and was built first for that
reason (2026-08-23).

**Mascot (committed feature)**
- Momo with emotional states (thriving, content, worried, regenerating) driven by streak health and today's completion status. "Regenerating" replaces "sad" on purpose — see §3.5.
- Appears in Today view, widget, and reminder notifications ("Momo is worried — 2 habits left today!"). Placement in the Today view is fixed: see `docs/ux/today-view.md`.
- Streak milestone celebrations (7, 30, 100 days; 4, 12, 52 weeks). Not yet designed. What **is** built (2026-08-26) is a day-complete celebration — the tank marks the mood entering thriving, once per screen session — and Momo's habitat now has life in it that keeps the mood's tempo, both per `docs/ux/momo.md` §4 and §6.
- **The app icon rides on the same decision** (recorded 2026-08-22; **built 2026-08-25**, the day the character was). Until then there was no `mipmap/ic_launcher` in the app at all — the manifest pointed `android:icon` at `@android:drawable/sym_def_app_icon`, Android's generic default and not public API, as a deliberate placeholder — because a logo drawn before Momo's art style was settled (OQ-4) would have been drawn twice. It is now Momo as a mark (`docs/ux/visual-identity.md` §7.1), an adaptive icon with the woven thread as its themed layer.
- **Design tooling plan:**
  - Concepting: iterate on character personality, expression sheets, and design briefs with Claude; AI image tools for visual concept exploration.
  - Production: **Rive** (recommended) — purpose-built for interactive, state-driven character animation with an official Android runtime; state machine maps directly to mascot moods. Alternatives: **Lottie** (After Effects pipeline, good for one-shot animations, weaker for interactive states), **Krita/Inkscape** (free, Linux-native, for drawing the base character), **Figma** (layout/design, can feed into Rive).
  - Fallback: static expression images first, animation later.
  - **What was actually done (2026-08-25): none of the above.** Rive was researched to an integration brief and dropped on one fact — since 2025-10-20 its free plan cannot export a `.riv`, and this project does not pay for tooling. The character was already drawn, in all four moods with a motion spec, on the Claude Design canvas that settled the visual identity; that motion is rigid (translate, rotate, scale, opacity), so it is drawn and animated in Compose directly, with no runtime and no asset. [docs/ux/momo.md](ux/momo.md) §1 has the reasoning and Lottie's standing as the fallback.

**Other Phase 1**
- Notification quick-complete actions (if not shipped in MVP).
- Insights v1: per-habit heatmap/calendar history, completion-rate trends. (**Both built — 2026-08-24.** A calendar month of two-state days stepped by two arrows, and a five-month rate trend under it, on a screen reached from habit detail's "see full history". [docs/ux/insights.md](ux/insights.md) §8 records what building them settled, including the four places the plan and the design artboard turned out to be wrong.)
- **Tag-based effort distribution**: share of completions per tag over a selected period. (**Built 2026-08-24**, as one breakdown of a new top-level Insights screen reached from Today's app bar — §7's "where is it reached from" had no answer while the metric had no per-habit screen to hang off. The period is Month, Quarter or Year, which settles §7's last open question. Totals rather than percentages, for the reason [docs/ux/insights.md](ux/insights.md) §5 gives about OQ-1.)
- **Beyond what this section asked for**, and worth recording rather than leaving to be discovered: that screen also carries an app-wide headline and a per-habit adherence list, which is the first line of Phase 1.5 below. Everything in the app until then reported on one habit or one day, and the gap was raised as soon as the heatmap was reviewable. The retrospectives now have a screen to grow out of rather than a blank module.

### Phase 1.5 — Retrospectives (the long-horizon payoff)
- **Quarterly / yearly review screens**: adherence per habit and per tag across the period, trend lines, best/worst streaks, "focus shifted from X to Y" summaries.
- Export of a review as an image/PDF (nice-to-have).

### Phase 2 — LAN Sync (committed)
- Device discovery on same network via mDNS; pairing with confirmation code (LocalSend-style trust model).
- Sync = exchange event logs, union by UUID, tombstones handle deletes. Manual "sync now" first; background auto-sync later.

### Phase 3 — Cloud Sync & monetization
- Cloud is just another sync peer: an E2E-encrypted event store; server never sees plaintext.
- Free forever: local + LAN sync. Paid: cloud backup/sync (Obsidian Sync model).

### Phase 4 — Other platforms
- iOS, then possibly desktop/web. Event-log model and sync protocol keep clients thin.

## 6. Key UX Requirements

1. Logging < 5 seconds: widget or notification action, one tap. (**The widget satisfies this as of 2026-08-21.** The notification *action* has now been deferred to Phase 1 on §4's own terms — the criterion is met, so a second one-tap path would carry OQ-2 for no gain. [docs/ux/reminder.md](ux/reminder.md) §4.)
2. Today view is the app's home screen.
3. Notes/tags never add friction to the base flow — always optional, always secondary. (**Built 2026-08-21** — the note is a long-press on a day already logged, on habit detail; nothing on the way to logging one asks about it. [docs/ux/habits.md](ux/habits.md) §7.)
4. Retroactive edits carry deliberate friction (confirmation + honesty prompt) but stay possible within 3 days; same-day undo is frictionless. (**Built 2026-08-21** — the strip draws the day outside the window shut rather than refusing a tap on it, and the prompt appears for an undo as well as a completion, since §5's "editing a past day" covers both. [docs/ux/habits.md](ux/habits.md) §7.)
5. One reminder max per day; silent when all done. (**Built 2026-08-21** — [docs/ux/reminder.md](ux/reminder.md). Both halves are pinned by `ReminderCheckTest`, including the case that would break them: a wake deferred past the day cutoff, which would otherwise remind about a fresh day and consume its one reminder.)
6. Streak visibility everywhere it motivates: Today view and habit detail. (**Both built as of 2026-08-21**; detail draws the streak large and captioned rather than as the Today row's badge, and the two share one `StreakUi` so the days-versus-weeks rule cannot drift. **Narrowed 2026-08-21**: this said "Today view, widget, habit detail" and contradicted OQ-5, which asked whether the widget should show streaks at all. The widget is minimal — [docs/ux/widget.md](ux/widget.md) §2 has the reasoning.)

## 7. Technical Direction (Android MVP)

- **Language/UI:** Kotlin + Jetpack Compose (also serves as a learning project — prefer idiomatic modern Android over shortcuts).
- **Widget:** Jetpack Glance (Compose-based app widgets).
- **Storage:** Room over SQLite; event-log tables + derived-state tables/views for fast reads.
- **IDs:** UUIDv7 for all events.
- **Reminders:** WorkManager for the end-of-day reminder check; notification actions via standard PendingIntents if quick-complete ships. **`POST_NOTIFICATIONS` is the app's first and only hand-declared permission**, requested from the settings reminder row rather than at first launch; it cannot move data off the device, so "no network permission at MVP" below is untouched.
- **No network permission at MVP.**
- **Schema versioning** embedded in the event log; migrations replay-safe (sync prerequisite).

### Development environment
- Primary: **Arch Linux**. Android Studio via AUR (`android-studio`) or JetBrains Toolbox (easier updates). Emulator acceleration via KVM — ensure kvm modules loaded and `/dev/kvm` access (`kvm` group). Known rough edge: GPU rendering with some drivers; fallback `-gpu swiftshader_indirect` or software graphics in AVD settings.
- **Physical device via USB debugging is the primary test target** for widget and notification behavior (launchers and OEM battery policies differ from emulators).
- Fallback environment: macOS (MacBook) if Linux tooling misbehaves.

## 8. Open Questions

- ~~**OQ-1:** Multi-tag per habit, or is one tag enough? (Proposal: one at MVP.)~~ **Settled 2026-08-23: multi-tag, eventually.** The proposal held for the MVP and one tag is what ships, but the answer to the question as asked is that one tag is *not* enough for good. It is committed and **deliberately not assigned to a phase** — what is decided is the direction, not the date. The cost is known and is why it is not being rushed: `HabitMetadata.tag` is a single field in the domain *and in the wire format*, so this is an event-payload schema bump with an upcast-on-read rather than a UI change, which the event log's embedded schema versioning (§7) exists to absorb. The practical consequence today is a prohibition rather than a task — nothing new should be built as though one tag were permanent, and §5's Phase 1 item 2 is where that bites first. Settled on the reasoning above rather than on the 30-day trial, which is where this question used to be parked and which was waived (§5).
- **OQ-2:** Notification quick-complete UX when >3 habits remain (Android caps 3 action buttons) — show top 3? "Complete all"? Opens the app?
- **OQ-3:** Streak freeze / grace day mechanics. **Re-parked 2026-08-23 on OQ-4 rather than on usage.** This used to read "decide after the 30-day personal trial reveals how resets feel"; the trial was waived (§5), and a question whose only trigger has been removed is a question with no owner, so it was given a new one rather than left floating. **The new trigger is Momo's fourth face** — this opens when OQ-4 does, and gets decided on a build where a reset is actually visible on screen. Until then the mechanics stay unbuilt and `Mascot.REGENERATING_WINDOW_DAYS` stays 3 and stays a guess. Waiving the trial costs *this* question less than it looks, for the reason the rest of this bullet already gave: **two numbers ride on this, not one** (noted 2026-08-22), and the trial could never have answered the second. `Mascot.REGENERATING_WINDOW_DAYS = 3` is a separate guess at how long a broken streak keeps Momo regenerating, and its KDoc flags it for this same trial. **The trial as shipped cannot answer that half.** Phase 0 draws three faces and folds `regenerating` onto `neutral` ([docs/ux/today-view.md](ux/today-view.md) §4), so nothing on screen distinguishes a user recovering from a broken streak from one merely pottering — the window is decided, tested and unobservable. So it waits for Phase 1's fourth face — which is now the whole question's trigger and not just this half's. Recorded because the code said "flagged for the 30-day trial" and that instruction could not be carried out as written; `Mascot.REGENERATING_WINDOW_DAYS`' KDoc now names the fourth face instead.
- **OQ-4:** Mascot art style (round/chibi? pixel? flat vector?), and static-first vs animated-first. (Species and name decided: Momo the axolotl.) **The app's launcher icon is part of this question, not a separate one** (noted 2026-08-22) — it is Android's default placeholder today, and a mark drawn before the character would have to be redrawn to match it. §5's Phase 1 has the detail. **Widened 2026-08-23 to the whole visual identity: the app's colour scheme, its typography and the habit hues are part of this question too.** Not a new decision so much as the discovery of an old one — `core/ui`'s `GawiTheme` is stock Material 3 and its KDoc already says why ("Momo's palette is PRD OQ-4 and undesigned — inventing one here would mean choosing it in the module least able to explain the choice"), and `HabitPalette`'s says the same of its hues. Both had parked themselves here; nothing recorded that they had. The practical consequence is that this question now blocks more than a character, which is why §5's Phase 1 order was inverted to put it first. It has two halves with different lead times — palette, typography and hues, then Momo's art and the icon — and only the first half blocks Insights. **Two surfaces, not one:** a Glance tree is `RemoteViews` and cannot consume a Compose theme (architecture §2), so the widget takes any palette a second time, and `WidgetTextColourDarkTest` and its light twin must be re-run against the new values.

  **First half decided and built, 2026-08-23** — the colour scheme and the eight habit hues are in the code, and `GawiTheme` is no longer stock. [docs/ux/visual-identity.md](ux/visual-identity.md) is the record: §7.2 for the scheme, §3 for the role values including the two that failed measurement and were replaced, §6 for the hues. Building it also fixed the glyph-contrast defect §4.2 of that document describes. **Typography is decided in principle and deliberately not built**: §5 of that document defines the ten type roles the app draws and commits to bundling one variable font, but the typeface itself waits on an experiment — whether a Glance widget can be handed a bundled font at all — because the app and the widget sit next to each other on a home screen and a face that cannot reach the widget is a different choice from one that can. ~~**The second half is untouched and still open**: Momo's art style, static-versus-animated, and the launcher icon, which is still Android's default placeholder.~~ **The second half is decided and built for Today, 2026-08-25** — flat, the canvas's character, animated in Compose, all four moods drawn ([docs/ux/momo.md](ux/momo.md)). The launcher icon is now unblocked and still Android's default placeholder; the widget and reminder still show no mascot. What is now *not* true of this question is that it blocks Insights — that was the whole point of the split, and the first half landing is what discharges it. The widget's own palette is also still owed, for the reason the paragraph above gives; §7.4 of that document scopes it.
- ~~**OQ-5:** Should the widget show streaks or stay minimal (just checkboxes)?~~ **Settled 2026-08-21 with the widget: minimal.** A streak is the one number that reaches zero with no new event, so it is the value whose staleness is not bounded by user inaction — on the one surface with no live query. It also costs the width that rows need. §6.6 is narrowed accordingly rather than contradicted; see [docs/ux/widget.md](ux/widget.md) §2.
- **OQ-6:** Final name call between Gawi / Hinabi / Araw; verify availability (Play Store, domain, trademark) closer to launch. ("Habi" rejected — existing habit tracker at habi.app.)

## 9. Risks

- **Sync scope creep.** Mitigation: export/import at MVP; event-log model de-risks Phase 2 now.
- **Building it but not using it.** **Unmitigated as of 2026-08-23.** The 30-day personal-use criterion was this risk's only mitigation and it was waived (§5), so the risk is now carried open rather than covered. Nothing in Phase 1 restores it — shipping more features is the opposite move — so the honest statement is that this risk is live and accepted. Written down because an unmitigated risk that still *lists* a mitigation is worse than one that admits it has none.
- **Streak resets causing abandonment.** Mitigation: OQ-3's escape hatch, now triggered by Momo's fourth face rather than by the trial (§8). **Half of this mitigation is gone**: "observe own behavior in a trial" had no replacement and was not given one, so what remains is the escape hatch without the evidence that would have sized it.
- **Mascot becomes an art project.** Mitigation: emotive indicator at MVP; static expressions before animation; Rive state machine keeps engineering simple.
- **Notification quick-complete complexity.** Mitigation: explicitly allowed to slip to Phase 1; documented so it isn't lost.
- **Emulator friction on Arch.** Mitigation: KVM setup notes above; physical device as primary target; macOS fallback.
- **Monetization vs privacy tension.** Mitigation: E2E-encrypted cloud; local/LAN always free.

## 10. Competitive Landscape (brief)

Loop Habit Tracker (Android, offline, open source — closest competitor; no sync, no mascot/emotional layer, no tag-based retrospectives), Streaks (iOS-only), HabitKit (heatmap-centric), Habitica (heavy gamification). The wedge: local-first + peer sync + motivating character + long-horizon retrospectives, on Android.
