# Gawi

Offline-first habit tracker for Android — no account, no network permission,
your data never leaves the device. For what and why, read
[the PRD](docs/prd.md); for how, [the architecture](docs/architecture.md).

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
by CI.

Working with an AI coding agent? Conventions live in [AGENTS.md](AGENTS.md),
which every agent tool reads.

## License

MIT — see [LICENSE](LICENSE).
