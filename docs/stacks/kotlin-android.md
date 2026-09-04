# Stack: Kotlin/Android (Gradle + Spotless/ktlint + detekt)

Scaffold first, then paste the blocks below. The build needs a JDK 17 and an
Android SDK; neither lives in the repo.

```sh
# One-time machine setup (user-space, no sudo):
#   JDK 17    → ~/.jdks            (https://adoptium.net)
#   SDK       → ~/Android/Sdk      (cmdline-tools + sdkmanager)
# then export JAVA_HOME and ANDROID_HOME in your shell profile.
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
# Emulator, system images and device setup: ../running.md

# Generate the wrapper once with a downloaded Gradle distribution; commit it.
# Pin the distribution checksum (from
# services.gradle.org/distributions/gradle-<version>-bin.zip.sha256) —
# validateDistributionUrl alone checks the URL, not the bytes. After this,
# every command goes through ./gradlew and nothing else needs a local Gradle.
gradle wrapper --gradle-version 9.7.1 --distribution-type bin \
  --gradle-distribution-sha256-sum acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
```

## 1. Makefile

```make
setup: hooks ## Install dependencies and git hooks
	./gradlew help

fmt: ## Format the codebase
	./gradlew spotlessApply

lint: ## Lint and type-check the codebase
	./scripts/check-citations.sh
	./scripts/check-tests.sh
	./gradlew spotlessCheck detekt lint :app:assembleDebug

test: ## Run the test suite
	./gradlew test
```

`./gradlew help` looks like a no-op but the wrapper downloads the Gradle
distribution and warms the daemon, which is exactly what `setup` means here.
`lint` is two scripts and four Gradle gates. `scripts/check-citations.sh`
checks that every `docs/` citation in a comment resolves, and
`scripts/check-tests.sh` refuses the constructs that reach past behaviour into
the implementation. Both are scripts and not Gradle tasks on purpose, because a
task can pass by being UP-TO-DATE (architecture §9). The Gradle gates are formatting
(Spotless check, non-mutating), static analysis (detekt), Android Lint, and a
debug assemble — the one step that packages, without which CI never proves
the app builds into an APK (architecture §9). `test` is deliberately generic —
it resolves to plain `test` on pure-JVM modules and the unit-test tasks on
Android modules, so a newly added module can never be silently skipped the
way an explicit task list would allow.

## 2. .pre-commit-config.yaml

Append **nothing**. Every Gradle-based hook pays JVM + daemon startup
(10–30 s) per commit, which violates this file's own charter — hooks are fast
feedback, not enforcement. Formatting drift is caught by `make lint` in CI,
and `make fmt` fixes it in one command. The generic hooks (whitespace,
yaml/toml checks, gitleaks) still cover the new files.

One adjustment instead of an addition: `gradlew.bat` is deliberately CRLF, so
the `mixed-line-ending` hook needs `exclude: '\.(bat|cmd)$'`.

## 3. Quality config

All in the **root** `build.gradle.kts` — one config site, whole-tree targets,
`build-logic/` included; module build files stay unaware of the tooling:

- Spotless targets `**/*.kt` and `**/*.kts` (minus `**/build/**`) with ktlint
  pinned from the version catalog.
- detekt runs as a single root task over every module's `src` plus
  `build-logic/src`, `buildUponDefaultConfig = true`, config at
  `config/detekt/detekt.yml`, no type resolution.
- ktlint reads `.editorconfig`, so it needs an override there — the template
  default is 2-space, Kotlin's convention is 4:

```ini
[*.{kt,kts}]
indent_size = 4
max_line_length = 140
```

## 4. .gitignore

Append:

```gitignore
# Android / Gradle
.gradle/
.kotlin/
local.properties
captures/
*.apk
*.aab
*.hprof
.cxx/
```

## 5. dependabot.yml

```yaml
  - package-ecosystem: gradle
    directory: /
    schedule:
      interval: weekly
    commit-message:
      prefix: "build(deps)"
    groups:
      minor-patch:
        update-types: [minor, patch]
```

The gradle ecosystem reads `gradle/libs.versions.toml`, so one entry covers
the main build and `build-logic/`.

## Notes

- SDK platform packages are **minor-versioned** as of API 36.1/37: the id is
  `platforms;android-37.0`, and `platforms;android-37` does not exist. Two
  automated reviewers have already "corrected" this the wrong way — check
  `sdkmanager --list` before trusting intuition from the old naming scheme.
- `ci.yml` passes `cache-encryption-key: ${{ secrets.GRADLE_ENCRYPTION_KEY }}`
  to setup-gradle. Create that repository secret with a value from
  `openssl rand -base64 16`. It only encrypts CI cache entries — losing it
  costs nothing (rotate freely). Without it the build stays green but
  silently never persists Gradle's configuration cache, so cold-configure
  time is paid on every run.
- The runner needs a JDK and benefits hugely from dependency caching. Add
  `actions/setup-java` (temurin 17) and `gradle/actions/setup-gradle` to
  `ci.yml` — the sanctioned case where editing that file is expected. No SDK
  step: GitHub's ubuntu runners ship the Android SDK with licenses accepted,
  and AGP installs any missing platform itself.
- The wrapper (`gradlew`, `gradle-wrapper.jar`) is committed on purpose —
  that is the standard Gradle contract and the jar is ~45 KB. Mark `*.jar
  binary` in `.gitattributes` and never edit `gradle/wrapper/` by hand.
- All dependency and tool versions live in `gradle/libs.versions.toml` and
  nowhere else. Convention plugins in `build-logic/` own build configuration;
  module build files only declare dependencies.
- detekt 1.23.x is compiled against an older Kotlin and warns about it —
  harmless without type resolution. Migrate to detekt 2.x when it goes
  stable (the plugin id moves to `dev.detekt`).
- AGP 9 has built-in Kotlin: applying `org.jetbrains.kotlin.android` is an
  error, and Android convention plugins must not touch a `kotlin {}`
  extension — `jvmTarget` follows `compileOptions.targetCompatibility`.
