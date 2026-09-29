# Gawi

[![ci](https://github.com/SiegfredLorelle/gawi/actions/workflows/ci.yml/badge.svg)](https://github.com/SiegfredLorelle/gawi/actions/workflows/ci.yml)

Offline-first habit tracker for Android — no account, no network permission,
your data never leaves the device. For what and why, read
[the PRD](docs/prd.md); for how, [the architecture](docs/architecture.md).

## Status

`v1.0.0` (`versionCode 3`) is the latest release and the first with an
installable build attached: a signed, shrunk APK with its `mapping.txt`, on the
[GitHub release](https://github.com/SiegfredLorelle/gawi/releases/tag/v1.0.0).
Not on any store; [install the APK by hand](#installing), or build it from
source with the commands below. [CHANGELOG.md](CHANGELOG.md) records what each
tag contained, and what 1.0.0 leaves unverified on a device.

Phase 0, the MVP, is feature-complete: habits, logging, streaks, the
home-screen widget, the end-of-day reminder, and export/import all work. Its
success criterion was deliberately a usage one and not a code one: thirty
consecutive days of real daily use without reverting to the old method.
**That criterion was waived on 2026-08-23 — not met, not failed, not run** —
and Phase 1 started in its place. PRD §5 records what waiving it cost, and §9
records the risk it leaves uncovered.

Phase 1 is done, and closes with 1.0.0. It was taken in a different order
from the one PRD §5 first assessed: the visual identity came first, because the
app was on stock Material 3 by an explicit deferral (PRD §8, OQ-4) and the
screens Phase 1 adds would otherwise have been styled twice. All of it has
landed — a designed light and dark scheme, Outfit on every type role, a
vendored Lucide icon set, a habit that carries only its name, and a launcher
mark of one gill cluster in Momo's pink
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
a real device ([docs/running.md](docs/running.md) §4). Phase 1's last open
bullet, the reminder's quick-complete action, is built: up to three buttons on
the end-of-day reminder, each completing a habit for the day the notification
was posted for. The two questions Phase 1 raised and left open — grace
mechanics, decided and built as gills, and Momo's real copy — are answered
with it.

What comes next is 1.x in [PRD §5](docs/prd.md): growth one feature at a
time, each on the design canvas first, before sync. The open design questions
are in §8 of the same file.

Single maintainer, so expect unhurried responses.

## Installing

1. Download the APK attached to the
   [latest release](https://github.com/SiegfredLorelle/gawi/releases/latest)
   and check its SHA-256 against the one in that release's notes —
   `sha256sum` on a computer, or any hash app on the phone.
2. Open it. Android asks once whether the browser or file manager may install
   unknown apps; allow it.
3. **Play Protect blocks the first install.** It says *App blocked to protect
   your device* and *Play Protect hasn't seen an app from this developer
   before. It may be unsafe.* Tap **More details**, then **Install anyway**.
   That wording was seen on a Nothing A059 on 2026-09-29; other phones and
   Android versions word it differently.

The warning is about the signing key, not the app. Gawi is not on Google Play
and its key is not registered with Google, so Play Protect has no record of
who signed it. The checks that do say something about the app are the hash
above and the permissions it asks for — none of them network, as
[SECURITY.md](SECURITY.md) sets out. Registering the
key is on the roadmap as OQ-7 in [PRD §8](docs/prd.md).

Every release is signed with the same key, so a newer one installs over an
older one and keeps your habits. Uninstalling deletes them: Auto Backup is off, so export
from Settings first.

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
| `make release` | Build a signed, shrunk release APK — needs the signing key |

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
