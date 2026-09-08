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
| `make itest` | Instrumented tests on a device — **destroys that device's app data** |
| `make release` | Build a signed, shrunk release APK — needs the signing key |

`make itest` is the one target here that can lose something, so it is listed
rather than left to be discovered: it uninstalls the app when it finishes and
`allowBackup` is off, so the event log goes with it. Point it at a throwaway
emulator, never at a device holding real data. `make run`, `make itest` and
`make release` are stack-specific additions to the shared five, recorded in
docs/architecture.md §9; the rest of the table is the same in every repo.
`make release` is the only target that needs a secret — the four `GAWI_*`
variables `.env.example` names, exported into the shell, never read from the
file.

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
  composables), `:feature:today`, `:feature:habits`, `:feature:insights`,
  `:feature:settings`, `:widget` (Glance) and `:core:testing` (shared test
  helpers; its main source set is consumed only by test source sets). **All ten
  exist** and `:widget` is not a screen. `:core:testing` is the newest, and the
  only one that is not part of the app: its main source set is consumed by test
  source sets alone. **A new module is created together with its
  first real file, never empty**: an Android library always has test sources
  configured for its unit-test variant, so Gradle fails its test task with "no
  tests discovered" until there is a real test in it. That is a rule for the
  *next* module rather than a note about this one, and so is the other half of
  it: add the scope to `.commitlintrc.yaml` **before** the module's first commit,
  or the `commit-msg` hook rejects it. **`docs/architecture.md` §2
  owns the contents of each and the full dependency rule** — read it rather than
  trusting this summary, which is the drift this file warns about everywhere
  else.
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
- **Look in `core/ui/component/` before writing a composable a second feature
  could want.** Anything drawn by more than one feature belongs in `:core:ui`,
  and so do presentation types shared by more than one (docs/architecture.md
  §2). This is the rule most easily broken by accident: the habit icon badge was
  written three times before it was shared, which meant three hand-copied
  contrast decisions, and fixing two of the three would have looked exactly like
  fixing it. The pointer is to the directory rather than to a list of what is in
  it, because a list would be stale by Phase 1. **Caught at two copies on
  2026-08-24**, which is what following this rule looks like: `GlyphButton` and
  the seven weekday labels moved to `:core:ui` when the history screen would
  have been their third copy, rather than after it was.
- **Design on the canvas before writing UI or animation code.** Any change to
  what a screen, the mascot or a motion looks like goes on the Gawi Redesign
  canvas (one canvas, its link in docs/ux/momo.md §1; never a new artifact) as
  a new page beside a screenshot of the build, gets marked by the
  maintainer, and only then is coded and checked on the emulator against its
  artboard. Rule since 2026-09-03, when a side-by-side found four drifts the
  tests could not: type weight lighter than designed, a mood face drawn with
  its eyes closed, stars scaling where they should drift, and weeds lost on a
  gradient. A test pins what was built; only the canvas says what was meant.
- **Dependency and tool versions live only in `gradle/libs.versions.toml`.**
  App identity (`applicationId`, `versionCode`, `versionName`) is the app
  module's own, per the convention plugin's comment. Convention plugins
  in `build-logic/` own build configuration; module build files only apply
  `gawi.*` plugin ids and declare dependencies. **One recorded exception**:
  `:feature:settings` names the repository's `licenses/` directory as an
  assets source set in its own build file, because a convention plugin for one
  module's one directory is heavier than the thing it configures
  (docs/architecture.md §10, docs/ux/settings.md §9). Do not copy it as
  precedent; a second one is the signal to write the plugin.
- **AGP 9 has built-in Kotlin**: never apply `org.jetbrains.kotlin.android`,
  never use kapt (KSP only).
- **Never edit by hand**: `gradle/wrapper/`, `gradlew`, `gradlew.bat`.
- **Testing** (docs/architecture.md §8): new `:core:domain` logic ships with
  JVM unit tests, and a feature module's screen composable ships with a JVM
  Compose test under Robolectric in its own `test` source set — not
  `androidTest`. CI runs unit tests only; instrumented tests are a manual,
  on-device activity. **A test asserts a behaviour a user or a document names,
  never an artefact of the implementation**: no exact pixel or ARGB constant,
  no magic computed value, no reflection into a library, no XML attribute
  string read off disk, no subpath count, and no second test of the same
  thing. `scripts/check-tests.sh`, in `make lint`, refuses `Thread.sleep` and
  reflection by method name in test sources. **Shared test helpers live in
  `:core:testing`**, except one that builds or measures a single module's own
  types, which is published from that module's `src/testFixtures` and
  re-exported: ids and events from `:core:domain`, the WCAG contrast helper
  from `:core:ui`. A second copy of one is the defect.
