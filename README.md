# Gawi

[![ci](https://github.com/SiegfredLorelle/gawi/actions/workflows/ci.yml/badge.svg)](https://github.com/SiegfredLorelle/gawi/actions/workflows/ci.yml)

Offline-first habit tracker for Android — no account, no network permission,
your data never leaves the device. For what and why, read
[the PRD](docs/prd.md); for how, [the architecture](docs/architecture.md).

## Status

Pre-1.0. Version `0.2.0` (`versionCode 2`), tagged `v0.2.0` on 2026-09-03 —
a tag and release notes, **not an installable build**: the release variant has
never been signed, so every phone still runs the debug build from source. Signing
is the 1.0.0 deliverable. Not on any store. Build it from source with the
commands below; [CHANGELOG.md](CHANGELOG.md) records what each tag contained.

Phase 0, the MVP, is feature-complete: habits, logging, streaks, the
home-screen widget, the end-of-day reminder, and export/import all work. Its
success criterion was deliberately a usage one and not a code one: thirty
consecutive days of real daily use without reverting to the old method.
**That criterion was waived on 2026-08-23 — not met, not failed, not run** —
and Phase 1 started in its place. PRD §5 records what waiving it cost, and §9
records the risk it leaves uncovered.

Phase 1 is all but done, taken in a different order from the one PRD §5 first
assessed: the visual identity came first, because the app was on stock Material 3
by an explicit deferral (PRD §8, OQ-4) and the screens Phase 1 adds would
otherwise have been styled twice. All of it has landed — a designed light and dark
scheme, eight habit hues, Outfit on every type role, a vendored Lucide icon set,
and Momo as the launcher mark
([docs/ux/visual-identity.md](docs/ux/visual-identity.md)). Momo lives in the
Today view's tank in four moods, celebrates finished days and streak milestones,
and speaks through an app-bar chip ([docs/ux/momo.md](docs/ux/momo.md)). Three
home-screen widgets share one palette derived from the app's
([docs/ux/widget.md](docs/ux/widget.md)). Settings choose the theme and carry an
About section with licences. **Insights v1 is what the reordering was for**: a
per-habit history calendar and completion-rate trend reached from habit detail,
and an Insights screen reporting on every habit at once over a month, quarter or
year, stepped back through the calendar for retrospectives
([docs/ux/insights.md](docs/ux/insights.md)). An accessibility pass was heard on
a real device ([docs/running.md](docs/running.md) §4). The one Phase 1 bullet
still open is the reminder's quick-complete action, decided and scheduled.

What comes next, and in what order, is recorded in [PRD §5](docs/prd.md): a
cleanup pass, then release signing, then the first installable release as
1.0.0. The open design questions are in §8 of the same file.

Single maintainer, so expect unhurried responses.

## Requirements

- JDK 17 and an Android SDK (platform 37) — setup notes in
  [docs/stacks/kotlin-android.md](docs/stacks/kotlin-android.md)
- [pre-commit](https://pre-commit.com) for git hooks
- `make`

## Getting started

```sh
make setup
```

`make setup` installs dependencies and wires the git hooks. The first run
downloads a Node toolchain into `~/.cache/pre-commit` for the commit-message
linter; this happens once per machine.

## Commands

| Command | Does |
|---|---|
| `make help` | List available targets |
| `make setup` | Install dependencies and git hooks |
| `make fmt` | Format the codebase |
| `make lint` | Lint and type-check |
| `make test` | Run the test suite |
| `make run` | Build, install and launch on a device or emulator |
| `make itest` | Instrumented tests on a device — **destroys that device's app data** |

`make itest` is the one command here that can lose something. It uninstalls the
app when it finishes, and `allowBackup` is off by design, so the event log goes
with it. Point it at a throwaway emulator, never at a device you actually track
habits on — [docs/running.md](docs/running.md) §3 and §4 have the detail.

## Usage

```sh
make test                      # unit tests (pure-JVM domain + Android modules)
make run                       # build, install and launch on a device or emulator
./gradlew :app:assembleDebug   # APK only, at app/build/outputs/apk/debug/
```

Setting up an emulator or a physical device, and the manual checks that CI
cannot run, are in [docs/running.md](docs/running.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Commit messages follow a
[strict convention](.github/COMMIT_CONVENTION.md) enforced by a git hook and
by CI. Participation is covered by the
[Code of Conduct](CODE_OF_CONDUCT.md).

Found a security issue? [SECURITY.md](SECURITY.md) has the reporting route and
the threat model. Please do not open a public issue for one.

Working with an AI coding agent? Conventions live in [AGENTS.md](AGENTS.md),
which every agent tool reads.

## License

MIT — see [LICENSE](LICENSE).
