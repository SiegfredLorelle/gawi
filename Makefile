# The command contract.
#
# Every repo made from this template exposes the same five targets, whatever
# the language. That is what lets .github/workflows/ci.yml be identical in a
# Python, React, PHP or Go repo — it only ever calls `make lint` and
# `make test` and never needs to know which stack it is running against.
#
# Wired for Kotlin/Android per docs/stacks/kotlin-android.md.
# Do not rename the targets.
#
# `run`, `itest` and `release` are deliberate stack-specific additions on top of
# the shared contract, recorded in docs/architecture.md §9 the way ci.yml's JDK
# step already is. Nothing in CI calls any of the three, so the sameness the
# paragraph above is protecting is untouched: an app you cannot launch, a test
# that needs a real launcher, and a build that needs a key no runner holds are
# the three things this file could not otherwise do.
#
# `test` and `itest` are separate because they are separate gates, not two ways
# of saying the same thing: `./gradlew test` is the unit-test umbrella and never
# touches a device, which is what keeps architecture §8's "CI runs unit tests
# only" true without ci.yml having to know that instrumented tests exist.

.DEFAULT_GOAL := help
.PHONY: help setup hooks fmt lint test itest run release

# Resolved from PATH. Override it if the SDK is somewhere unusual, e.g.
#   make run ADB=~/Library/Android/sdk/platform-tools/adb
ADB ?= adb