- **Comments keep what the code cannot say** (`scripts/check-history.sh`, in
  `make lint`): the mechanism, the invariant, the reason a shape was chosen,
  and the `docs/… §N` it answers to. **History is git's, not the comment's** —
  no dates, no "used to", no "a review said", no "caught on review", no "this
  KDoc was wrong", no "previously said" (a bare "previously" is fine), no "an
  earlier version", no "the first cut", no "since Phase N". The script is the
  mechanical half and holds the full list; it refuses those phrases and any
  `YYYY-MM-DD` in a `src/main` comment. **A date is exempt only where it stamps
  a measurement whose staleness cannot be read off anything else** — a phone, an
  API level, a launcher, a third-party reader — and `measured` or `seen on` must
  sit on the same line as the date, because the gate reads one line at a time
  and wrapping will otherwise separate them.
- **A comment does not retell the code.** If the declaration under it already
  says it, the sentence goes; no script can catch this one. Explain what the
  reader cannot see — why the mutex is not reentrant, what breaks if the
  argument is removed, which of two plausible readings is the wrong one. **Two
  traps when trimming.** A sentence phrased as history is sometimes the only
  statement of a live invariant — rewrite it in the present tense, never delete
  it. And the anchoring `docs/… §N` often sits inside the paragraph being cut,
  with bare `§N` references far below inheriting it, so re-anchor it into a
  surviving sentence and run `scripts/check-citations.sh` after every file, not
  every commit.
- **Say it once, at the site that can break it.** A mechanism argued in a
  class KDoc, again in a member's, and again inline is one fact costing three
  paragraphs. Keep it where an edit would violate it; the other places get
  nothing — a cross-reference is still a line, and three of them are how a
  reader stops trusting any of them. The shape to watch is a long KDoc that
  argues a mechanism living in the function below it, whose own comments then
  argue it again.
- **Keep the finding, not the search.** The trap stays, and so does why it is
  a trap: a reader about to delete a redundant-looking argument has to be
  stopped. How it was found does not — the decompiled bridge, the theory that
  was ruled out, the pixel counts, the thing that was expected to happen and
  did not. Git holds the investigation and `docs/… §N` holds the decision, so
  the comment cites the section rather than restating what it settled.
- **A document records decisions and current state; git records history**
  (`scripts/check-docs.sh`, in `make lint`). **A checklist box keeps its
  instruction and one status line** — the quoted screen-reader output and the
  session narrative go, and an unticked box with nothing recorded against it
  gets no status line at all, because the empty box is the status. **A struck
  passage is deleted**: a claim struck through with its correction after it is
  history written in place, so the surviving half is rewritten in the present
  tense. A "still open" section compresses to one bullet per item with its
  blocker, and a section whose list has closed items in it says so in its
  heading. The script is the mechanical half: it caps a checkbox body at 26
  prose lines, allows a status line at most two dates (when it ran and when it
  was re-run), and refuses `~~` outside a code span or a fenced block. **Never
  drop an instruction to get under the cap** — that is the rule inverted, and it
  cost this document two setup recipes before the margin was widened.
- **Method that serves a whole block belongs to the block, not to one box.** A
  recipe, a trap or an invariant appended to one checklist entry is the shape
  that outgrows the cap, and it is usually the shape that gets repeated in the
  next entry too — the same defect as a mechanism argued three times in one
  file. State it once in the block's preamble and let the boxes point at it.
- **A measurement another document cites is not narrative.** Before cutting a
  paragraph, check whether the number in it is the thing some other file cites
  §N for; `scripts/check-citations.sh` proves the section still exists and
  cannot prove it still says what the citation claims.
- Run `make fmt` before committing; `make lint` and `make test` before
  considering any change complete.

## Secrets

Never commit real credentials. `gitleaks` runs as a pre-commit hook and again
in CI. Add every new configuration variable to `.env.example` with a
placeholder value.
