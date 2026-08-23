# Gawi

[![ci](https://github.com/SiegfredLorelle/gawi/actions/workflows/ci.yml/badge.svg)](https://github.com/SiegfredLorelle/gawi/actions/workflows/ci.yml)

Offline-first habit tracker for Android — no account, no network permission,
your data never leaves the device. For what and why, read
[the PRD](docs/prd.md); for how, [the architecture](docs/architecture.md).

## Status

Pre-1.0 and pre-release. Version `0.1.0` (`versionCode 1`), no published
release, and not on Play Store. Build it from source with the commands below.

Phase 0, the MVP, is feature-complete: habits, logging, streaks, the
home-screen widget, the end-of-day reminder, and export/import all work. Its
success criterion was deliberately a usage one and not a code one: thirty
consecutive days of real daily use without reverting to the old method.
**That criterion was waived on 2026-08-23 — not met, not failed, not run** —
and Phase 1 started in its place. PRD §5 records what waiving it cost, and §9
records the risk it leaves uncovered.

Phase 1 is under way. It is being taken in a different order from the one
PRD §5 first assessed: the visual identity came first, because the app was on
stock Material 3 by an explicit deferral (PRD §8, OQ-4) and the screens Phase 1
adds would otherwise have been styled twice. The colour half of that has landed —
a designed light and dark scheme and eight retuned habit hues
([docs/ux/visual-identity.md](docs/ux/visual-identity.md)). Typography and Momo's
own art are still open, and Insights v1 is what the reordering was for.

What comes next, and in what order, is recorded in [PRD §5](docs/prd.md). The
open design questions are in §8 of the same file.

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
