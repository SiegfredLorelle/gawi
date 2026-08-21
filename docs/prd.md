# PRD: Gawi (working title) — habit tracker with Momo the axolotl

**Version:** 0.5 (draft)
**Author:** You
**Status:** Baseline for iteration
**Last updated:** 2026-08-16

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

**Habits**
- Create/edit/archive habits: name, icon/color, schedule (daily or n-per-week), optional tag.
- Weekly habits show per-week progress ("2/3 this week"), week start per settings.

**Logging**
- Today view (app home screen): all habits, one tap to complete, tap again to undo (undo = tombstone event; same-day undo has no friction).
- Optional note per completion (long-press / detail view — never blocks the one-tap flow).
- **Retroactive logging: up to 3 days back only.** Editing a past day triggers a confirmation with an honesty prompt ("You're logging for a previous day — make sure this is accurate. Be true to yourself.").
- Android **home-screen widget**: today's habits, tap to complete without opening the app. **Built 2026-08-21** — a tap toggles, so it undoes too; [docs/ux/widget.md](ux/widget.md) records the decisions and what a widget cannot keep current on its own.
- **End-of-day reminder notification** if due habits remain incomplete as the day boundary approaches (configurable time, e.g., 21:00). Silent when everything is done. One reminder max per day. (**Built 2026-08-21** — [docs/ux/reminder.md](ux/reminder.md).)
- **Notification quick-complete actions** (complete a habit directly from the reminder): MVP **stretch goal**, and **moved to Phase 1 on 2026-08-21**, which this bullet explicitly allowed. The reminder shipped as open-the-app only. The deciding reason was not complexity: §6.1's one-tap criterion was already met by the widget, so an action button is a second path to a solved problem, and it would carry OQ-2 — unanswered — along with it. Still committed. [docs/ux/reminder.md](ux/reminder.md) §4.

**Motivation**
- Daily habits: day-streak counter. Weekly habits: **week-streak** (consecutive weeks hitting n/n).
- Simple emotive indicator on Today view (happy/neutral/worried) as the mascot placeholder.
- Missing a day/week resets the streak. Grace mechanics: revisit after real usage (OQ-3).

**Data & settings**
- SQLite local storage, no account; **no network permission at MVP** (verifiable privacy claim).
- **Android Auto Backup disabled** (`allowBackup=false`) — otherwise the OS ships app data to the user's Google account with no network permission needed, silently breaking the privacy claim. Trade-off: no OS device-to-device transfer, so export/import is the only migration/recovery path until sync ships.
- Full JSON export + CSV of completions; import from export (doubles as manual device migration until sync ships).
- Gentle in-app nudge when no export has been made for 30 days (local check, shown in-app only — never a notification). Compensates for backup being disabled.
- Configurable: day boundary time, week start day (default Monday), reminder time, timezone behavior (default: device timezone).

**MVP success criteria:** I use it every day for 30 consecutive days without reverting to my old method.

### Phase 1 — Mascot, quick actions & insights (committed)

**Mascot (committed feature)**
- Momo with emotional states (thriving, content, worried, regenerating) driven by streak health and today's completion status. "Regenerating" replaces "sad" on purpose — see §3.5.
- Appears in Today view, widget, and reminder notifications ("Momo is worried — 2 habits left today!"). Placement in the Today view is fixed: see `docs/ux/today-view.md`.
- Streak milestone celebrations (7, 30, 100 days; 4, 12, 52 weeks).
- **Design tooling plan:**
  - Concepting: iterate on character personality, expression sheets, and design briefs with Claude; AI image tools for visual concept exploration.
  - Production: **Rive** (recommended) — purpose-built for interactive, state-driven character animation with an official Android runtime; state machine maps directly to mascot moods. Alternatives: **Lottie** (After Effects pipeline, good for one-shot animations, weaker for interactive states), **Krita/Inkscape** (free, Linux-native, for drawing the base character), **Figma** (layout/design, can feed into Rive).
  - Fallback: static expression images first, animation later.

**Other Phase 1**
- Notification quick-complete actions (if not shipped in MVP).
- Insights v1: per-habit heatmap/calendar history, completion-rate trends.
- **Tag-based effort distribution**: share of completions per tag over a selected period.

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
3. Notes/tags never add friction to the base flow — always optional, always secondary.
4. Retroactive edits carry deliberate friction (confirmation + honesty prompt) but stay possible within 3 days; same-day undo is frictionless.
5. One reminder max per day; silent when all done. (**Built 2026-08-21** — [docs/ux/reminder.md](ux/reminder.md). Both halves are pinned by `ReminderCheckTest`, including the case that would break them: a wake deferred past the day cutoff, which would otherwise remind about a fresh day and consume its one reminder.)
6. Streak visibility everywhere it motivates: Today view and habit detail. (**Narrowed 2026-08-21**: this said "Today view, widget, habit detail" and contradicted OQ-5, which asked whether the widget should show streaks at all. The widget is minimal — [docs/ux/widget.md](ux/widget.md) §2 has the reasoning.)

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

- **OQ-1:** Multi-tag per habit, or is one tag enough? (Proposal: one at MVP.)
- **OQ-2:** Notification quick-complete UX when >3 habits remain (Android caps 3 action buttons) — show top 3? "Complete all"? Opens the app?
- **OQ-3:** Streak freeze / grace day mechanics — decide after the 30-day personal trial reveals how resets feel.
- **OQ-4:** Mascot art style (round/chibi? pixel? flat vector?), and static-first vs animated-first. (Species and name decided: Momo the axolotl.)
- ~~**OQ-5:** Should the widget show streaks or stay minimal (just checkboxes)?~~ **Settled 2026-08-21 with the widget: minimal.** A streak is the one number that reaches zero with no new event, so it is the value whose staleness is not bounded by user inaction — on the one surface with no live query. It also costs the width that rows need. §6.6 is narrowed accordingly rather than contradicted; see [docs/ux/widget.md](ux/widget.md) §2.
- **OQ-6:** Final name call between Gawi / Hinabi / Araw; verify availability (Play Store, domain, trademark) closer to launch. ("Habi" rejected — existing habit tracker at habi.app.)

## 9. Risks

- **Sync scope creep.** Mitigation: export/import at MVP; event-log model de-risks Phase 2 now.
- **Building it but not using it.** Mitigation: 30-day personal-use success criterion gates Phase 1+.
- **Streak resets causing abandonment.** Mitigation: observe own behavior in trial; OQ-3 escape hatch.
- **Mascot becomes an art project.** Mitigation: emotive indicator at MVP; static expressions before animation; Rive state machine keeps engineering simple.
- **Notification quick-complete complexity.** Mitigation: explicitly allowed to slip to Phase 1; documented so it isn't lost.
- **Emulator friction on Arch.** Mitigation: KVM setup notes above; physical device as primary target; macOS fallback.
- **Monetization vs privacy tension.** Mitigation: E2E-encrypted cloud; local/LAN always free.

## 10. Competitive Landscape (brief)

Loop Habit Tracker (Android, offline, open source — closest competitor; no sync, no mascot/emotional layer, no tag-based retrospectives), Streaks (iOS-only), HabitKit (heatmap-centric), Habitica (heavy gamification). The wedge: local-first + peer sync + motivating character + long-horizon retrospectives, on Android.
