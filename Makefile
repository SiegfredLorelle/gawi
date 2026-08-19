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
# `run` is a deliberate stack-specific addition on top of the shared contract,
# recorded in docs/architecture.md §9 the way ci.yml's JDK step already is.
# Nothing in CI calls it, so the sameness the paragraph above is protecting is
# untouched: an app you cannot launch is the one thing this file could not do.

.DEFAULT_GOAL := help
.PHONY: help setup hooks fmt lint test run

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

lint: ## Lint and type-check the codebase
	./gradlew spotlessCheck detekt lint

test: ## Run the test suite
	./gradlew test

run: ## Build, install and launch the app on a device or emulator
	./gradlew :app:installDebug
	$(ADB) shell am start -n com.gawi.app/.MainActivity