# apksigner ships inside a versioned build-tools directory and never on PATH,
# so the highest-versioned one installed is the default. `sort -V` and not a
# plain sort, which orders 9.0.0 above 37.0.0 by comparing the first digit. It
# still prefers a preview to the stable release of the same version, since -rc3
# sorts after nothing at all — override APKSIGNER, as with ADB, when that
# matters.
APKSIGNER ?= $(shell ls -d \
  $(or $(ANDROID_HOME),$(HOME)/Android/Sdk)/build-tools/*/apksigner \
  2>/dev/null | sort -V | tail -1)

# AGP names this file app-release-unsigned.apk when no signing config resolved,
# so the name itself is a signing check and `release` guards its inputs first.
RELEASE_APK := app/build/outputs/apk/release/app-release.apk
RELEASE_MAPPING := app/build/outputs/mapping/release/mapping.txt

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) \
	  | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-8s\033[0m %s\n", $$1, $$2}'

setup: hooks ## Install dependencies and git hooks
	./gradlew help

hooks: ## Install pre-commit git hooks (pre-commit + commit-msg)
	@if [ -n "$$CI" ]; then \
	  echo "CI detected — skipping git hook install"; \
	else \
	  pre-commit install --install-hooks; \
	fi

fmt: ## Format the codebase
	./gradlew spotlessApply

# The scripts run first because each takes about a second and Gradle takes
# minutes, so a narrated comment, a stale `docs/` reference, a forbidden call in
# a test or an overgrown checklist box fails fast instead of at the end. Scripts
# and not Gradle tasks on purpose — see their headers, and architecture §9.
# `check-docs.sh` is last of the four because it is the only one that reads
# nothing but `docs/`, so a code change trips the other three first.
#
# `:app:assembleDebug` is the only step here that packages, and CI calls nothing
# but `make`. Without it nothing in fmt/lint/test merges a manifest, merges
# resources or dexes anything, so a green run would say nothing about whether
# the app still builds into an APK and a packaging regression would wait for the
# next `make run`. Debug and not release: `release` is the gate that packages a
# shippable APK and it needs a key no runner holds, so the release assemble CI
# could run would be unsigned, would pay R8's two minutes on every gate, and
# would prove nothing this step does not (docs/running.md §6).
#
# It is listed last for the reader, not the scheduler. Gradle takes command-line
# order as a hint, and with org.gradle.parallel on, :app's compile and dex start
# while Spotless and detekt are still running — measured: spotlessCheck finished
# after dexBuilderDebug. A formatting failure therefore lands after some of the
# assemble has been paid for, not before it starts; the waste is small because
# lintDebug already compiled :app, leaving only dex and packaging (~3s warm).
# The ordering that does hold is make's: this recipe is CI's lint step, which
# runs before its test step, so a packaging break fails there and not at the
# end. That is also why it lives in `lint` rather than `test`, together with
# `test` staying plain `./gradlew test` (architecture §9).
lint: ## Lint and type-check the codebase
	./scripts/check-history.sh
	./scripts/check-citations.sh
	./scripts/check-tests.sh
	./scripts/check-docs.sh
	./gradlew spotlessCheck detekt lint :app:assembleDebug

test: ## Run the test suite
	./gradlew test

# WARNING: connectedAndroidTest uninstalls the app when it finishes, and an
# uninstall deletes /data/data — the entire event log, every habit and the
# settings. allowBackup is off (architecture §6), so there is no OS copy to
# restore from and export/import is the only way back. Export first, or point
# this at a throwaway AVD. Measured: one run wiped an emulator holding 345 events.
itest: ## Run instrumented tests on a device (DESTROYS app data; not run by CI)
	./gradlew :app:connectedDebugAndroidTest

run: ## Build, install and launch the app on a device or emulator
	./gradlew :app:installDebug
	$(ADB) shell am start -n com.gawi.app/.MainActivity

# The only target that needs a secret. AndroidApplicationConventionPlugin reads
# the four GAWI_KEYSTORE_* variables through Gradle's provider API, and an unset
# one leaves `release` unsigned rather than failing the build — which is what
# lets CI assemble with no key, and what makes these guards the only place the
# omission can be caught. All four are checked and not just the path, because a
# missing alias or password reaches R8 and fails minutes later inside
# packageRelease; and the path is checked as a *file*, because `.env.example`'s
# placeholders are quoted strings that export perfectly well and are not paths.
# Export them into the shell first with `set -a; . ./.env; set +a` — the `./`
# because zsh looks a bare name up on PATH and not in the working directory.
#
# apksigner rather than a Gradle assertion, because with no signing config at
# all AGP names the output app-release-unsigned.apk and exits 0 — the guards
# above are what stop that reaching a release, and this is the check that says
# so out loud. It prints the certificate and not just the verdict,
# because `Verifies` says that an APK is signed and not *by whom* — and an
# up-to-date assemble reports success without repackaging, so the DN is all
# that separates the real key from one left over from a test.
#
# `mapping.txt` is printed because PRD §5 requires it to travel with every
# release, and it is the only way to read a stack trace from a shrunk build.
release: ## Build a signed, shrunk release APK (needs the GAWI_KEYSTORE_* vars)
	@test -n "$(GAWI_KEYSTORE_PATH)" \
	  || { echo "GAWI_KEYSTORE_PATH is unset — see .env.example"; exit 2; }
	@test -n "$(GAWI_KEYSTORE_PASSWORD)" \
	  || { echo "GAWI_KEYSTORE_PASSWORD is unset — see .env.example"; exit 2; }
	@test -n "$(GAWI_KEY_ALIAS)" \
	  || { echo "GAWI_KEY_ALIAS is unset — see .env.example"; exit 2; }
	@test -n "$(GAWI_KEY_PASSWORD)" \
	  || { echo "GAWI_KEY_PASSWORD is unset — see .env.example"; exit 2; }
	@test -f "$(GAWI_KEYSTORE_PATH)" \
	  || { echo "GAWI_KEYSTORE_PATH names no file: $(GAWI_KEYSTORE_PATH)"; exit 2; }
	@test -n "$(APKSIGNER)" \
	  || { echo "apksigner not found — pass APKSIGNER=<path>"; exit 2; }
	./gradlew :app:assembleRelease
	$(APKSIGNER) verify --print-certs --verbose $(RELEASE_APK)
	@echo "APK:     $(RELEASE_APK)"
	@echo "mapping: $(RELEASE_MAPPING)"
