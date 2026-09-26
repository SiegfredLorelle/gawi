# Changelog

What each tag contained, newest first. From `v0.2.0` on, the entry for a tag
is also the tag's annotated body and the GitHub release body, so the three
cannot disagree; the two earlier tags predate this file, and `v0.0.1` is a
lightweight tag with no body at all. Format
after [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions after
[Semantic Versioning](https://semver.org/spec/v2.0.0.html). The roadmap that
says what the next tag will be is [docs/prd.md](docs/prd.md) §5.

## [Unreleased]

## [1.0.0] — 2026-09-27

The first installable release. Marks the tip of main once the release branch
merged: 270 commits of work past `v0.2.0` (12 feat, 26 fix, 2 perf, 45
refactor, 31 build, 19 test, 1 style, 5 chore, 129 docs), plus the release
branch's own notes. 1,312 unit tests green across 134 suites, run fresh with
`--rerun-tasks` (330 tasks, none up-to-date). `versionCode` 3.

**A signed, shrunk APK is attached**, with the `mapping.txt` that de-obfuscates
its stack traces beside it. The phone checks in running.md §4 ran on a signed
release build of the same code, on a Nothing A059; the commits since change
documents and one comment. Not on any store; install it by hand. The APK's
SHA-256 is
`cce6b77d48c0a722a60b0d4e14444c420d485e9d9ad8f82e9e71ace331e6364e`.

### Added

- **Release signing and R8.** `make release` builds the signed, shrunk APK
  from a key held outside the repository, and prints which key signed it. R8
  needs two keep rules, one for the class Glance reaches by name and one for
  the widget classes it would otherwise merge into one. The release build
  counts the names that survive, so the second cannot come undone unseen
  ([docs/ux/widget.md](docs/ux/widget.md) §8).
- **Gills.** Momo's three right gills carry a spare life per seven clean
  units, so a single miss is forgiven rather than breaking the run. The panel
  names the weakest outstanding habit and speaks the count
  ([docs/ux/momo.md](docs/ux/momo.md) §3).
- **Quick-complete buttons** on the end-of-day reminder: one per outstanding
  habit up to three, none at four or more. Each writes a completion for the
  date the notification was posted for rather than for whenever it is tapped.
  That closes PRD §6.1's first criterion on both its halves
  ([docs/ux/reminder.md](docs/ux/reminder.md) §4).
- **An off switch for the reminder** in Settings, under a new *Your day*
  heading with the three time rows. Switching it off keeps the reminder time,
  which still tells Momo when to start looking worried
  ([docs/ux/settings.md](docs/ux/settings.md) §1).

### Changed

- **A habit keeps no icon and no colour.** The pickers are gone, and so is the
  badge. Both fields stay in the event format, unread, so no log changes
  ([docs/ux/visual-identity.md](docs/ux/visual-identity.md) §7.3).
- **The canvas-fidelity pass.** Four type roles are SemiBold, as designed.
  Content Momo has her eyes open, the stars drift as well as pulse, and the
  dark tank ends on its own colour so the weeds hold. The launcher mark is one
  gill cluster in Momo's pink on a dark teal ground, with no face
  ([docs/ux/visual-identity.md](docs/ux/visual-identity.md) §7.1).
- **More of it is heard.** The weekly ratio is spoken in words. The widget's
  32 dp control is an image glyph, a Today widget row is one focus stop, and
  the Momo widget's tile speaks for itself. Each widget body reads its own
  line (mood line, copy or *as of*) from its root. The Today panel speaks one
  sentence after every tick, the same one the app-bar chip says. All of it
  was heard with TalkBack on the phone ([docs/running.md](docs/running.md) §4).
- **The cleanup pass.** Five gates in `make lint` hold the rules `AGENTS.md`
  states: tests assert behaviour and never implementation artefacts, comments
  carry no history, documents keep one status line per box, citations
  resolve, and names follow one convention. Each shared test helper has one
  home: `:core:testing`, or the test fixtures of the module whose types it
  builds.

### Not in this release

- **12 of the 177 device checks in running.md §4 are unticked.** PRD §5
  defines 1.0.0 as every box ticked or named here. Nine need something a
  single phone cannot give:
  - Three widget sizes need a launcher whose cells land somewhere the Nothing
    A059's and the Pixel's do not: Momo's size gate, the middle Today body, and
    three rows with the date at the smallest size.
  - The launcher icon's colour mark on the home screen and at folder size. The
    Nothing launcher re-themes every home-screen icon.
  - Three need the clock carried past a real midnight: the Streaks widget's
    untouched rollover, a very late reminder wake staying quiet, and a
    next-morning button tap writing to the night before.
  - The re-post after a button tap making no second sound. The flags are
    right; nobody has listened to it.

  Three the phone can run are deferred past the tag, by decision:
  - The reminder's buttons read by TalkBack on this build. They were heard on
    the build before the unspoken `TtsSpan` was removed from their title.
  - The vendor battery optimiser box, not run yet.
  - The Streaks widget's days and weeks judged in greyscale by eye, not run
    yet.
- Insights period labels break mid-word at 200 % font size. The fix waits on a
  canvas decision.
- The weekly milestone rung has not been seen on a device.

## [0.2.0] — 2026-09-04

Marks the tip of main once the release branch merged: 201 commits of work
past `v0.1.0-alpha.1` (30 feat, 38 fix, 24 test, 7 refactor, 5 build, 2 style,
95 docs), plus the release branch's own version bump and notes. 1,235 unit
tests green across 126 suites, run fresh with `--rerun-tasks` (289 tasks, none
up-to-date). `versionCode` 2.

**This is a tag and a set of notes, not an installable build.** The release
variant has never been signed and R8 is off, so `assembleRelease` still
produces an APK no device will accept. Every phone runs the debug build from
source. Signing and shrinking are step 1 of the 1.0.0 road in PRD §5.

### Added

- **Visual identity, finished.** Outfit on every Material type role, one
  variable font at four registered weights; a vendored Lucide icon set drawn as
  paths where the app chrome used to draw characters; Momo as the adaptive
  launcher icon and the reminder's status-bar mark
  ([docs/ux/visual-identity.md](docs/ux/visual-identity.md)).
- **Momo.** Drawn in Compose in four moods — thriving, content, worried,
  regenerating — in a living tank with weeds and bubbles, interpolating between
  moods rather than cutting. The day's completion plays a celebration; the
  7/30/100-day and 4/12/52-week milestones play a larger one and pulse the row's
  streak badge. An app-bar chip carries Momo's face, the count left and the
  milestone line ([docs/ux/momo.md](docs/ux/momo.md)).
- **Three widgets.** The Today widget grows a header and Momo when tall enough;
  a Streaks widget names each habit's run in days or weeks; a Momo widget shows
  the still frame. All three derive one palette from the app's colour scheme in
  both themes, mirror under RTL, and draw habit names in Outfit as bitmaps
  ([docs/ux/widget.md](docs/ux/widget.md)).
- **Settings.** System, light or dark theme, applied to the window as well as
  the tree. An About section with the version and the Outfit and Lucide
  licences on their own screen ([docs/ux/settings.md](docs/ux/settings.md)).
- **Insights retrospectives.** The Month/Quarter/Year period steps back through
  the calendar; an active-days-per-month trend, a best run per habit and a
  "focus shifted" sentence join the tag breakdown
  ([docs/ux/insights.md](docs/ux/insights.md) §9).

### Changed

- **Heard, not only tested.** Every Today row speaks name, streak in words and
  its check box last; archive buttons name their row; strip cells, grid cells,
  trend columns and swatches speak one label each; the chip is one stop. All of
  it re-heard on a Nothing A059 with TalkBack and recorded word for word
  ([docs/running.md](docs/running.md) §4).
- Momo's line names the habit whose streak broke, and never one already done
  today.

### Not in this release

- No installable build (above).
- The reminder's quick-complete action: decided (up to three buttons, none
  above three) and scheduled, not built.
- Momo's copy: every panel line but the regenerating one is still placeholder
  text, functional and unreviewed. Grace mechanics are decided as gills and not
  built. Both are scheduled beside quick-complete.
- 119 of the 172 manual device checks in running.md §4 are unticked; they run
  against the release build before 1.0.0.
- Four accessibility follow-ups left open on purpose: labels for the icon
  picker, an image glyph for the widget's 32 dp control, the Momo widget's
  clickable experiment, a spoken weekly ratio.

## 0.1.0-alpha.1 — 2026-08-24

Phase 1, typography: the app draws in Outfit. Marks `2542bda`, the merged and
re-verified state of the typography half of Phase 1, 13 commits past the
pre-typography tip, 901 tests green on main with `--rerun-tasks`.

- One variable font, Outfit, 110,884 bytes, on all fifteen Material roles at
  four registered weights. Only the face changed; every metric is still
  Material's, asserted against a fresh `Typography()` as a single equality.
- The widget experiment, answered: a Glance widget cannot be handed a bundled
  font, permanently. Measured on a launcher with a positive control and
  confirmed three further ways.
- A pre-release suffix because Phase 1 was not finished: the icon set landed
  next and the launcher icon still waited on Momo's art. `versionName` was
  already `0.1.0` and stayed.

## [0.0.1] — 2026-08-23

Marks `78cfcc4`, the Phase 0 tip before the restyle. The MVP feature-complete:
habits with daily or n-per-week schedules and one tag, one-tap logging with
undo, retroactive logging three days back behind an honesty prompt, notes on a
day, day and week streaks, the Today widget, the end-of-day reminder, JSON and
CSV export with import, the 30-day export nudge, and the settings for day
cutoff, week start and reminder time — all on an append-only event log with
UUIDv7 ids, no network permission and Auto Backup off. The 30-day personal
trial that was to close Phase 0 was waived the same day and Phase 1 started.

[Unreleased]: https://github.com/SiegfredLorelle/gawi/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/SiegfredLorelle/gawi/releases/tag/v1.0.0
[0.2.0]: https://github.com/SiegfredLorelle/gawi/releases/tag/v0.2.0
[0.0.1]: https://github.com/SiegfredLorelle/gawi/releases/tag/v0.0.1
