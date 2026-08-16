# The command contract.
#
# Every repo made from this template exposes the same five targets, whatever
# the language. That is what lets .github/workflows/ci.yml be identical in a
# Python, React, PHP or Go repo — it only ever calls `make lint` and
# `make test` and never needs to know which stack it is running against.
#
# Wired for Kotlin/Android per docs/stacks/kotlin-android.md.
# Do not rename the targets.

.DEFAULT_GOAL := help
.PHONY: help setup hooks fmt lint test

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
	./gradlew testDebugUnitTest :core:domain:test
