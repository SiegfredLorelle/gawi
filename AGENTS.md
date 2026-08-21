# AGENTS.md

Instructions for AI coding agents working in this repository.

This is the **single source of truth** for agent guidance. `CLAUDE.md` imports
this file rather than duplicating it, and any other tool's config file
(`.github/copilot-instructions.md`, `.cursor/rules/`, `GEMINI.md`,
`.windsurfrules`) should be a one-line pointer here. Do not copy content out
of this file — it will drift.

The project: **Gawi**, an offline-first, event-sourced habit tracker for
Android (Kotlin, Jetpack Compose, Hilt, Room). `docs/prd.md` says what and
why; `docs/architecture.md` fixes how — read it before writing code, it is
the contract.

## Commands

Always use these. Never guess at the underlying tool — the Makefile is the
contract, and it is the same in every repo regardless of language.

| Command | Does |
|---|---|
| `make setup` | Install dependencies and git hooks |
| `make fmt` | Format the codebase |
| `make lint` | Lint and type-check |
| `make test` | Run the test suite |
| `make run` | Build, install and launch on a device or emulator |

Run `make lint` and `make test` before considering any change complete.

## Commit messages

Enforced by a `commit-msg` hook and by CI. Commits that break these rules are
rejected, so get them right the first time:

- Format `type(scope): subject`, header **50 characters maximum**
- Scope is **required** and must be one of the values in `scope-enum` in
  `.commitlintrc.yaml` (e.g. `app`, `domain`, `data`, `gradle`, `ci`,
  `docs`); add a new scope there first if a module is born
- Types: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build` `ci`
  `chore` `revert`
- Subject: lowercase first word, imperative mood, no trailing period
- **Blank line between header and body** — required
- Body wrapped at **72 characters**
- Long URLs and `Co-Authored-By:` trailers go in the footer, which has no
  length limit. Never put a long URL in the body; it cannot be wrapped and
  will fail the check.

Full guide with examples: `.github/COMMIT_CONVENTION.md`

## Conventions

- **Modules**: `:app` (wiring, navigation), `:core:domain` (pure Kotlin/JVM),
  `:core:data` (Room, DataStore, repositories), `:core:ui` (theme, shared
  composables), `:feature:today`, `:feature:habits`, `:feature:settings` and
  `:widget` (Glance). **All eight exist as of Phase 0** — this bullet used to
  say the feature modules and `:core:ui` "are created when their first screen is
  built", which stopped being true on 2026-08-21, and `:widget` is not a screen
  at all. **`docs/architecture.md` §2 owns the contents of each and the full
  dependency rule** — read it rather than trusting this summary, which is the
  drift this file warns about everywhere else.
- **The dependency rule is non-negotiable**: `:core:domain` depends only on
  the Kotlin stdlib and kotlinx-serialization. Domain logic never lands in a
  module that can import Android.
- **`:app` owns navigation; no other module depends on a navigation library.**
  A feature module exposes Route composables taking plain lambdas, and `:app`'s
  graph decides where each one leads. Feature modules take
  `androidx.hilt:hilt-lifecycle-viewmodel-compose` for `hiltViewModel()`, never
  `hilt-navigation-compose` — its pom would drag navigation onto their
  classpath. Routes are type-safe `@Serializable` classes
  (docs/architecture.md §2).
- **Versions live only in `gradle/libs.versions.toml`.** Convention plugins
  in `build-logic/` own build configuration; module build files only apply
  `gawi.*` plugin ids and declare dependencies.
- **AGP 9 has built-in Kotlin**: never apply `org.jetbrains.kotlin.android`,
  never use kapt (KSP only).
- **Never edit by hand**: `gradle/wrapper/`, `gradlew`, `gradlew.bat`.
- **Testing** (docs/architecture.md §8): new `:core:domain` logic ships with
  JVM unit tests, and a feature module's screen composable ships with a JVM
  Compose test under Robolectric in its own `test` source set — not
  `androidTest`. CI runs unit tests only; instrumented tests are a manual,
  on-device activity.
- Run `make fmt` before committing; `make lint` and `make test` before
  considering any change complete.

## Secrets

Never commit real credentials. `gitleaks` runs as a pre-commit hook and again
in CI. Add every new configuration variable to `.env.example` with a
placeholder value.
