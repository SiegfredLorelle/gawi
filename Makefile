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
# `run` and `itest` are deliberate stack-specific additions on top of the shared
# contract, recorded in docs/architecture.md §9 the way ci.yml's JDK step already
# is. Nothing in CI calls either, so the sameness the paragraph above is
# protecting is untouched: an app you cannot launch, and a test that needs a real
# launcher, are the two things this file could not otherwise do.
#
# `test` and `itest` are separate because they are separate gates, not two ways
# of saying the same thing: `./gradlew test` is the unit-test umbrella and never
# touches a device, which is what keeps architecture §8's "CI runs unit tests
# only" true without ci.yml having to know that instrumented tests exist.

.DEFAULT_GOAL := help
.PHONY: help setup hooks fmt lint test itest run

# Resolved from PATH. Override it if the SDK is somewhere unusual, e.g.
#   make run ADB=~/Library/Android/sdk/platform-tools/adb
ADB ?= adb

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
# minutes, so a stale `docs/` reference or a forbidden call in a test fails fast
# instead of at the end. Scripts and not Gradle tasks on purpose — see their
# headers, and architecture §9.
#
# `:app:assembleDebug` is the only step here that packages, and CI calls nothing
# but `make`. Without it nothing in fmt/lint/test merges a manifest, merges
# resources or dexes anything, so a green run would say nothing about whether
# the app still builds into an APK and a packaging regression would wait for the
# next `make run`. Debug and not release: release is unsigned and R8 is deferred
# (docs/running.md §3), so assembling it would prove nothing more.
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
	./scripts/check-citations.sh
	./scripts/check-tests.sh
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
